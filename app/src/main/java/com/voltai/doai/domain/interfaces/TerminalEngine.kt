package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.TerminalSession
import kotlinx.coroutines.flow.Flow

interface TerminalEngine {
    fun createSession(): TerminalSession
    fun executeCommand(sessionId: String, command: String): CommandResult
    fun getSessionOutput(sessionId: String): Flow<String>
    fun closeSession(sessionId: String)
    fun isSessionActive(sessionId: String): Boolean
}
