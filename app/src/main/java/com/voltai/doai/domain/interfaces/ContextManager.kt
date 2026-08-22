package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ExecutionContext
import kotlinx.coroutines.flow.Flow

interface ContextManager {
    fun createContext(sessionId: String): ExecutionContext
    fun updateContext(sessionId: String, updates: ContextUpdate)
    fun getContext(sessionId: String): ExecutionContext?
    fun getRecentFiles(sessionId: String): List<String>
    fun getRecentCommands(sessionId: String): List<String>
    fun getEnvironmentInfo(sessionId: String): EnvironmentInfo
    fun clearContext(sessionId: String)
    fun getContextHistory(sessionId: String): Flow<ExecutionContext>
}

data class ContextUpdate(
    val currentDirectory: String? = null,
    val recentFiles: List<String>? = null,
    val recentCommands: List<String>? = null,
    val environmentVariables: Map<String, String>? = null,
    val activeTools: List<String>? = null,
    val lastAction: String? = null,
    val lastResult: String? = null
)

data class EnvironmentInfo(
    val osName: String,
    val osVersion: String,
    val architecture: String,
    val availableMemory: Long,
    val availableDisk: Long,
    val installedPackages: List<String>,
    val runningProcesses: List<String>
)