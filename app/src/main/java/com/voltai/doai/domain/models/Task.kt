package com.voltai.doai.domain.models

import com.voltai.doai.domain.interfaces.Priority
import com.voltai.doai.domain.interfaces.TaskStatus

data class Task(
    val id: String,
    val description: String,
    val intent: Intent,
    val steps: List<TaskStep>,
    val estimatedDuration: Long,
    val priority: Priority,
    val status: TaskStatus
)