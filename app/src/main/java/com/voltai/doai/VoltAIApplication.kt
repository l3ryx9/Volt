package com.voltai.doai

import android.app.Application
import android.os.Process
import android.util.Log
import com.itsaky.androidide.treesitter.TreeSitter
import com.voltai.doai.data.qwen.PersistentCookieStore
import com.voltai.doai.data.terminal.TermuxRuntimeManager
import com.voltai.doai.data.tools.ToolchainManager
import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.service.VoltAIKeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class VoltAIApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        ServiceLocator.init(this)

        // Cookies persistants : la session (Google/Colab, tunnel Cloudflare…)
        // est conservée entre deux lancements -> l'app est déjà connectée.
        PersistentCookieStore.install(this)

        // Persistance : service de premier plan pour rester actif en
        // tâche de fond (agent Qwen + orchestrateur + Ubuntu proot).
        VoltAIKeepAliveService.start(this)
        try {
            // Charge les bibliothèques natives Tree-sitter (si disponibles)
            TreeSitter.loadLibrary()
        } catch (_: Throwable) {
            // Natif indisponible (tests JVM, ABI non embarqué...) :
            // le parseur AST intégré prend le relais automatiquement.
        }

        // Qwen distant (llama-server Colab) : vérifie la disponibilité du
        // serveur au démarrage (aucun modèle GGUF sur l'appareil).
        applicationScope.launch {
            ServiceLocator.modelManager.ensureModel()
        }

        applicationScope.launch {
            ToolchainManager.ensureTools(this@VoltAIApplication)
        }
    }

    /**
     * Phase 13 — capture les exceptions non gérées dans filesDir/crash.log
     * pour permettre un diagnostic sans logcat (l'appareil n'a pas adb).
     */
    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val log = buildString {
                    appendLine("=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} ===")
                    appendLine("Thread: ${thread.name} (id=${thread.id})")
                    appendLine(sw.toString())
                }
                File(filesDir, "crash.log").appendText(log + "\n")
                Log.e("VoltAI", "Uncaught exception on ${thread.name}", throwable)
            } catch (_: Throwable) {
            } finally {
                Process.killProcess(Process.myPid())
            }
        }
    }
}
