package com.voltai.doai.presentation.settings

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltai.doai.data.qwen.PersistentCookieStore
import com.voltai.doai.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran Paramètres : configuration de l'URL du tunnel Colab (llama-server
 * Qwen distant), test de connexion via /v1/models, et connexion GitHub
 * (username + token) pour cloner des dépôts dans le workspace.
 */
class SettingsViewModel : ViewModel() {

    private val qwenSettings = ServiceLocator.qwenSettings
    private val qwenClient = ServiceLocator.qwenClient
    private val githubSettings = ServiceLocator.githubSettings
    private val githubManager = ServiceLocator.githubManager

    val url: StateFlow<String> = qwenSettings.url

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    val githubUsername: StateFlow<String> = githubSettings.username
    val githubToken: StateFlow<String> = githubSettings.token

    private val _testingGithub = MutableStateFlow(false)
    val testingGithub: StateFlow<Boolean> = _testingGithub.asStateFlow()

    private val _githubTestResult = MutableStateFlow<String?>(null)
    val githubTestResult: StateFlow<String?> = _githubTestResult.asStateFlow()

    private val _cloning = MutableStateFlow(false)
    val cloning: StateFlow<Boolean> = _cloning.asStateFlow()

    private val _cloneResult = MutableStateFlow<String?>(null)
    val cloneResult: StateFlow<String?> = _cloneResult.asStateFlow()

    private val _repoUrl = MutableStateFlow("")
    val repoUrl: StateFlow<String> = _repoUrl.asStateFlow()

    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> = _loggingOut.asStateFlow()

    private val _logoutResult = MutableStateFlow<String?>(null)
    val logoutResult: StateFlow<String?> = _logoutResult.asStateFlow()

    fun setUrl(value: String) {
        qwenSettings.setUrl(value)
    }

    fun setGithubUsername(value: String) {
        githubSettings.setUsername(value)
    }

    fun setGithubToken(value: String) {
        githubSettings.setToken(value)
    }

    fun setRepoUrl(value: String) {
        _repoUrl.value = value
    }

    /** Déconnexion Google/Colab : efface les cookies de session persistés. */
    fun logout() {
        if (_loggingOut.value) return
        _loggingOut.value = true
        _logoutResult.value = null
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    PersistentCookieStore.clear(ServiceLocator.appContext())
                    CookieManager.getInstance().removeAllCookies(null)
                }.isSuccess
            }
            _logoutResult.value = if (ok) {
                "✓ Session déconnectée (Google/Colab). Il faudra vous reconnecter la prochaine fois."
            } else {
                "✗ Échec de la déconnexion."
            }
            _loggingOut.value = false
        }
    }

    fun testConnection() {
        val urlValue = qwenSettings.currentUrl()
        if (urlValue.isBlank()) {
            _testResult.value = "✗ Aucune URL configurée."
            return
        }
        _testing.value = true
        _testResult.value = null
        viewModelScope.launch {
            val ok = runCatching { qwenClient.checkModel() }.getOrDefault(false)
            _testResult.value = if (ok) {
                "✓ Qwen disponible sur $urlValue"
            } else {
                "✗ Serveur injoignable ou modèle Qwen absent."
            }
            _testing.value = false
        }
    }

    fun testGithubConnection() {
        val username = githubSettings.currentUsername()
        val token = githubSettings.currentToken()
        if (username.isBlank() || token.isBlank()) {
            _githubTestResult.value = "✗ Renseignez le nom d'utilisateur et le token."
            return
        }
        _testingGithub.value = true
        _githubTestResult.value = null
        viewModelScope.launch {
            val ok = runCatching { githubManager.testConnection() }.getOrDefault(false)
            _githubTestResult.value = if (ok) {
                "✓ Connexion GitHub validée ($username)"
            } else {
                "✗ Token invalide ou réseau inaccessible."
            }
            _testingGithub.value = false
        }
    }

    fun cloneRepo() {
        val urlValue = _repoUrl.value.trim()
        if (urlValue.isBlank()) {
            _cloneResult.value = "✗ Entrez une URL GitHub."
            return
        }
        if (!githubSettings.isConfigured()) {
            _cloneResult.value = "✗ Configurez d'abord username + token GitHub."
            return
        }
        _cloning.value = true
        _cloneResult.value = null
        viewModelScope.launch {
            val result = runCatching { githubManager.cloneRepo(urlValue) }.getOrNull()
            _cloneResult.value = if (result?.exitCode == 0) {
                "✓ Dépôt cloné dans le workspace (repos)."
            } else {
                "✗ Échec du clonage : ${result?.error ?: result?.output ?: "erreur inconnue"}"
            }
            _cloning.value = false
        }
    }
}