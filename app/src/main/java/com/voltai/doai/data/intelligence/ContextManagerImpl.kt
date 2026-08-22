package com.voltai.doai.data.intelligence

import com.voltai.doai.data.terminal.ShellExecutor
import com.voltai.doai.domain.interfaces.ContextManager
import com.voltai.doai.domain.interfaces.ContextUpdate
import com.voltai.doai.domain.interfaces.EnvironmentInfo
import com.voltai.doai.domain.models.ExecutionContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

class ContextManagerImpl : ContextManager {

    private val contexts = ConcurrentHashMap<String, ExecutionContext>()
    private val history = ConcurrentHashMap<String, MutableSharedFlow<ExecutionContext>>()

    override fun createContext(sessionId: String): ExecutionContext {
        val context = ExecutionContext(
            sessionId = sessionId,
            currentDirectory = ShellExecutor.TERMUX_HOME,
            recentFiles = emptyList(),
            recentCommands = emptyList(),
            environmentVariables = emptyMap(),
            activeTools = emptyList(),
            lastAction = null,
            lastResult = null,
            timestamp = System.currentTimeMillis()
        )
        contexts[sessionId] = context
        history[sessionId] = MutableSharedFlow(extraBufferCapacity = 50)
        return context
    }

    override fun updateContext(sessionId: String, updates: ContextUpdate) {
        val current = contexts[sessionId] ?: createContext(sessionId)
        val updated = current.copy(
            currentDirectory = updates.currentDirectory ?: current.currentDirectory,
            recentFiles = updates.recentFiles ?: current.recentFiles,
            recentCommands = updates.recentCommands ?: current.recentCommands,
            environmentVariables = updates.environmentVariables ?: current.environmentVariables,
            activeTools = updates.activeTools ?: current.activeTools,
            lastAction = updates.lastAction ?: current.lastAction,
            lastResult = updates.lastResult ?: current.lastResult,
            timestamp = System.currentTimeMillis()
        )
        contexts[sessionId] = updated
        history[sessionId]?.tryEmit(updated)
    }

    override fun getContext(sessionId: String): ExecutionContext? {
        return contexts[sessionId]
    }

    override fun getRecentFiles(sessionId: String): List<String> {
        return contexts[sessionId]?.recentFiles ?: emptyList()
    }

    override fun getRecentCommands(sessionId: String): List<String> {
        return contexts[sessionId]?.recentCommands ?: emptyList()
    }

    override fun getEnvironmentInfo(sessionId: String): EnvironmentInfo {
        val osName = ShellExecutor.execute("uname -s").output.ifBlank { "Linux" }
        val osVersion = ShellExecutor.execute("uname -r").output
        val arch = ShellExecutor.execute("uname -m").output.ifBlank { "arm64" }
        val availableMemory = readMemInfo()
        val availableDisk = readDiskSpace()
        val installedPackages = listInstalledPackages()
        val runningProcesses = listRunningProcesses()

        updateContext(sessionId, ContextUpdate(environmentVariables = mapOf("OS" to osName, "ARCH" to arch)))

        return EnvironmentInfo(
            osName = osName,
            osVersion = osVersion,
            architecture = arch,
            availableMemory = availableMemory,
            availableDisk = availableDisk,
            installedPackages = installedPackages,
            runningProcesses = runningProcesses
        )
    }

    override fun clearContext(sessionId: String) {
        contexts.remove(sessionId)
        history.remove(sessionId)
    }

    override fun getContextHistory(sessionId: String): Flow<ExecutionContext> {
        return history.getOrPut(sessionId) { MutableSharedFlow(extraBufferCapacity = 50) }.asSharedFlow()
    }

    private fun readMemInfo(): Long {
        return runCatching {
            java.io.File("/proc/meminfo").readLines()
                .firstOrNull { it.startsWith("MemAvailable") }
                ?.replace(Regex("[^0-9]"), "")
                ?.toLongOrNull()
                ?: 0L
        }.getOrDefault(0L)
    }

    private fun readDiskSpace(): Long {
        val result = ShellExecutor.execute("df -k /data")
        return result.output.lineSequence()
            .mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(3)?.toLongOrNull() }
            .sum() * 1024L
    }

    private fun listInstalledPackages(): List<String> {
        val result = ShellExecutor.execute("pkg list-installed", timeoutSeconds = 30L)
        if (result.exitCode != 0) return emptyList()
        return result.output.lineSequence()
            .mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { pkg -> pkg.isNotBlank() && pkg != "package" } }
            .toList()
            .take(100)
    }

    private fun listRunningProcesses(): List<String> {
        val result = ShellExecutor.execute("ps aux", timeoutSeconds = 15L)
        if (result.exitCode != 0) return emptyList()
        return result.output.lineSequence()
            .mapNotNull { it.trim().takeIf { line -> line.isNotBlank() && !line.startsWith("USER") } }
            .map { it.split(Regex("\\s+")).lastOrNull().orEmpty() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
            .take(50)
    }
}
