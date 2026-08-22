package com.voltai.doai.domain.models

data class ExecutionContext(
    val sessionId: String,
    val currentDirectory: String,
    val recentFiles: List<String>,
    val recentCommands: List<String>,
    val environmentVariables: Map<String, String>,
    val activeTools: List<String>,
    val lastAction: String?,
    val lastResult: String?,
    val timestamp: Long
)