package com.voltai.doai.domain.models

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val attachedFile: String? = null,
    val actions: List<String> = emptyList(),
    val results: List<String> = emptyList()
)
