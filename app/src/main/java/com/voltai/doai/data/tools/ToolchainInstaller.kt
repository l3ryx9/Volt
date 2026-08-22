package com.voltai.doai.data.tools

import android.content.Context
import com.voltai.doai.data.terminal.ShellExecutor
import java.io.File
import java.io.FileOutputStream

/**
 * Déploie les outils intégrés dans les assets de l'APK vers le rootfs
 * Ubuntu proot, là où l'application exécute déjà ses commandes.
 *
 * Driver du script `assets/scripts/voltai-setup.sh` (exécuté dans le runtime
 * Termux embarqué) qui orchestre : proot-distro → Ubuntu 24.04 → extraction
 * du bundle dans /root/voltai → wrappers (java/python) → wheels → apt.
 *
 * L'installation est idempotente : un marqueur de version empêche de
 * re-déployer les outils à chaque démarrage.
 */
class ToolchainInstaller(
    private val context: Context,
    private val version: String = "2.1.0"
) {

    private val toolsDir: File = File(context.filesDir, "tools")
    private val markerFile: File = File(toolsDir, ".installed-$version")
    private val setupScript: File = File(toolsDir, "voltai-setup.sh")

    @Volatile
    var lastError: String? = null

    fun isInstalled(): Boolean = markerFile.exists()

    suspend fun install(onProgress: (Float, String) -> Unit = { _, _ -> }): Boolean {
        if (isInstalled()) return true

        android.util.Log.i(TAG, "install(): démarrage (marker absent)")
        onProgress(0.05f, "Extraction des assets intégrés…")
        if (!extractAssets()) {
            lastError = "Extraction des assets échouée"
            android.util.Log.e(TAG, lastError!!)
            return false
        }

        onProgress(0.10f, "Lancement de l'installation automatisée…")
        val parser = VoltaiProgressParser(
            onProgress = { p, m -> onProgress(p, m) }
        )
        val cmd = "sh '${setupScript.absolutePath}' '${toolsDir.absolutePath}'"
        val result = ShellExecutor.executeStreamingFull(
            cmd,
            timeoutSeconds = SETUP_TIMEOUT_SECONDS
        ) { output -> parser.feed(output) }
        android.util.Log.i(
            TAG,
            "setup terminé: exit=${result.exitCode} duration=${result.duration}ms " +
                "output=${result.output.take(400)} error=${result.error?.take(400)} " +
                "parserError=${parser.lastError} done=${parser.done}"
        )

        if (result.exitCode != 0 || parser.lastError != null || !parser.done) {
            lastError = parser.lastError
                ?: (result.error?.takeIf { it.isNotBlank() } ?: "Échec de l'installation (code ${result.exitCode})")
            onProgress(0.02f, lastError.orEmpty())
            return false
        }

        onProgress(0.97f, "Finalisation…")
        markerFile.parentFile?.mkdirs()
        val written = markerFile.createNewFile() || markerFile.exists()
        if (written) onProgress(1f, "Installation terminée")
        return written
    }

    private fun extractAssets(): Boolean = runCatching {
        toolsDir.mkdirs()
        extractAsset("tools/7zz", File(toolsDir, "7zz"))
        extractAsset(TOOLS_ARCHIVE_ASSET, File(toolsDir, TOOLS_ARCHIVE_ASSET.substringAfterLast('/')))
        extractAsset("scripts/voltai-setup.sh", File(toolsDir, "voltai-setup.sh"))
        extractAsset("scripts/voltai-fix.sh", File(toolsDir, "voltai-fix.sh"))
        File(toolsDir, "7zz").setExecutable(true)
        File(toolsDir, "voltai-setup.sh").setExecutable(true)
        File(toolsDir, "voltai-fix.sh").setExecutable(true)
    }.isSuccess

    private fun extractAsset(asset: String, target: File) {
        context.assets.open(asset).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, bufferSize = 1 shl 16)
            }
        }
    }

    companion object {

        private const val TAG = "VoltTools"
        /**
         * Chemin de l'archive fournie par le projet.
         *
         * Structure attendue :
         *   usr/share/voltai/tools-install.sh
         *   usr/share/voltai/deps-install.sh
         *   [outils et wrappers optionnels sous usr/]
         */
        const val TOOLS_ARCHIVE_ASSET = "tools/voltai-tools.7z"

        private const val SETUP_TIMEOUT_SECONDS = 7200L
    }
}