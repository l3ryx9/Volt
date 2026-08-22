package com.voltai.doai.data.terminal

import com.voltai.doai.domain.interfaces.EnvironmentManager
import com.voltai.doai.domain.interfaces.EnvironmentStatus
import com.voltai.doai.domain.models.CommandResult
import java.io.File

class EnvironmentManagerImpl : EnvironmentManager {

    override fun initializeTermuxEnvironment(): CommandResult {
        // Auto-init du runtime embarqué : la décompression du bootstrap se
        // fait automatiquement avant de vérifier l'environnement.
        if (!TermuxRuntimeManager.isRuntimeInstalled) {
            TermuxRuntimeManager.init(com.voltai.doai.di.ServiceLocator.appContext())
        }
        val home = File(ShellExecutor.TERMUX_HOME)
        return if (ShellExecutor.isTermuxRuntimeAvailable || home.exists()) {
            CommandResult(
                command = "setup ${ShellExecutor.TERMUX_HOME}",
                output = "Environnement Termux disponible (${ShellExecutor.TERMUX_PREFIX})",
                error = null,
                exitCode = 0,
                duration = 0L
            )
        } else {
            CommandResult(
                command = "setup ${ShellExecutor.TERMUX_HOME}",
                output = "",
                error = "Runtime Termux non détecté : ${TermuxRuntimeManager.lastError ?: "bootstrap indisponible"}. Réessayez ou vérifiez le réseau.",
                exitCode = -1,
                duration = 0L
            )
        }
    }

    override fun installBasicPackages(): CommandResult {
        val packages = "git python curl wget zip unzip tar grep sed awk findutils file openssl"
        return ShellExecutor.execute("pkg update -y && pkg install -y $packages", timeoutSeconds = 600L)
    }

    override fun installProotDistro(): CommandResult {
        return ShellExecutor.execute(
            "pkg update -y && pkg install -y proot-distro",
            timeoutSeconds = 600L
        )
    }

    override fun installUbuntu(): CommandResult {
        return ShellExecutor.execute("proot-distro install ubuntu:24.04", timeoutSeconds = 900L)
    }

    override fun isUbuntuInstalled(): Boolean = ShellExecutor.isUbuntuInstalled

    override fun setupUbuntuEnvironment(): CommandResult {
        return ShellExecutor.executeUbuntu(
            "apt update -y && apt install -y python3 python3-pip git curl wget unzip zip tar nano vim",
            timeoutSeconds = 900L
        )
    }

    override fun getEnvironmentStatus(): EnvironmentStatus {
        return EnvironmentStatus(
            termuxInitialized = ShellExecutor.isTermuxRuntimeAvailable,
            basicPackagesInstalled = checkBasicPackagesInstalled(),
            prootDistroInstalled = ShellExecutor.isProotDistroInstalled,
            ubuntuInstalled = ShellExecutor.isUbuntuInstalled,
            ubuntuReady = ShellExecutor.isUbuntuInstalled
        )
    }

    private fun checkBasicPackagesInstalled(): Boolean {
        if (!ShellExecutor.isTermuxRuntimeAvailable) return false
        val result = ShellExecutor.execute("pkg list-installed", timeoutSeconds = 60L)
        if (result.exitCode != 0) return false
        val required = listOf("git", "python", "curl", "wget", "zip", "unzip", "tar", "grep", "sed", "awk", "findutils")
        val installed = result.output.lineSequence()
            .map { it.trim().split(Regex("\\s+")).firstOrNull().orEmpty() }
            .toSet()
        return required.all { installed.contains(it) }
    }
}
