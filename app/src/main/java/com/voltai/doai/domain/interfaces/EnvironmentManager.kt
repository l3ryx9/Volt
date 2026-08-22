package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.CommandResult

interface EnvironmentManager {
    fun initializeTermuxEnvironment(): CommandResult
    fun installBasicPackages(): CommandResult
    fun installProotDistro(): CommandResult
    fun installUbuntu(): CommandResult
    fun isUbuntuInstalled(): Boolean
    fun setupUbuntuEnvironment(): CommandResult
    fun getEnvironmentStatus(): EnvironmentStatus
}

data class EnvironmentStatus(
    val termuxInitialized: Boolean = false,
    val basicPackagesInstalled: Boolean = false,
    val prootDistroInstalled: Boolean = false,
    val ubuntuInstalled: Boolean = false,
    val ubuntuReady: Boolean = false
)
