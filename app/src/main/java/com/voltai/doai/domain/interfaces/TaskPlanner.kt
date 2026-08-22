package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.Intent
import com.voltai.doai.domain.models.Task
import com.voltai.doai.domain.models.TaskStep

interface TaskPlanner {
    fun createPlan(intent: Intent): Task
    fun addStep(task: Task, step: TaskStep): Task
    fun validatePlan(task: Task): Boolean
    fun optimizePlan(task: Task): Task
    fun estimateExecutionTime(task: Task): Long
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}