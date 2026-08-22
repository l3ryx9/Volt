package com.voltai.doai.data.agent

import com.voltai.doai.domain.interfaces.ExecutionManager
import com.voltai.doai.domain.interfaces.Orchestrator
import com.voltai.doai.domain.interfaces.TaskValidator
import com.voltai.doai.domain.interfaces.ToolRouter
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.PhaseState
import com.voltai.doai.domain.models.StepReport
import com.voltai.doai.domain.models.Task
import com.voltai.doai.domain.models.TaskStep
import java.util.UUID

/**
 * Orchestrateur local : moteur d'exécution pur.
 * Exécute le plan décidé par Qwen sans restriction, observe chaque étape
 * (répertoire, dépendances, cohérence) et rapporte fidèlement
 * COMMAND / OUTPUT / ERROR / EXIT_CODE / DURATION / FILES_CHANGED /
 * BUILD_RESULT / TEST_RESULT.
 */
class OrchestratorImpl(
    private val toolRouter: ToolRouter,
    private val executionManager: ExecutionManager,
    private val taskValidator: TaskValidator
) : Orchestrator {

    override fun execute(
        task: Task,
        confirm: ((TaskStep) -> Boolean)?,
        onStep: ((StepReport) -> Unit)?
    ): ExecutionReport {
        executionManager.reset()
        val start = System.currentTimeMillis()
        val stepReports = mutableListOf<StepReport>()
        val filesChanged = LinkedHashSet<String>()
        var buildResult: String? = null
        var testResult: String? = null

        for (step in task.steps) {
            if (executionManager.isStopRequested()) {
                return buildReport(task, stepReports, filesChanged, buildResult, testResult, PhaseState.CANCELLED, "Exécution arrêtée par l'utilisateur", start)
            }

            executionManager.awaitResume()
            if (executionManager.isStopRequested()) {
                return buildReport(task, stepReports, filesChanged, buildResult, testResult, PhaseState.CANCELLED, "Exécution arrêtée par l'utilisateur", start)
            }

            val accepted = confirm?.invoke(step) ?: true
            if (!accepted) {
                return buildReport(task, stepReports, filesChanged, buildResult, testResult, PhaseState.CANCELLED, "Étape refusée : ${step.command}", start)
            }

            val observations = executionManager.observe(step)
            val stepStart = System.currentTimeMillis()
            val result = executeStep(step)
            val duration = System.currentTimeMillis() - stepStart

            val validated = taskValidator.validateStepResult(result, step)
            filesChanged.addAll(taskValidator.detectFilesChanged(result.output, step.command))
            buildResult = taskValidator.analyzeBuildResult(result.output) ?: buildResult
            testResult = taskValidator.analyzeTestResult(result.output) ?: testResult

            val stepReport = StepReport(
                step = step,
                command = result.command,
                output = result.output,
                error = result.error,
                exitCode = result.exitCode,
                duration = duration,
                validated = validated,
                observations = observations
            )
            stepReports.add(stepReport)
            onStep?.invoke(stepReport)
        }

        val phase = if (executionManager.isStopRequested()) PhaseState.CANCELLED else PhaseState.COMPLETED
        val message = if (phase == PhaseState.CANCELLED) {
            "Exécution arrêtée (${stepReports.size}/${task.steps.size} étapes effectuées)"
        } else {
            "Plan exécuté (${stepReports.size} étapes)"
        }
        return buildReport(task, stepReports, filesChanged, buildResult, testResult, phase, message, start)
    }

    override fun stop() {
        executionManager.requestStop()
    }

    private fun executeStep(step: TaskStep): CommandResult {
        return toolRouter.routeStep(step)
    }

    private fun buildReport(
        task: Task,
        steps: List<StepReport>,
        filesChanged: Set<String>,
        buildResult: String?,
        testResult: String?,
        phase: PhaseState,
        message: String,
        start: Long
    ): ExecutionReport {
        return ExecutionReport(
            id = UUID.randomUUID().toString(),
            request = task.description,
            intent = task.intent,
            task = task,
            phase = phase,
            steps = steps,
            filesChanged = filesChanged.toList(),
            buildResult = buildResult,
            testResult = testResult,
            message = message,
            timestamp = System.currentTimeMillis()
        )
    }
}
