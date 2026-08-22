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

    /**
     * Répertoire privé contenant les libs de proot (libtalloc.so.2,
     * libandroid-shmem.so) copiées depuis les assets.
     *
     * NOTE : le binaire proot lui-même ne peut PAS vivre ici — depuis
     * Android 10 (targetSdk ≥ 29), SELinux interdit execve() sur
     * app_data_file (error=13). Il est donc embarqué en jniLibs sous le
     * nom libproot.so, extrait exécutable par le PM dans nativeLibraryDir.
     */
    val HOST_TOOLS_DIR: File
        get() = File(context().filesDir, "host-tools")

    /** Répertoire des libs natives extraites par le PM (exécutable). */
    private val nativeLibraryDir: String
        get() = context().applicationInfo.nativeLibraryDir

    /** Binaire proot extrait par le PM (jniLibs lib*.so → natif + exécutable). */
    val PROOT_PATH: String
        get() = File(nativeLibraryDir, "libproot.so").absolutePath

    /** Répertoires de recherche des libs proot (talloc copié + shmem natif). */
    val PROOT_LIB_DIR: String
        get() = "$HOST_TOOLS_DIR:$nativeLibraryDir"

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

    /** Initialise le runtime (copie proot/libs depuis les assets ; décompression du bootstrap). Idempotent. */
    fun init(context: Context, onProgress: (Float, String) -> Unit = { _, _ -> }) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        onProgress(0.01f, "Préparation du runtime embarqué…")
        ensureHostTools()
        // Garantit les répertoires temporaires même sur une installation
        // existante (install() ne s'exécute plus une fois le runtime présent) :
        // sans usr/tmp, proot et les outils du guest échouent avec
        // « can't create temporary directory ».
        runCatching {
            File(context.filesDir, "tmp").mkdirs()
            File(PREFIX_DIR, "tmp").mkdirs()
            File(HOME_DIR).mkdirs()
        }
        install(context, onProgress)
    }

    /**
     * Copie libtalloc.so.2 et libandroid-shmem.so depuis assets/tools/
     * vers HOST_TOOLS_DIR (idempotent). Les libs n'ont besoin que de la
     * lecture — seul proot exige un emplacement exécutable, d'où son
     * passage en jniLibs (libproot.so).
     */
    fun ensureHostTools() {
        val dir = HOST_TOOLS_DIR
        dir.mkdirs()
        copyAssetIfChanged("tools/libtalloc.so.2", File(dir, "libtalloc.so.2"))
        copyAssetIfChanged("tools/libandroid-shmem.so", File(dir, "libandroid-shmem.so"))
    }

    private fun copyAssetIfChanged(assetPath: String, target: File) {
        context().assets.open(assetPath).use { input ->
            val expectedSize = input.available().toLong()
            if (target.exists() && target.length() == expectedSize) return
            FileOutputStream(target).use { output -> input.copyTo(output, 1 shl 16) }
        }
        runCatching { Os.chmod(target.absolutePath, 0x1A4) } // 0644 : lecture seule suffit pour une lib
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
        // lien vers le proot copié depuis les assets dans HOST_TOOLS_DIR.
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

        // Répertoire temporaire du guest : requis par proot (TMPDIR virtuel)
        // et par les outils (apktool, apt…). Les zips n'emmagent pas les
        // répertoires vides — il faut le créer explicitement.
        File(staging, "tmp").mkdirs()

        // Extraction transactionnelle : staging → PREFIX final
        if (prefix.exists()) prefix.deleteRecursively()
        if (!staging.renameTo(prefix)) {
            throw RuntimeException("Impossible de déplacer le prefix staging vers $PREFIX_DIR")
        }
        android.util.Log.i(
            "VoltRuntime",
            "Bootstrap extrait: $entryCount entrées, ${symlinks.size} symlinks → $PREFIX_DIR; " +
                "bash=${File(binDir, "bash").exists()} sh=${File(binDir, "sh").exists()} login=${File(binDir, "login").exists()}"
        )
    }

    /**
     * Construit un ProcessBuilder exécutant `command` dans le runtime Termux embarqué.
     * Utilise proot pour remapper le chemin virtuel /data/data/com.termux/files/usr
     * vers le PREFIX réel de l'application.
     */
    fun buildProcessBuilder(command: String, workDir: File?): ProcessBuilder? {
        if (!isRuntimeInstalled) return null
        ensureHostTools()
        val proot = File(nativeLibraryDir, "libproot.so")
        val talloc = File(HOST_TOOLS_DIR, "libtalloc.so.2")
        if (!proot.exists() || !talloc.exists()) {
            android.util.Log.e(
                "VoltRuntime",
                "buildProcessBuilder: libproot.so=${proot.exists()} (${proot.absolutePath}) talloc=${talloc.exists()}"
            )
            return null
        }

        val pb = ProcessBuilder(
            proot.absolutePath,
            "-b", "$PREFIX_DIR:$VIRTUAL_PREFIX",
            "-b", "$HOME_DIR:$VIRTUAL_HOME",
            "-w", workDir?.absolutePath ?: VIRTUAL_HOME,
            "$VIRTUAL_PREFIX/bin/bash",
            "-c", command
        )
        val env = pb.environment()
        env["HOME"] = VIRTUAL_HOME
        env["PREFIX"] = VIRTUAL_PREFIX
        env["PATH"] = "$VIRTUAL_PREFIX/bin:$VIRTUAL_PREFIX/bin/applets:/usr/local/bin:/root/voltai/usr/bin:/usr/bin:/bin"
        // Keep the bootstrap paths virtual, but expose the Ubuntu-installed toolchain.
        // PREFIX_DIR remains the only real host path used for filesystem checks.
        env["JAVA_HOME"] = "/root/voltai/usr/lib/jvm/java-17-openjdk"
        env["PYTHONPATH"] = "/root/voltai/usr/lib/python3.14/site-packages"
        // TMPDIR est vu depuis le guest (chemin virtuel remappé par proot) ;
        // PROOT_TMP_DIR doit pointer vers un répertoire RÉEL côté hôte, sinon
        // proot échoue au démarrage (« can't create temporary directory »).
        val hostTmp = File(context().filesDir, "tmp")
        hostTmp.mkdirs()
        env["PROOT_TMP_DIR"] = hostTmp.absolutePath
        env["TMPDIR"] = "$VIRTUAL_PREFIX/tmp"
        env["TERM"] = "xterm-256color"
        env["LD_LIBRARY_PATH"] = "$HOST_TOOLS_DIR:$nativeLibraryDir"
        return pb
    }

    private fun context(): Context =
        requireNotNull(appContext) { "TermuxRuntimeManager.init(context) doit être appelé avant toute utilisation" }
}
