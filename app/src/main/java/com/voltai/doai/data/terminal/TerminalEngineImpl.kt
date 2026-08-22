package com.voltai.doai.data.terminal

import com.voltai.doai.domain.interfaces.TerminalEngine
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TerminalEngineImpl : TerminalEngine {

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val outputs = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    override fun createSession(): TerminalSession {
        val session = TerminalSession(
            id = UUID.randomUUID().toString(),
            isActive = true,
            currentDirectory = ShellExecutor.TERMUX_HOME
        )
        sessions[session.id] = session
        outputs[session.id] = MutableSharedFlow(extraBufferCapacity = 100)
        return session
    }

    override fun executeCommand(sessionId: String, command: String): CommandResult {
        val session = sessions[sessionId]
            ?: return CommandResult(command, "", "Session inactive ou inconnue", -1, 0L)

        val result = ShellExecutor.execute(command)
        outputs[sessionId]?.tryEmit(buildOutput(result))

        if (result.exitCode == 0) {
            val currentDir = session.currentDirectory
            val newDir = extractWorkingDirectory(result.output)
            sessions[sessionId] = session.copy(currentDirectory = newDir ?: currentDir)
        }
        return result
    }

    override fun getSessionOutput(sessionId: String): Flow<String> {
        return outputs.getOrPut(sessionId) { MutableSharedFlow(extraBufferCapacity = 100) }.asSharedFlow()
    }

    override fun closeSession(sessionId: String) {
        sessions[sessionId]?.let { sessions[sessionId] = it.copy(isActive = false) }
        outputs.remove(sessionId)
    }

    override fun isSessionActive(sessionId: String): Boolean {
        return sessions[sessionId]?.isActive == true
    }

    private fun buildOutput(result: CommandResult): String {
        val errorPart = result.error?.let { "\n[ERREUR] $it" } ?: ""
        return "\$ ${result.command}\n${result.output}$errorPart\n[exit: ${result.exitCode}]"
    }

    private fun extractWorkingDirectory(output: String): String? {
        if (!output.contains("pwd")) return null
        val line = output.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("/") } ?: return null
        return line
    }
}
