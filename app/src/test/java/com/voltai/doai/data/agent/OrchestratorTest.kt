package com.voltai.doai.data.agent

import com.voltai.doai.data.intelligence.IntentAnalyzerImpl
import com.voltai.doai.data.intelligence.TaskPlannerImpl
import com.voltai.doai.data.llama.MemoryManagerImpl
import com.voltai.doai.data.terminal.CommandExecutorImpl
import com.voltai.doai.domain.interfaces.Complexity
import com.voltai.doai.domain.interfaces.Priority
import com.voltai.doai.domain.interfaces.TaskStatus
import com.voltai.doai.domain.interfaces.ToolRouter
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.Intent
import com.voltai.doai.domain.models.PhaseState
import com.voltai.doai.domain.models.Task
import com.voltai.doai.domain.models.TaskStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class OrchestratorTest {

    // ------------------------------------------------------------------
    // Orchestrator
    // ------------------------------------------------------------------

    @Test
    fun orchestrator_executesAllSteps_andReturnsCompleteReport() {
        val router = FakeToolRouter(
            listOf(
                ok("echo a", "sortie A"),
                ok("echo b", "sortie B")
            )
        )
        val orchestrator = orchestratorWith(router)
        val executed = mutableListOf<String>()
        val report = orchestrator.execute(
            task(listOf(step("step-0", "echo a"), step("step-1", "echo b"))),
            onStep = { executed.add(it.command) }
        )

        assertEquals(2, report.steps.size)
        assertEquals(PhaseState.COMPLETED, report.phase)
        assertEquals("sortie A", report.steps[0].output)
        assertEquals("sortie B", report.steps[1].output)
        assertEquals(0, report.steps[0].exitCode)
        assertTrue(report.steps[0].duration >= 0)
        assertTrue(report.steps[0].validated)
        assertTrue(executed.size == 2)
        assertTrue(report.message.contains("Plan exécuté"))
    }

    @Test
    fun orchestrator_reportsBuildResult_fromOutput() {
        val router = FakeToolRouter(
            listOf(ok("./gradlew assembleRelease", "BUILD SUCCESSFUL in 1m"))
        )
        val report = orchestratorWith(router).execute(task(listOf(step("step-0", "./gradlew assembleRelease"))))

        assertEquals("BUILD SUCCESSFUL", report.buildResult)
    }

    @Test
    fun orchestrator_reportsTestResult_fromOutput() {
        val router = FakeToolRouter(
            listOf(ok("./gradlew test", "OK (12 tests, 0 failures)"))
        )
        val report = orchestratorWith(router).execute(task(listOf(step("step-0", "./gradlew test"))))

        assertEquals("TESTS OK", report.testResult)
    }

    @Test
    fun orchestrator_detectsFilesChanged_fromUnzipOutput() {
        val router = FakeToolRouter(
            listOf(ok("unzip -o archive.zip", "inflating: out/a.txt\ninflating: out/b.txt"))
        )
        val report = orchestratorWith(router).execute(task(listOf(step("step-0", "unzip -o archive.zip"))))

        assertEquals(listOf("out/a.txt", "out/b.txt"), report.filesChanged)
    }

    @Test
    fun orchestrator_reportsFailure_whenStepFails() {
        val router = FakeToolRouter(
            listOf(CommandResult("pkg install -y x", "", "Échec", 1, 10L))
        )
        val report = orchestratorWith(router).execute(task(listOf(step("step-0", "pkg install -y x"))))

        assertEquals(1, report.steps[0].exitCode)
        assertFalse(report.steps[0].validated)
        assertEquals("Échec", report.steps[0].error)
    }

    @Test
    fun orchestrator_stop_cancelsRemainingSteps() {
        val router = FakeToolRouter(
            listOf(ok("echo a", "A"), ok("echo b", "B"), ok("echo c", "C"))
        )
        val manager = ExecutionManagerImpl()
        val orchestrator = OrchestratorImpl(router, manager, TaskValidatorImpl())

        val result = arrayOfNulls<ExecutionReport>(1)
        val thread = Thread {
            result[0] = orchestrator.execute(
                task(listOf(step("step-0", "echo a"), step("step-1", "echo b"), step("step-2", "echo c"))),
                confirm = {
                    orchestrator.stop()
                    true
                }
            )
        }
        thread.start()
        thread.join(5000)

        assertEquals(PhaseState.CANCELLED, result[0]?.phase)
        assertEquals(1, result[0]?.steps?.size)
        assertTrue(result[0]?.message?.contains("arrêtée") == true)
    }

    @Test
    fun orchestrator_onStep_callbackInvokedForEachStep() {
        val router = FakeToolRouter(listOf(ok("echo a", "A"), ok("echo b", "B")))
        val steps = mutableListOf<String>()
        orchestratorWith(router).execute(
            task(listOf(step("step-0", "echo a"), step("step-1", "echo b"))),
            onStep = { steps.add(it.step.id) }
        )
        assertEquals(listOf("step-0", "step-1"), steps)
    }

    // ------------------------------------------------------------------
    // TaskValidator
    // ------------------------------------------------------------------

    private val validator = TaskValidatorImpl()

    @Test
    fun validator_zeroExitCode_isValid() {
        val result = CommandResult("cmd", "output", null, 0, 10L)
        assertTrue(validator.validateStepResult(result, step("s", "cmd")))
    }

    @Test
    fun validator_nonZeroExitCode_isInvalid() {
        val result = CommandResult("cmd", "", "erreur", 1, 10L)
        assertFalse(validator.validateStepResult(result, step("s", "cmd")))
    }

    @Test
    fun validator_expectedOutputMismatch_isInvalid() {
        val result = CommandResult("cmd", "autre chose", null, 0, 10L)
        val myStep = TaskStep("s", "desc", "shell", "cmd", emptyList(), "attendu", 120L)
        assertFalse(validator.validateStepResult(result, myStep))
    }

    @Test
    fun validator_blankOutputWithError_isInvalid() {
        val result = CommandResult("cmd", "", "erreur silencieuse", 0, 10L)
        assertFalse(validator.validateStepResult(result, step("s", "cmd")))
    }

    @Test
    fun validator_validateExecution_rejectsFailedSteps() {
        val report = ExecutionReport(
            "id", "req", Intent("X", "", emptyList(), emptyList(), Complexity.SIMPLE, 0.5f),
            null, PhaseState.COMPLETED,
            listOf(
                com.voltai.doai.domain.models.StepReport(step("s0", "a"), "a", "", null, 0, 1L, true),
                com.voltai.doai.domain.models.StepReport(step("s1", "b"), "b", "", null, 1, 1L, false)
            ),
            emptyList(), null, null, "msg", 0L
        )
        assertFalse(validator.validateExecution(report))
    }

    @Test
    fun validator_validateExecution_acceptsAllZero() {
        val report = ExecutionReport(
            "id", "req", Intent("X", "", emptyList(), emptyList(), Complexity.SIMPLE, 0.5f),
            null, PhaseState.COMPLETED,
            listOf(
                com.voltai.doai.domain.models.StepReport(step("s0", "a"), "a", "", null, 0, 1L, true)
            ),
            emptyList(), null, null, "msg", 0L
        )
        assertTrue(validator.validateExecution(report))
    }

    @Test
    fun validator_detectFilesChanged_parsesInflatingLines() {
        val files = validator.detectFilesChanged("inflating: out/a.txt\n  inflating: out/b.txt", "unzip -o x.zip")
        assertTrue(files.contains("out/a.txt"))
        assertTrue(files.contains("out/b.txt"))
    }

    @Test
    fun validator_analyzeBuildResult() {
        assertEquals("BUILD SUCCESSFUL", validator.analyzeBuildResult("> Task :app:compileDebugKotlin\nBUILD SUCCESSFUL in 2m"))
        assertEquals("BUILD FAILED", validator.analyzeBuildResult("FAILURE: Build failed with an exception."))
        assertEquals(null, validator.analyzeBuildResult("simple output"))
    }

    @Test
    fun validator_analyzeTestResult() {
        assertEquals("TESTS OK", validator.analyzeTestResult("OK (89 tests, 0 failures)"))
        assertEquals("TESTS FAILED (3 échec(s))", validator.analyzeTestResult("3 tests, 3 failures"))
        assertEquals(null, validator.analyzeTestResult("rien"))
    }

    // ------------------------------------------------------------------
    // ExecutionManager
    // ------------------------------------------------------------------

    @Test
    fun executionManager_pauseResume_blocksAndReleases() {
        val manager = ExecutionManagerImpl()
        manager.pause()
        val progress = java.util.concurrent.atomic.AtomicInteger(0)
        val t = Thread {
            manager.awaitResume()
            progress.incrementAndGet()
        }
        t.start()
        Thread.sleep(150)
        assertEquals(0, progress.get())
        manager.resume()
        t.join(2000)
        assertEquals(1, progress.get())
    }

    @Test
    fun executionManager_stopRequested() {
        val manager = ExecutionManagerImpl()
        assertFalse(manager.isStopRequested())
        manager.requestStop()
        assertTrue(manager.isStopRequested())
        manager.reset()
        assertFalse(manager.isStopRequested())
    }

    @Test
    fun executionManager_history_keepsReports() {
        val manager = ExecutionManagerImpl()
        manager.addToHistory(dummyReport("1"))
        manager.addToHistory(dummyReport("2"))
        assertEquals(2, manager.getHistory().size)
        manager.clearHistory()
        assertEquals(0, manager.getHistory().size)
    }

    @Test
    fun executionManager_observe_reportsContext() {
        val manager = ExecutionManagerImpl()
        val observations = manager.observe(step("s", "echo ok"))
        assertTrue(observations.isNotEmpty())
        assertTrue(observations.any { it.contains("Outil décidé par Qwen") })
    }

    // ------------------------------------------------------------------
    // AgentEngine
    // ------------------------------------------------------------------

    @Test
    fun agentEngine_executesRequest_andProducesMessages() {
        val router = FakeToolRouter(listOf(ok("unzip -o archive.zip", "inflating: a.txt")))
        val engine = engineWith(router, confirmationMode = false)

        runBlocking {
            engine.send("Décompresse archive.zip")
        }

        assertTrue(engine.messages.value.any { it.isUser })
        assertTrue(engine.messages.value.any { !it.isUser })
        assertEquals(PhaseState.COMPLETED, engine.currentPhase.value)
        assertTrue(engine.history.value.isNotEmpty())
        val report = engine.history.value.last()
        assertEquals("a.txt", report.filesChanged.firstOrNull())
        assertTrue(report.buildResult == null || report.testResult == null || report.filesChanged.isNotEmpty())
    }

    @Test
    fun agentEngine_confirmationMode_waitsForConfirm() {
        val router = FakeToolRouter(listOf(ok("echo x", "modifié")))
        val engine = engineWith(router, confirmationMode = true)

        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            engine.send("Modifie le code dans Main.kt")
        }
        Thread.sleep(400)
        assertEquals(PhaseState.AWAITING_CONFIRMATION, engine.currentPhase.value)
        assertFalse(engine.history.value.isNotEmpty())

        engine.confirmAction()
        runBlocking { job.join() }

        assertEquals(PhaseState.COMPLETED, engine.currentPhase.value)
        assertTrue(engine.messages.value.any { !it.isUser })
    }

    @Test
    fun agentEngine_confirmationMode_denyCancels() {
        val router = FakeToolRouter(listOf(ok("echo x", "modifié")))
        val engine = engineWith(router, confirmationMode = true)

        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            engine.send("Modifie le code dans Main.kt")
        }
        Thread.sleep(400)
        engine.denyAction()
        runBlocking { job.join() }

        assertEquals(PhaseState.CANCELLED, engine.currentPhase.value)
        assertTrue(engine.messages.value.any { it.content.contains("annulée") })
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun orchestratorWith(router: ToolRouter): OrchestratorImpl {
        return OrchestratorImpl(router, ExecutionManagerImpl(), TaskValidatorImpl())
    }

    private fun engineWith(router: ToolRouter, confirmationMode: Boolean): AgentEngineImpl {
        val memory = MemoryManagerImpl(
            memoryFile = File(System.getProperty("java.io.tmpdir"), "qwen_memory_${UUID.randomUUID()}.txt")
        )
        val orchestrator = orchestratorWith(router)
        val manager = ExecutionManagerImpl()
        val engine = AgentEngineImpl(
            intentAnalyzer = IntentAnalyzerImpl(),
            taskPlanner = TaskPlannerImpl(),
            contextManager = com.voltai.doai.data.intelligence.ContextManagerImpl(),
            commandExecutor = CommandExecutorImpl(),
            orchestrator = orchestrator,
            executionManager = manager,
            taskValidator = TaskValidatorImpl(),
            memoryManager = memory
        )
        engine.setConfirmationMode(confirmationMode)
        return engine
    }

    private fun ok(command: String, output: String): CommandResult =
        CommandResult(command, output, null, 0, 10L)

    private fun step(id: String, command: String): TaskStep =
        TaskStep(id, "desc $id", "shell", command, emptyList(), null, 120L)

    private fun task(steps: List<TaskStep>): Task {
        val intent = Intent("TEST", "", listOf("shell"), emptyList(), Complexity.SIMPLE, 0.9f)
        return Task(
            id = UUID.randomUUID().toString(),
            description = "test",
            intent = intent,
            steps = steps,
            estimatedDuration = 1000L,
            priority = Priority.MEDIUM,
            status = TaskStatus.PENDING
        )
    }

    private fun dummyReport(id: String): ExecutionReport {
        return ExecutionReport(
            id = id,
            request = "req",
            intent = Intent("X", "", emptyList(), emptyList(), Complexity.SIMPLE, 0.5f),
            task = null,
            phase = PhaseState.COMPLETED,
            steps = emptyList(),
            filesChanged = emptyList(),
            buildResult = null,
            testResult = null,
            message = "ok",
            timestamp = 0L
        )
    }

    private class FakeToolRouter(
        private val results: List<CommandResult>
    ) : ToolRouter {
        private var index = 0

        override fun routeStep(step: TaskStep): CommandResult {
            val result = if (index < results.size) results[index++] else CommandResult(step.command, "ok", null, 0, 5L)
            return result
        }

        override fun selectTool(action: String, target: String): String = "shell"
        override fun isToolAvailable(tool: String): Boolean = true
        override fun getToolCapabilities(tool: String): List<String> = emptyList()
        override fun optimizeToolSelection(step: TaskStep): TaskStep = step
    }
}
