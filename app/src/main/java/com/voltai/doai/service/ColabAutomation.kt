package com.voltai.doai.service

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Automatisation de Colab dans un WebView, sans GitHub :
 *  - le notebook est embarqué dans l'app (assets/notebooks/VoltAI.ipynb) et
 *    chargé dans Colab via le flux d'upload (stratégies multiples) ;
 *  - `JS_AUTO_RUN` exécute les cellules de démarrage ([1/8] -> [8/8]) via
 *    l'API JavaScript globale de Colab (`colab.global.notebook.*`), puis
 *    lance la cellule keep-alive [9/9] (marqueur `KEEP-ALIVE`) en dernier,
 *    en fire-and-forget (boucle infinie, aucune attente) ;
 *  - l'URL publique du tunnel Cloudflare (`*.trycloudflare.com`) imprimée
 *    par le notebook est capturée.
 *
 * Un fallback DOM lit l'URL dans la page (document + iframes same-origin) :
 * même si l'API interne change ou si l'utilisateur lance les cellules à la
 * main, l'URL est détectée dès qu'elle apparaît.
 */
object ColabAutomation {

    const val COLAB_HOME_URL = "https://colab.research.google.com/"

    private val TUNNEL_RE = Regex("https://[-a-zA-Z0-9]+\\.trycloudflare\\.com")

    private val JS_AUTO_RUN = """
        (function () {
          function setUrl(url) {
            try {
              var el = document.getElementById('voltai-url');
              if (!el) { el = document.createElement('div'); el.id = 'voltai-url'; el.style.display = 'none'; document.body.appendChild(el); }
              el.innerText = url;
            } catch (e) {}
            try { if (window.colabConn) colabConn.onUrl(url); } catch (e) {}
          }
          function scan(text) {
            if (!text) return;
            var m = text.match(/https:\/\/[-a-zA-Z0-9]+\.trycloudflare\.com/);
            if (m && m[0]) setUrl(m[0]);
          }
          function getCodeCells() {
            var cells = [];
            var keepAlive = [];
            try {
              var nb = colab.global.notebook;
              var count = 0;
              try { count = nb.cell.getCellCount ? nb.cell.getCellCount() : 0; } catch (e) {}
              if (!count) { for (var i = 0; i < 20; i++) { try { nb.cell.getCell(i); count = i + 1; } catch (e) { break; } } }
              for (var i = 0; i < count; i++) {
                try {
                  var cell = nb.cell.getCell(i);
                  var code = cell.getCode ? cell.getCode() : '';
                  if (code && code.indexOf('KEEP-ALIVE') !== -1) {
                    keepAlive.push(i);
                  } else if (code && code.trim().charAt(0) !== '#' && code.trim() !== '') {
                    cells.push({ i: i, code: code });
                  } else if (code) {
                    scan(code);
                  }
                } catch (e) {}
              }
            } catch (e) {}
            return { cells: cells, keepAlive: keepAlive };
          }
          function runCells() {
            var result = getCodeCells();
            var cells = result.cells;
            var keepAlive = result.keepAlive;
            var idx = 0;
            function launchKeepAlive() {
              var nb = colab.global.notebook;
              for (var j = 0; j < keepAlive.length; j++) {
                try { nb.cell.Execute(keepAlive[j]); } catch (e) {}
              }
            }
            function next() {
              if (idx >= cells.length) { launchKeepAlive(); return; }
              var cell = cells[idx];
              idx++;
              try {
                var nb = colab.global.notebook;
                if (nb.cell.Execute) {
                  nb.cell.Execute(cell.i);
                  watchOutput(cell.i, next);
                } else if (nb.kernel.execute) {
                  nb.kernel.execute(cell.code, {
                    iopub: { output: function (msg) { scan(JSON.stringify(msg)); } }
                  });
                  setTimeout(next, 2500);
                } else {
                  next();
                }
              } catch (e) { next(); }
            }
            function watchOutput(i, done) {
              var tries = 0;
              var t = setInterval(function () {
                tries++;
                try {
                  var area = colab.global.notebook.cell.getOutputArea ? colab.global.notebook.cell.getOutputArea(i) : null;
                  if (area) scan(area.innerText || area.textContent || '');
                } catch (e) {}
                if (tries > 30) { clearInterval(t); done(); }
              }, 2000);
            }
            next();
          }
          function wait() {
            try {
              if (colab && colab.global && colab.global.notebook) { runCells(); return; }
            } catch (e) {}
            setTimeout(wait, 3000);
          }
          wait();
        })();
    """.trimIndent()

