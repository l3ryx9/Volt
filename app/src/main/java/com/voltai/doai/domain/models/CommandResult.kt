package com.voltai.doai.domain.models

data class CommandResult(
    val command: String,
    val output: String,
    val error: String?,
    val exitCode: Int,
    val duration: Long
)
