package com.voltai.doai.domain.models

data class TaskStep(
    val id: String,
    val description: String,
    val tool: String,
    val command: String,
    val dependencies: List<String>,
    val expectedOutput: String?,
    val timeout: Long
)