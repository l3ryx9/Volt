package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.TaskStep

interface ToolRouter {
    fun routeStep(step: TaskStep): CommandResult
    fun selectTool(action: String, target: String): String
    fun isToolAvailable(tool: String): Boolean
    fun getToolCapabilities(tool: String): List<String>
    fun optimizeToolSelection(step: TaskStep): TaskStep
}

data class Tool(
    val name: String,
    val description: String,
    val capabilities: List<String>,
    val requiredPackages: List<String>,
    val supportedTargets: List<String>
)