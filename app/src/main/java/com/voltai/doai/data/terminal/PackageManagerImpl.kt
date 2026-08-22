package com.voltai.doai.data.terminal

import com.voltai.doai.domain.interfaces.PackageManager
import com.voltai.doai.domain.models.CommandResult

class PackageManagerImpl : PackageManager {

    override fun installPackage(packageName: String): CommandResult {
        if (packageName.isBlank()) {
            return CommandResult("pkg install", "", "Aucun nom de package spécifié", -1, 0L)
        }
        var result = ShellExecutor.execute("pkg install -y $packageName", timeoutSeconds = 300L)
        // Auto-réparation : erreur réseau/dépôt → pkg update puis réessai.
        if (result.exitCode != 0) {
            ShellExecutor.execute("pkg update -y", timeoutSeconds = 300L)
            result = ShellExecutor.execute("pkg install -y $packageName", timeoutSeconds = 300L)
        }
        return result
    }

    override fun removePackage(packageName: String): CommandResult {
        if (packageName.isBlank()) {
            return CommandResult("pkg uninstall", "", "Aucun nom de package spécifié", -1, 0L)
        }
        return ShellExecutor.execute("pkg uninstall -y $packageName", timeoutSeconds = 180L)
    }

    override fun updatePackages(): CommandResult {
        var result = ShellExecutor.execute("pkg update -y", timeoutSeconds = 300L)
        if (result.exitCode != 0) {
            result = ShellExecutor.execute("pkg update -y", timeoutSeconds = 300L)
        }
        return result
    }

    override fun upgradePackages(): CommandResult {
        return ShellExecutor.execute("pkg upgrade -y", timeoutSeconds = 600L)
    }

    override fun searchPackage(query: String): CommandResult {
        if (query.isBlank()) {
            return CommandResult("pkg search", "", "Aucune requête de recherche spécifiée", -1, 0L)
        }
        return ShellExecutor.execute("pkg search $query", timeoutSeconds = 120L)
    }

    override fun listInstalledPackages(): CommandResult {
        return ShellExecutor.execute("pkg list-installed", timeoutSeconds = 120L)
    }

    override fun isPackageInstalled(packageName: String): Boolean {
        val result = ShellExecutor.execute("pkg list-installed", timeoutSeconds = 60L)
        if (result.exitCode != 0) return false
        return result.output.lineSequence()
            .map { it.trim().split(Regex("\\s+")).firstOrNull().orEmpty() }
            .any { it == packageName }
    }
}
