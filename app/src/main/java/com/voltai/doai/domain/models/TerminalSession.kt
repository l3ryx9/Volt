package com.voltai.doai.domain.models

data class TerminalSession(
    val id: String,
    val isActive: Boolean = false,
    val currentDirectory: String = "/",
    val environment: Map<String, String> = emptyMap()
)
