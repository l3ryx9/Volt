package com.voltai.doai.data.tools

import android.content.Context
import com.voltai.doai.data.terminal.ShellExecutor
import com.voltai.doai.data.terminal.TermuxRuntimeManager
import com.voltai.doai.domain.models.CommandResult
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auto-réparation des dépendances.
 *
 * Détecte les erreurs de type « commande introuvable / outil manquant »
 * ([shouldRepair]) et relance automatiquement voltai-fix.sh pour installer
 * les paquets manquants (apt, wrappers du bundle, wheels python, JRE).
 *
 * Anti-boucle : un cooldown global évite de relancer la réparation trop
 * souvent, et une seule tentative automatique est faite par appelant.
 */
object AutoFixer {

    private const val COOLDOWN_MS = 120_000L

    @Volatile
    private var lastRepairTime = 0L

    @Volatile
    private var repairing = false

    /**
     * Progression de la réparation en cours, exposée pour la barre de
     * progression visible dans l'UI (identique au flux d'installation).
     */
    private val _repairStatus = MutableStateFlow(ToolchainStatus())
    val repairStatus: StateFlow<ToolchainStatus> = _repairStatus.asStateFlow()

    private fun beginRepair() {
        _repairStatus.value = ToolchainStatus(
            phase = ToolchainPhase.RUNNING,
            message = "Réparation de l'environnement…",
            progress = 0f
        )
    }

    private fun endRepair(success: Boolean, message: String, progress: Float) {
        _repairStatus.value = ToolchainStatus(
            phase = if (success) ToolchainPhase.DONE else ToolchainPhase.FAILED,
            message = message,
            progress = progress
        )
    }

    /** Vrai si [result] ressemble à un outil/package manquant. */
    fun shouldRepair(result: CommandResult): Boolean {
        if (result.exitCode == 0) return false
        if (result.exitCode == 127) return true
        val text = ((result.error ?: "") + "\n" + result.output).lowercase()
        return MISSING_MARKERS.any { text.contains(it) }
    }

    /**
     * Tente la réparation (une seule, soumise au cooldown).
     * Renvoie le résultat du script, ou un CommandResult explicatif si la
     * réparation est déjà en cours / trop récente.
     */
    fun requestRepair(
        context: Context,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CommandResult {
        if (repairing) {
            return CommandResult(
                "voltai-fix.sh",
                "",
                "Réparation déjà en cours",
                -1,
                0L
            )
        }
        val now = System.currentTimeMillis()
        if (now - lastRepairTime < COOLDOWN_MS) {
            return CommandResult(
                "voltai-fix.sh",
                "",
                "Réparation ignorée (tentative trop récente)",
                -1,
                0L
            )
        }

        repairing = true
        beginRepair()
        return try {
            lastRepairTime = System.currentTimeMillis()
            val result = doRepair(context) { p, m ->
                onProgress(p, m)
                _repairStatus.value = ToolchainStatus(
                    phase = ToolchainPhase.RUNNING,
                    message = m,
                    progress = p
                )
            }
            endRepair(result.exitCode == 0, result.output.ifBlank { result.error ?: "" }, 1f)
            result
        } finally {
            repairing = false
        }
    }

    /** Force la réparation (bouton « Réparer »), sans cooldown. */
    fun repairNow(
        context: Context,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): CommandResult {
        if (repairing) {
            return CommandResult(
                "voltai-fix.sh",
                "",
                "Réparation déjà en cours",
                -1,
                0L
            )
        }
        repairing = true
        beginRepair()
        return try {
            val result = doRepair(context) { p, m ->
                onProgress(p, m)
                _repairStatus.value = ToolchainStatus(
                    phase = ToolchainPhase.RUNNING,
                    message = m,
                    progress = p
                )
            }
            endRepair(result.exitCode == 0, result.output.ifBlank { result.error ?: "" }, 1f)
            result
        } finally {
            repairing = false
        }
    }

    private fun doRepair(context: Context, onProgress: (Float, String) -> Unit): CommandResult {
        val appContext = context.applicationContext
        if (!TermuxRuntimeManager.isRuntimeInstalled) {
            onProgress(0.02f, "Préparation du runtime embarqué…")
            TermuxRuntimeManager.init(appContext) { p, m -> onProgress(p, m) }
            if (!TermuxRuntimeManager.isRuntimeInstalled) {
                return CommandResult(
                    "voltai-fix.sh",
                    "",
                    "Runtime Termux non disponible (bootstrap) : ${TermuxRuntimeManager.lastError ?: "erreur inconnue"}",
                    -1,
                    0L
                )
            }
        }

        val toolsDir = File(appContext.filesDir, "tools")
        val fixScript = File(toolsDir, "voltai-fix.sh")
        if (!fixScript.exists()) {
            return CommandResult(
                "voltai-fix.sh",
                "",
                "Script de réparation absent (extraction des assets requise)",
                -1,
                0L
            )
        }

        val parser = VoltaiProgressParser(
            onProgress = { p, m -> onProgress(p, m) }
        )
        val result = ShellExecutor.executeStreamingFull(
            "sh '${fixScript.absolutePath}' '${toolsDir.absolutePath}'",
            timeoutSeconds = REPAIR_TIMEOUT_SECONDS
        ) { output -> parser.feed(output) }

        val fixedText = if (parser.fixed.isEmpty()) "" else " — Réparé : ${parser.fixed.joinToString("; ")}"
        val status = if (result.exitCode == 0) result.output.trim() else (result.error ?: "").trim()
        return CommandResult(
            command = "voltai-fix.sh",
            output = status + fixedText,
            error = parser.lastError,
            exitCode = result.exitCode,
            duration = result.duration
        )
    }

    private const val REPAIR_TIMEOUT_SECONDS = 3600L

    private val MISSING_MARKERS = listOf(
        "command not found",
        "inaccessible or not found",
        "no such file or directory",
        "not found",
        "not installed"
    )
}