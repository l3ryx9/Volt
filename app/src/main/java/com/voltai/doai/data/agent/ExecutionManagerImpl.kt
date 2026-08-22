package com.voltai.doai.data.agent

import com.voltai.doai.data.terminal.ShellExecutor
import com.voltai.doai.domain.interfaces.ExecutionManager
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.TaskStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExecutionManagerImpl : ExecutionManager {

    private val _history = MutableStateFlow<List<ExecutionReport>>(emptyList())

    @Volatile
    private var stopRequested = false

    @Volatile
    private var paused = false
    private val pauseMonitor = Object()

    override fun observe(step: TaskStep): List<String> {
        val observations = mutableListOf<String>()
        observations.add("Étape : ${step.description.ifBlank { step.command }}")
        observations.add("Outil décidé par Qwen : ${step.tool.ifBlank { "shell" }}")
        observations.add("Répertoire : ${workingDirectory()}")
        if (ShellExecutor.isUbuntuInstalled) {
            observations.add("Environnement Ubuntu 24.04 (proot-distro) présent")
        } else {
            observations.add("Environnement Ubuntu non détecté (proot-distro) : commande exécutée sur l'environnement disponible")
        }
        val deps = step.dependencies
        if (deps.isNotEmpty()) observations.add("Dépendances : ${deps.joinToString(", ")}")
        return observations
    }

    override fun requestStop() {
        stopRequested = true
        resume()
    }

    override fun isStopRequested(): Boolean = stopRequested

    override fun reset() {
        stopRequested = false
        paused = false
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
        synchronized(pauseMonitor) {
            pauseMonitor.notifyAll()
        }
    }

    override fun isPaused(): Boolean = paused

    override fun awaitResume() {
        synchronized(pauseMonitor) {
            while (paused) {
                try {
                    pauseMonitor.wait()
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    override fun addToHistory(report: ExecutionReport) {
        _history.value = (_history.value + report).takeLast(50)
    }

    override fun clearHistory() {
        _history.value = emptyList()
    }

    override fun getHistory(): List<ExecutionReport> = _history.value

    override fun historyFlow(): Flow<List<ExecutionReport>> = _history.asStateFlow()

    private fun workingDirectory(): String {
        return runCatching { System.getProperty("user.dir") }.getOrNull() ?: "indéfini"
    }
}
