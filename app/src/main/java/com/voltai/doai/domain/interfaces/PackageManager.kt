package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.CommandResult

interface PackageManager {
    fun installPackage(packageName: String): CommandResult
    fun removePackage(packageName: String): CommandResult
    fun updatePackages(): CommandResult
    fun upgradePackages(): CommandResult
    fun searchPackage(query: String): CommandResult
    fun listInstalledPackages(): CommandResult
    fun isPackageInstalled(packageName: String): Boolean
}
