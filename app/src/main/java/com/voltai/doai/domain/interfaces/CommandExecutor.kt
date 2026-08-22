package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.CommandResult
import kotlinx.coroutines.flow.Flow

interface CommandExecutor {
    fun analyzeRequest(request: String): String
    fun executeCommand(command: String): CommandResult
    fun executeCommand(command: String, timeoutSeconds: Long): CommandResult
    fun getExecutionLog(): Flow<List<CommandResult>>
    fun executeWithUbuntu(command: String): CommandResult
    fun setUbuntuPrefix(enabled: Boolean)
    fun isUbuntuPrefixEnabled(): Boolean
}
