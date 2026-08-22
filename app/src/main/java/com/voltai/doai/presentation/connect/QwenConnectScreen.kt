package com.voltai.doai.presentation.connect

import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltai.doai.presentation.VoltColors
import com.voltai.doai.service.ColabAutomation
import com.voltai.doai.service.ColabBridge
import com.voltai.doai.service.NotebookAsset

/**
 * Fenêtre de connexion au serveur Qwen : WebView plein écran qui ouvre le
 * notebook Colab. L'utilisateur se connecte à son compte Google (une seule
 * fois, la session est conservée par cookies), le script injecté lance les
 * cellules et capture l'URL du tunnel automatiquement.
 */
@Composable
fun QwenConnectScreen(onBack: () -> Unit) {
    val viewModel: QwenConnectViewModel = viewModel()
    val status by viewModel.status.collectAsState()
    val foundUrl by viewModel.foundUrl.collectAsState()
    val showManualUpload by viewModel.showManualUpload.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.onWebViewDetached() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = VoltColors.Text
                )
            }
            Text(
                text = "Qwen Connect",
                color = VoltColors.Text,
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Text(
            text = status,
            color = if (foundUrl != null) VoltColors.AccentBright else VoltColors.Accent,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (showManualUpload) {
            Button(
                onClick = { viewModel.onManualUpload() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Charger le notebook (Colab)")
            }
        }

        AndroidView(
            factory = { context ->
                val container = FrameLayout(context)
                val webView = WebView(context)
                webView.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                configureWebView(webView, container, viewModel)
                container.addView(webView)
                container
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        )
    }
}

private fun configureWebView(
    webView: WebView,
    container: FrameLayout,
    viewModel: QwenConnectViewModel
) {
    val settings: WebSettings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.userAgentString = settings.userAgentString + " VoltAI/1.0"

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

    val bridge = ColabBridge(
        context = webView.context.applicationContext,
        onUrl = { url -> viewModel.onUrlFound(url) },
        onChooserOpened = {
            webView.post {
                runCatching {
                    webView.evaluateJavascript("window.__voltaiChooserOpened = true;", null)
                }
            }
        }
    )
    webView.addJavascriptInterface(bridge, "colabConn")

    webView.webChromeClient = object : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val source = view ?: webView
            val child = WebView(source.context)
            child.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            child.settings.javaScriptEnabled = true
            child.settings.domStorageEnabled = true
            child.settings.setSupportMultipleWindows(true)
            child.settings.javaScriptCanOpenWindowsAutomatically = true
            child.webChromeClient = this
            container.addView(child)
            (resultMsg?.obj as? WebView.WebViewTransport)?.webView = child
            resultMsg?.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            container.removeView(window)
            window?.destroy()
        }

        override fun onShowFileChooser(
            view: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean {
            val accept = (fileChooserParams.acceptTypes ?: emptyArray()).joinToString(",")
            val isNotebook = accept.isBlank() ||
                accept.contains("ipynb", ignoreCase = true) ||
                accept.contains("json", ignoreCase = true) ||
                accept.contains("octet-stream", ignoreCase = true) ||
                accept.contains("*/*", ignoreCase = true)
            if (!isNotebook) return false
            val uri = NotebookAsset.uri(view?.context ?: webView.context)
            bridge.onChooserOpened()
            filePathCallback.onReceiveValue(arrayOf(uri))
            return true
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            viewModel.onPageLoaded(view ?: webView)
        }
    }

    webView.loadUrl(ColabAutomation.COLAB_HOME_URL)
}