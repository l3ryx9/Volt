package com.voltai.doai.data.qwen

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuration de la connexion au serveur Qwen distant (llama-server sur
 * Google Colab). L'URL du tunnel change à chaque session Colab : elle est
 * stockée dans les préférences et modifiable à tout moment depuis l'écran
 * Paramètres.
 */
class QwenSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _url = MutableStateFlow(currentUrl())
    val url: StateFlow<String> = _url.asStateFlow()

    /** URL publique actuelle du tunnel Colab (sans slash final). */
    fun currentUrl(): String = prefs.getString(KEY_URL, "").orEmpty().trim().trimEnd('/')

    /** Enregistre une nouvelle URL de tunnel. */
    fun setUrl(value: String) {
        val normalized = value.trim().trimEnd('/')
        prefs.edit().putString(KEY_URL, normalized).apply()
        _url.value = normalized
    }

    /** Vrai si une URL est configurée. */
    fun hasUrl(): Boolean = currentUrl().isNotBlank()

    companion object {
        private const val PREFS_NAME = "qwen_settings"
        private const val KEY_URL = "colab_tunnel_url"
    }
}