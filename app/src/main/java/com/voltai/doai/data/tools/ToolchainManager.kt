package com.voltai.doai.data.tools

import com.voltai.doai.data.terminal.TermuxRuntimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ToolchainPhase { IDLE, RUNNING, DONE, FAILED }

data class ToolchainStatus(
    val phase: ToolchainPhase = ToolchainPhase.IDLE,
    val message: String? = null,
    val progress: Float? = null,
    /** Identifiant unique d'une exécution (permet de fermer la fenêtre de progression sans la réafficher pour cette exécution). */
    val runId: Long = 0L
)

/**
 * Gère l'installation des outils intégrés dans l'APK (apktool, jadx,
 * smali/baksmali, ripgrep, 7-Zip, androguard).
 *
 * L'installation est idempotente : un marqueur de version empêche de
 * re-déployer les outils à chaque démarrage.
 */
object ToolchainManager {

    private val _status = MutableStateFlow(ToolchainStatus())
    val status: StateFlow<ToolchainStatus> = _status.asStateFlow()

    private val mutex = Mutex()

    suspend fun ensureTools(context: android.content.Context): ToolchainStatus {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                if (_status.value.phase == ToolchainPhase.DONE ||
                    _status.value.phase == ToolchainPhase.RUNNING
                ) {
                    return@withLock _status.value
                }

                val runId = System.currentTimeMillis()
                _status.value = ToolchainStatus(
                    ToolchainPhase.RUNNING,
                    "Préparation du runtime embarqué…",
                    0f,
                    runId
                )

                try {
                    // Le runtime doit être prêt avant toute commande d'installation.
                    TermuxRuntimeManager.init(context.applicationContext) { progress, message ->
                        _status.value = ToolchainStatus(
                            ToolchainPhase.RUNNING,
                            message,
                            progress,
                            runId
                        )
                    }
                    if (!TermuxRuntimeManager.isRuntimeInstalled) {
                        return@withLock ToolchainStatus(
                            ToolchainPhase.FAILED,
                            "Runtime embarqué non décompressé. Relancez l'application pour réessayer."
                        ).also { _status.value = it }
                    }

                    val installer = ToolchainInstaller(context.applicationContext)
                    val installed = if (installer.isInstalled()) {
                        true
                    } else {
                        installer.install { progress, message ->
                            _status.value = ToolchainStatus(
                                ToolchainPhase.RUNNING,
                                message,
                                progress,
                                runId
                            )
                        }
                    }
                    ToolchainStatus(
                        if (installed) ToolchainPhase.DONE else ToolchainPhase.FAILED,
                        if (installed) "Dépendances prêtes" else (installer.lastError ?: "Échec de l'installation des dépendances"),
                        if (installed) 1f else _status.value.progress
                    ).also { _status.value = it }
                } catch (e: Exception) {
                    ToolchainStatus(
                        ToolchainPhase.FAILED,
                        e.message ?: "Erreur d'installation des dépendances",
                        _status.value.progress
                    ).also { _status.value = it }
                }
            }
        }
    }

    fun isInstalled(context: android.content.Context): Boolean =
        ToolchainInstaller(context.applicationContext).isInstalled()

    internal suspend fun run(install: suspend () -> Boolean): ToolchainStatus =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (_status.value.phase == ToolchainPhase.DONE || _status.value.phase == ToolchainPhase.RUNNING) {
                    return@withLock _status.value
                }
                _status.value = ToolchainStatus(ToolchainPhase.RUNNING, "Installation des outils intégrés…", 0f)
                _status.value = try {
                    if (install()) {
                        ToolchainStatus(ToolchainPhase.DONE, "Outils intégrés installés dans Ubuntu", 1f)
                    } else {
                        ToolchainStatus(ToolchainPhase.FAILED, "Échec de l'installation des outils intégrés")
                    }
                } catch (e: Exception) {
                    ToolchainStatus(ToolchainPhase.FAILED, e.message ?: "Erreur d'installation des outils")
                }
                _status.value
            }
        }

    internal fun resetForTest() {
        _status.value = ToolchainStatus()
    }
}