    /**
     * Ouvre le notebook embarqué dans Colab sans GitHub. Multi-stratégies
     * tentées en séquence (Ctrl+O -> Upload, menu File, injection
     * DataTransfer, variante Drive), avec garde-fou : ne s'exécute pas si un
     * notebook est déjà chargé (`colab.global.notebook` présent) ou si le
     * pont a signalé que le picker s'est ouvert (`__voltaiChooserOpened`).
     */
    private val JS_OPEN_NOTEBOOK = """
        (function () {
          var attempts = 0;
          var MAX_ATTEMPTS = 8;

          function textOf(el) {
            return (el.textContent || el.innerText || '').trim();
          }
          function findByTextContains(root, text) {
            var all = (root || document).querySelectorAll('*');
            for (var i = 0; i < all.length; i++) {
              var el = all[i];
              if (el.children.length === 0 && textOf(el).indexOf(text) !== -1) return el;
            }
            return null;
          }
          function findFileInput() {
            var inputs = document.querySelectorAll('input[type="file"]');
            return inputs.length ? inputs[inputs.length - 1] : null;
          }
          function pressCtrlO() {
            try {
              document.dispatchEvent(new KeyboardEvent('keydown', { key: 'o', code: 'KeyO', ctrlKey: true, bubbles: true, cancelable: true }));
              document.dispatchEvent(new KeyboardEvent('keyup', { key: 'o', code: 'KeyO', ctrlKey: true, bubbles: true, cancelable: true }));
            } catch (e) {}
          }
          function clickUploadTab() {
            var el = findByTextContains(document, 'Upload');
            if (el) { try { el.click(); } catch (e) {} }
          }
          function injectFile(input) {
            try {
              var b64 = colabConn.getNotebookBase64();
              if (!b64) return false;
              var bin = atob(b64);
              var bytes = new Uint8Array(bin.length);
              for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
              var file = new File([bytes], 'VoltAI.ipynb', { type: 'application/json' });
              var dt = new DataTransfer();
              dt.items.add(file);
              input.files = dt.files;
              input.dispatchEvent(new Event('change', { bubbles: true }));
              return true;
            } catch (e) { return false; }
          }
          function waitFor(fn, timeoutMs, stepMs, done) {
            var waited = 0;
            var t = setInterval(function () {
              var v = null;
              try { v = fn(); } catch (e) {}
              if (v) { clearInterval(t); done(v); return; }
              waited += stepMs;
              if (waited >= timeoutMs) { clearInterval(t); done(null); }
            }, stepMs);
          }
          function strategyInputClick() {
            pressCtrlO();
            waitFor(function () { clickUploadTab(); return findFileInput(); }, 6000, 400, function (input) {
              if (input) { try { input.click(); } catch (e) {} }
            });
          }
          function strategyMenu() {
            var fileBtn = findByTextContains(document, 'File');
            if (fileBtn) { try { fileBtn.click(); } catch (e) {} }
            waitFor(function () { return findByTextContains(document, 'Upload notebook'); }, 4000, 300, function (item) {
              if (item) { try { item.click(); } catch (e) {} }
            });
          }
          function strategyDataTransfer() {
            pressCtrlO();
            waitFor(function () { clickUploadTab(); return findFileInput(); }, 6000, 400, function (input) {
              if (input) injectFile(input);
            });
          }
          function strategyDrive() {
            pressCtrlO();
            waitFor(function () {
              var el = findByTextContains(document, 'Google Drive');
              if (el) { try { el.click(); } catch (e) {} }
              return el;
            }, 5000, 400, function () {});
          }
          function notebookReady() {
            try { return !!(colab && colab.global && colab.global.notebook); } catch (e) { return false; }
          }
          function done() {
            return notebookReady() || window.__voltaiChooserOpened === true;
          }
          function tryNext() {
            attempts++;
            if (done() || attempts > MAX_ATTEMPTS) return;
            if (attempts === 1) strategyInputClick();
            else if (attempts === 2) strategyMenu();
            else if (attempts === 3) strategyDataTransfer();
            else if (attempts === 4) strategyInputClick();
            else if (attempts === 5) strategyDrive();
            else strategyInputClick();
            setTimeout(tryNext, 9000);
          }
          function boot() {
            try {
              if (window.document.querySelector('colab-app') || window.colab) { tryNext(); return; }
            } catch (e) {}
            setTimeout(boot, 2000);
          }
          setTimeout(boot, 3000);
        })();
    """.trimIndent()

