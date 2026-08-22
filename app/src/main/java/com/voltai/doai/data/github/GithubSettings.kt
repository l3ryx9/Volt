package com.voltai.doai.data.github

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configuration de la connexion GitHub (nom d'utilisateur + token).
 * Utilisée pour cloner des dépôts dans le workspace et pour le push
 * depuis l'écran de chat.
 */
class GithubSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _username = MutableStateFlow(currentUsername())
    val username: StateFlow<String> = _username.asStateFlow()

    private val _token = MutableStateFlow(currentToken())
    val token: StateFlow<String> = _token.asStateFlow()

    fun currentUsername(): String = prefs.getString(KEY_USERNAME, "").orEmpty().trim()
    fun currentToken(): String = prefs.getString(KEY_TOKEN, "").orEmpty().trim()

    fun setUsername(value: String) {
        val normalized = value.trim()
        prefs.edit().putString(KEY_USERNAME, normalized).apply()
        _username.value = normalized
    }

    fun setToken(value: String) {
        prefs.edit().putString(KEY_TOKEN, value).apply()
        _token.value = value
    }

    fun isConfigured(): Boolean = currentUsername().isNotBlank() && currentToken().isNotBlank()

    companion object {
        private const val PREFS_NAME = "github_settings"
        private const val KEY_USERNAME = "github_username"
        private const val KEY_TOKEN = "github_token"
    }
}