package com.voltai.doai.service

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Base64

/**
 * Gère le notebook Colab embarqué dans les assets de l'app
 * (`assets/notebooks/VoltAI.ipynb`) : copie vers le cache pour l'exposer
 * via FileProvider (upload auto dans Colab) et fournit son contenu en
 * base64 pour l'injection directe dans le DOM de Colab.
 */
object NotebookAsset {

    const val ASSET_PATH = "notebooks/VoltAI.ipynb"

    private const val FILE_NAME = "VoltAI.ipynb"

    /** Copie l'asset vers le cache si absent, puis renvoie le fichier. */
    fun ensureCached(context: Context): File {
        val dir = File(context.cacheDir, "notebooks")
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, FILE_NAME)
        if (!out.exists() || out.length() == 0L) {
            runCatching {
                context.assets.open(ASSET_PATH).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        return out
    }

    /** URI FileProvider du notebook embarqué (pour l'upload automatique). */
    fun uri(context: Context): Uri =
        FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            ensureCached(context)
        )

    /** Contenu du notebook en base64 (pour l'injection DataTransfer). */
    fun base64(context: Context): String {
        val bytes = ensureCached(context).readBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }
}