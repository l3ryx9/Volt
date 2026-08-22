package com.voltai.doai.data.terminal

import android.content.Context
import android.system.Os
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * Gestionnaire du runtime Termux embarqué dans l'APK.
 *
 * Le bootstrap officiel de Termux (bash, coreutils, apt, pkg...) est embarqué
 * dans assets/termux/bootstrap-aarch64.zip et décompressé au premier lancement
 * dans filesDir/usr. Les binaires du bootstrap ont des chemins en dur vers
 * /data/data/com.termux/files/usr (RUNPATH, shebangs, liens symboliques), c'est
 * pourquoi l'exécution passe par `proot` (embarqué) qui remappe ce chemin
 * virtuel vers le PREFIX réel de l'application.
 */
object TermuxRuntimeManager {

    @Volatile
    private var appContext: Context? = null

    val PREFIX_DIR: String
        get() = File(context().filesDir, "usr").absolutePath

    val HOME_DIR: String
        get() = File(context().filesDir, "home").absolutePath

    val VIRTUAL_PREFIX: String = "/data/data/com.termux/files/usr"
    val VIRTUAL_HOME: String = "/data/data/com.termux/files/home"

    /** Binaire proot embarqué (extrait par le système dans nativeLibraryDir à l'install). */
    val PROOT_PATH: String
        get() = File(context().applicationInfo.nativeLibraryDir, "proot").absolutePath

    /** Répertoire contenant libtalloc.so.2 et libandroid-shmem.so pour proot. */
    val PROOT_LIB_DIR: String
        get() = context().applicationInfo.nativeLibraryDir

    val binDir: File
        get() = File(PREFIX_DIR, "bin")

    val isInitialized: Boolean
        get() = appContext != null

    val isRuntimeInstalled: Boolean
        get() = isInitialized &&
            File(binDir, "bash").exists() && File(PREFIX_DIR, "etc/profile").exists()

    /** Motif d'échec de la dernière tentative de décompression (null si OK). */
    @Volatile
    var lastError: String? = null
        private set

    /** Initialise le runtime (proot/libs déjà extraits par le système dans nativeLibraryDir ; décompression du bootstrap). Idempotent. */
    fun init(context: Context, onProgress: (Float, String) -> Unit = { _, _ -> }) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        onProgress(0.01f, "Préparation du runtime embarqué…")
        install(context, onProgress)
    }

    /** Tentative de décompression du bootstrap embarqué. Renvoie false en cas d'échec. */
    fun install(context: Context, onProgress: (Float, String) -> Unit = { _, _ -> }): Boolean {
        if (isRuntimeInstalled) {
            lastError = null
            return true
        }
        onProgress(0.04f, "Décompression de l’archive du runtime…")
        return try {
            doInstall(context, onProgress)
            lastError = null
            true
        } catch (e: Exception) {
            lastError = e.message ?: "Erreur inconnue pendant la décompression du runtime"
            false
        }
    }

    private fun doInstall(context: Context, onProgress: (Float, String) -> Unit) {
        val prefix = File(PREFIX_DIR)
        val staging = File(context.filesDir, "usr-staging")
        staging.mkdirs()

        val assetZip = context.assets.open("termux/bootstrap-aarch64.zip")
        val symlinks = mutableListOf<Pair<String, String>>()
        val buffer = ByteArray(8192)
        var entryCount = 0
        var processedEntries = 0

        ZipInputStream(assetZip).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                entry = zip.nextEntry
            }
        }

        context.assets.open("termux/bootstrap-aarch64.zip").use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                val name = entry.name
                when {
                    name == "SYMLINKS.txt" -> {
                        // Ne pas wrapper dans BufferedReader.use { } : cela fermerait
                        // le ZipInputStream sous-jacent et casserait la suite de la boucle.
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        text.lineSequence().forEach { line ->
                            val parts = line.split("←")
                            if (parts.size == 2) {
                                symlinks.add(parts[0] to parts[1])
                            }
                        }
                    }
                    entry.isDirectory -> {
                        File(staging, name).mkdirs()
                    }
                    else -> {
                        val target = File(staging, name)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            var read: Int
                            while (zip.read(buffer).also { read = it } != -1) {
                                out.write(buffer, 0, read)
                            }
                        }
                        val needsExec = name.startsWith("bin/") ||
                            name.startsWith("libexec") ||
                            name.startsWith("lib/apt/apt-helper") ||
                            name.startsWith("lib/apt/methods")
                        if (needsExec) {
                            runCatching { Os.chmod(target.absolutePath, 0x1ED) } // 0755
                        }
                    }
                }
                processedEntries++
                val progress = 0.04f + 0.18f *
                    (processedEntries.toFloat() / entryCount.coerceAtLeast(1))
                onProgress(progress, "Décompression du runtime…")
                entry = zip.nextEntry
                }
            }
        }

        symlinks.forEach { (old, new) ->
            val newPath = new.removePrefix("./")
            val target = File(staging, newPath)
            target.parentFile?.mkdirs()
            runCatching { Os.symlink(old, target.absolutePath) }
        }

        // proot-distro (installé ensuite via pkg) a besoin du binaire `proot`
        // dans le PATH du runtime. Le bootstrap ne le fournit pas : on crée un
        // lien vers le proot embarqué (nativeLibraryDir, extrait par le système
        // à l'install). LD_LIBRARY_PATH vers nativeLibraryDir est hérité de
        // l'environnement du processus parent.
        runCatching {
            val prootLink = File(staging, "bin/proot")
            prootLink.parentFile?.mkdirs()
            if (!prootLink.exists()) {
                Os.symlink(PROOT_PATH, prootLink.absolutePath)
            }
        }

        val home = File(HOME_DIR)
        home.mkdirs()
        File(home, ".termux").mkdirs()

        // Extraction transactionnelle : staging → PREFIX final
        if (prefix.exists()) prefix.deleteRecursively()
        if (!staging.renameTo(prefix)) {
            throw RuntimeException("Impossible de déplacer le prefix staging vers $PREFIX_DIR")
        }
    }

    /**
     * Construit un ProcessBuilder exécutant `command` dans le runtime Termux embarqué.
     * Utilise proot pour remapper le chemin virtuel /data/data/com.termux/files/usr
     * vers le PREFIX réel de l'application.
     */
    fun buildProcessBuilder(command: String, workDir: File?): ProcessBuilder? {
        if (!isRuntimeInstalled) return null
        val nativeLibDir = File(context().applicationInfo.nativeLibraryDir)
        val proot = File(nativeLibDir, "proot")
        if (!proot.exists() || !File(nativeLibDir, "libtalloc.so.2").exists()) return null

        val pb = ProcessBuilder(
            proot.absolutePath,
            "-b", "$PREFIX_DIR:$VIRTUAL_PREFIX",
            "-w", workDir?.absolutePath ?: VIRTUAL_HOME,
            "$VIRTUAL_PREFIX/bin/login",
            "-c", command
        )
        val env = pb.environment()
        env["HOME"] = VIRTUAL_HOME
        env["PREFIX"] = VIRTUAL_PREFIX
        env["PATH"] = "$VIRTUAL_PREFIX/bin:$VIRTUAL_PREFIX/bin/applets"
        env["TMPDIR"] = "$VIRTUAL_PREFIX/tmp"
        env["TERM"] = "xterm-256color"
        env["LD_LIBRARY_PATH"] = nativeLibDir.absolutePath
        return pb
    }

    private fun context(): Context =
        requireNotNull(appContext) { "TermuxRuntimeManager.init(context) doit être appelé avant toute utilisation" }
}
