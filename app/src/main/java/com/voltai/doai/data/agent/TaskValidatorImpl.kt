package com.voltai.doai.data.agent

import com.voltai.doai.domain.interfaces.TaskValidator
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.PhaseState
import com.voltai.doai.domain.models.TaskStep

class TaskValidatorImpl : TaskValidator {

    override fun validateStepResult(result: CommandResult, step: TaskStep): Boolean {
        if (result.exitCode != 0) return false
        val expected = step.expectedOutput
        if (expected != null && !result.output.contains(expected)) return false
        if (result.error != null && result.output.isBlank()) return false
        return true
    }

    override fun validateExecution(report: ExecutionReport): Boolean {
        if (report.phase == PhaseState.CANCELLED) return false
        if (report.steps.isEmpty()) return false
        return report.steps.all { it.exitCode == 0 }
    }

    override fun detectFilesChanged(output: String, command: String): List<String> {
        val changed = LinkedHashSet<String>()

        Regex("(?:inflating|extracting|creating|writing)\\s*:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE)
            .findAll(output)
            .forEach { changed.add(it.groupValues[1].trim()) }

        Regex("(?:Archive|unzip|zip)\\s*:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE)
            .findAll(output)
            .forEach { changed.add(it.groupValues[1].trim()) }

        return changed.toList().take(50)
    }

    override fun analyzeBuildResult(output: String): String? {
        return when {
            output.contains("BUILD SUCCESSFUL") -> "BUILD SUCCESSFUL"
            output.contains("BUILD FAILED") -> "BUILD FAILED"
            output.contains("FAILURE: Build failed") -> "BUILD FAILED"
            output.contains("BUILD COMPLETE") -> "BUILD COMPLETE"
            else -> null
        }
    }

    override fun analyzeTestResult(output: String): String? {
        val failMatch = Regex("(\\d+) failures").find(output)
        if (failMatch != null) {
            val fails = failMatch.groupValues[1].toIntOrNull() ?: 1
            return if (fails == 0) "TESTS OK" else "TESTS FAILED ($fails échec(s))"
        }
        if (output.contains("BUILD SUCCESSFUL") && output.contains("tests")) return "TESTS OK"
        if (output.contains("FAILURE:")) return "TESTS FAILED"
        return null
    }
}
