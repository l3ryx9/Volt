package com.voltai.doai.presentation.connect

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.service.ColabAutomation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Connexion automatique au serveur Qwen : charge le notebook Colab dans un
 * WebView (login Google manuel une fois, session mémorisée par cookies),
 * automatise le lancement des cellules et capture l'URL publique du tunnel
 * Cloudflare pour la sauvegarder dans QwenSettings.
 */
class QwenConnectViewModel : ViewModel() {

    private val qwenSettings = ServiceLocator.qwenSettings
    private val qwenClient = ServiceLocator.qwenClient

    private val _status = MutableStateFlow("Préparation…")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _foundUrl = MutableStateFlow<String?>(null)
    val foundUrl: StateFlow<String?> = _foundUrl.asStateFlow()

    private val _showManualUpload = MutableStateFlow(false)
    val showManualUpload: StateFlow<Boolean> = _showManualUpload.asStateFlow()

    @Volatile
    private var webView: WebView? = null

    private var pollJob: Job? = null

    /** Appelé quand la page (Colab) a fini de se charger. */
    fun onPageLoaded(webView: WebView) {
        this.webView = webView
        ColabAutomation.injectOpenNotebook(webView)
        ColabAutomation.injectAutoRun(webView)
        _status.value = "Colab chargé — ouverture du notebook et exécution des cellules…"
        startPolling()
    }

    /** Appelé quand l'écran est fermé. */
    fun onWebViewDetached() {
        webView = null
        pollJob?.cancel()
        pollJob = null
    }

    /** Relance le flux d'ouverture du notebook embarqué (secours manuel). */
    fun onManualUpload() {
        val wv = webView ?: return
        _status.value = "Relance de l'ouverture du notebook embarqué…"
        ColabAutomation.injectOpenNotebook(wv)
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                val wv = webView ?: break
                val url = withTimeoutOrNull(5_000) {
                    ColabAutomation.pollTunnelUrl(wv)
                }
                if (!url.isNullOrBlank()) {
                    onUrlFound(url)
                    break
                }
                if (System.currentTimeMillis() - start > 60_000 && !_showManualUpload.value) {
                    _showManualUpload.value = true
                    _status.value =
                        "Automatisation bloquée ? Touchez « Charger le notebook » ou « File → Upload notebook » dans Colab."
                }
                delay(2_000)
            }
        }
    }

    /** Sauvegarde l'URL du tunnel trouvée et vérifie la connexion. */
    fun onUrlFound(url: String) {
        qwenSettings.setUrl(url)
        _status.value = "URL trouvée — vérification de la connexion…"
        viewModelScope.launch {
            val ok = runCatching { qwenClient.checkModel() }.getOrDefault(false)
            _foundUrl.value = url
            _status.value = if (ok) {
                "Connecté ✓ — $url"
            } else {
                "URL enregistrée, serveur en cours de démarrage… $url"
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}