    private val JS_READ_URL = """
        (function () {
          try {
            var el = document.getElementById('voltai-url');
            if (el && el.innerText) return el.innerText;
          } catch (e) {}
          var text = '';
          try { text += document.body.innerText || ''; } catch (e) {}
          try {
            var frames = document.querySelectorAll('iframe');
            for (var i = 0; i < frames.length; i++) {
              try { var d = frames[i].contentDocument; if (d) text += d.body.innerText || ''; } catch (e) {}
            }
          } catch (e) {}
          var m = text.match(/https:\/\/[-a-zA-Z0-9]+\.trycloudflare\.com/);
          return m ? m[0] : null;
        })();
    """.trimIndent()

    /** Injecte le script d'ouverture du notebook embarqué (multi-stratégies). */
    fun injectOpenNotebook(webView: WebView) {
        webView.post {
            runCatching { webView.evaluateJavascript(JS_OPEN_NOTEBOOK, null) }
        }
    }

    /** Injecte le script d'auto-exécution dans la page chargée. */
    fun injectAutoRun(webView: WebView) {
        webView.post {
            runCatching { webView.evaluateJavascript(JS_AUTO_RUN, null) }
        }
    }

    /**
     * Interroge la page WebView et renvoie l'URL du tunnel si elle est
     * visible (null sinon). Suspendue : le WebView doit répondre.
     */
    suspend fun pollTunnelUrl(webView: WebView): String? =
        suspendCancellableCoroutine { continuation ->
            webView.post {
                try {
                    webView.evaluateJavascript(JS_READ_URL) { value ->
                        if (continuation.isActive) continuation.resume(parse(value))
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }

    private fun parse(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed == "null") return null
        return TUNNEL_RE.find(trimmed)?.value
    }
}

/**
 * Pont JS -> Kotlin : le script injecté appelle `colabConn.onUrl(url)`
 * dès qu'il détecte l'URL du tunnel, `colabConn.onChooserOpened()` quand le
 * picker natif s'ouvre, et `colabConn.getNotebookBase64()` pour récupérer
 * le notebook embarqué (injection DataTransfer).
 */
class ColabBridge(
    private val context: Context,
    private val onUrl: (String) -> Unit,
    private val onChooserOpened: () -> Unit = {}
) {
    @JavascriptInterface
    fun onUrl(url: String?) {
        if (!url.isNullOrBlank()) onUrl(url)
    }

    @JavascriptInterface
    fun onChooserOpened() {
        onChooserOpened()
    }

    @JavascriptInterface
    fun getNotebookBase64(): String =
        runCatching { NotebookAsset.base64(context) }.getOrDefault("")
}