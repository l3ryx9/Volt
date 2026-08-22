package com.voltai.doai.data.terminal

import com.voltai.doai.domain.models.CommandResult
import java.io.File
import java.util.concurrent.TimeUnit

object ShellExecutor {

    /** Chemin virtuel Termux utilisé pour la construction des commandes proot. */
    val TERMUX_PREFIX: String = "/data/data/com.termux/files/usr"
    val TERMUX_HOME: String = "/data/data/com.termux/files/home"

    /**
     * Prefix réel du runtime : `filesDir/usr` si le runtime embarqué est actif,
     * sinon le chemin du vrai Termux (fallback). Tous les checks de fichiers
     * doivent utiliser le prefix RÉEL (le chemin virtuel n'existe pas sur l'hôte).
     */
    val activePrefix: String
        get() = if (TermuxRuntimeManager.isRuntimeInstalled) TermuxRuntimeManager.PREFIX_DIR else TERMUX_PREFIX

    val activeHome: String
        get() = if (TermuxRuntimeManager.isRuntimeInstalled) TermuxRuntimeManager.HOME_DIR else TERMUX_HOME

    val TERMUX_BIN: File
        get() = File("$activePrefix/bin")

    val prootDistroBin: File
        get() = File("$activePrefix/bin/proot-distro")

    val ubuntuRootfs: File
        get() = File("$activePrefix/var/lib/proot-distro/installed-rootfs/ubuntu")

    val isTermuxRuntimeAvailable: Boolean
        get() = TERMUX_BIN.exists() || TermuxRuntimeManager.isRuntimeInstalled

    val isRootAvailable: Boolean
        get() = listOf(
            File("/system/bin/su"),
            File("/system/xbin/su"),
            File("/sbin/su")
        ).any { it.exists() } || runCatching {
            val p = ProcessBuilder("which", "su").start()
            p.waitFor(2, TimeUnit.SECONDS)
            val out = p.inputStream.bufferedReader().readText().trim()
            p.destroy()
            out.isNotBlank()
        }.getOrDefault(false)

    val isProotDistroInstalled: Boolean
        get() = prootDistroBin.exists()

    val isUbuntuInstalled: Boolean
        get() = ubuntuRootfs.exists() && ubuntuRootfs.listFiles()?.isNotEmpty() == true

    fun execute(command: String, timeoutSeconds: Long = 120L, workDir: File? = null): CommandResult {
        val start = System.currentTimeMillis()
        var output = ""
        var error = ""
        try {
            val (resultCommand, pb) = buildProcess(command, workDir)
            val process = pb.start()

            val outputReader = Thread {
                try {
                    output = process.inputStream.bufferedReader().readText()
                } catch (_: Exception) {
                }
            }
            val errorReader = Thread {
                try {
                    error = process.errorStream.bufferedReader().readText()
                } catch (_: Exception) {
                }
            }
            outputReader.start()
            errorReader.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                outputReader.join(2000)
                errorReader.join(2000)
                return CommandResult(
                    command = resultCommand,
                    output = output.trim(),
                    error = error.trim().ifBlank { "Timeout after ${timeoutSeconds}s" },
                    exitCode = -1,
                    duration = System.currentTimeMillis() - start
                )
            }

            outputReader.join(2000)
            errorReader.join(2000)
            val duration = System.currentTimeMillis() - start
            return CommandResult(
                command = resultCommand,
                output = output.trim(),
                error = error.trim().ifBlank { null },
                exitCode = process.exitValue(),
                duration = duration
            )
        } catch (e: Exception) {
            return CommandResult(
                command = command,
                output = "",
                error = e.message,
                exitCode = -1,
                duration = System.currentTimeMillis() - start
            )
        }
    }

    fun executeUbuntu(command: String, timeoutSeconds: Long = 600L, binds: List<String> = emptyList()): CommandResult {
        if (!isUbuntuInstalled) {
            return CommandResult(
                command = "proot-distro login ubuntu -- $command",
                output = "",
                error = "Ubuntu 24.04 n'est pas installé. Lancez d'abord l'installation via l'environnement.",
                exitCode = -1,
                duration = 0L
            )
        }
        val bindArgs = binds.joinToString(" ") { "--bind $it" }
        val loginCommand = "proot-distro login ubuntu" + if (bindArgs.isBlank()) "" else " $bindArgs"
        return execute("$loginCommand -- /bin/sh -lc ${shellQuote(command)}", timeoutSeconds)
    }

    /**
     * Exécute une commande dans Ubuntu proot en transmettant chaque chunk de
     * stdout à [onChunk] (utilisé pour la progression de la décompression).
     */
    fun executeUbuntuStreaming(
        command: String,
        timeoutSeconds: Long = 180L,
        binds: List<String> = emptyList(),
        onChunk: (String) -> Unit
    ): CommandResult {
        if (!isUbuntuInstalled) {
            return CommandResult(
                command = "proot-distro login ubuntu -- $command",
                output = "",
                error = "Ubuntu 24.04 n'est pas installé.",
                exitCode = -1,
                duration = 0L
            )
        }
        val bindArgs = binds.joinToString(" ") { "--bind $it" }
        return executeStreaming(
            "proot-distro login ubuntu" + if (bindArgs.isBlank()) " --" else " $bindArgs --" + " /bin/sh -lc ${shellQuote(command)}",
            timeoutSeconds,
            onChunk
        )
    }

    /**
     * Exécute une commande en lisant stdout en continu (buffer 4 Ko) et
     * transmet chaque portion à [onChunk]. Version dupliquée de [execute]
     * avec streaming, afin de ne pas casser les appelants existants.
     */
    fun executeStreaming(
        command: String,
        timeoutSeconds: Long = 120L,
        onChunk: (String) -> Unit = {}
    ): CommandResult {
        val start = System.currentTimeMillis()
        var output = ""
        var error = ""
        try {
            val (resultCommand, pb) = buildProcess(command, null)
            val process = pb.start()

            val outputReader = Thread {
                try {
                    val buffer = CharArray(4096)
                    val reader = process.inputStream.bufferedReader()
                    val tail = StringBuilder()
                    while (true) {
                        val read = reader.read(buffer, 0, buffer.size)
                        if (read == -1) break
                        val chunk = String(buffer, 0, read)
                        output += chunk
                        tail.append(chunk)
                        if (tail.length > 512) tail.delete(0, tail.length - 512)
                        onChunk(tail.toString())
                    }
                } catch (_: Exception) {
                }
            }
            val errorReader = Thread {
                try {
                    error = process.errorStream.bufferedReader().readText()
                } catch (_: Exception) {
                }
            }
            outputReader.start()
            errorReader.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                outputReader.join(2000)
                errorReader.join(2000)
                return CommandResult(
                    command = resultCommand,
                    output = output.trim(),
                    error = error.trim().ifBlank { "Timeout after ${timeoutSeconds}s" },
                    exitCode = -1,
                    duration = System.currentTimeMillis() - start
                )
            }

            outputReader.join(2000)
            errorReader.join(2000)
            val duration = System.currentTimeMillis() - start
            return CommandResult(
                command = resultCommand,
                output = output.trim(),
                error = error.trim().ifBlank { null },
                exitCode = process.exitValue(),
                duration = duration
            )
        } catch (e: Exception) {
            return CommandResult(
                command = command,
                output = "",
                error = e.message,
                exitCode = -1,
                duration = System.currentTimeMillis() - start
            )
        }
    }

    /**
     * Exécute une commande en transmettant la sortie CUMULATIVE à [onOutput]
     * à chaque lecture. Contrairement à [executeStreaming] (tampon roulant de
     * 512 caractères), la sortie complète est disponible pour un parsing fiable
     * des lignes [VOLTAI|...] des scripts d'installation.
     */
    fun executeStreamingFull(
        command: String,
        timeoutSeconds: Long = 120L,
        onOutput: (String) -> Unit = {}
    ): CommandResult {
        val start = System.currentTimeMillis()
        var output = ""
        var error = ""
        try {
            val (resultCommand, pb) = buildProcess(command, null)
            val process = pb.start()

            val outputReader = Thread {
                try {
                    val buffer = CharArray(4096)
                    val reader = process.inputStream.bufferedReader()
                    while (true) {
                        val read = reader.read(buffer, 0, buffer.size)
                        if (read == -1) break
                        output += String(buffer, 0, read)
                        onOutput(output)
                    }
                } catch (_: Exception) {
                }
            }
            val errorReader = Thread {
                try {
                    error = process.errorStream.bufferedReader().readText()
                } catch (_: Exception) {
                }
            }
            outputReader.start()
            errorReader.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                outputReader.join(2000)
                errorReader.join(2000)
                return CommandResult(
                    command = resultCommand,
                    output = output.trim(),
                    error = error.trim().ifBlank { "Timeout after ${timeoutSeconds}s" },
                    exitCode = -1,
                    duration = System.currentTimeMillis() - start
                )
            }

            outputReader.join(2000)
            errorReader.join(2000)
            val duration = System.currentTimeMillis() - start
            return CommandResult(
                command = resultCommand,
                output = output.trim(),
                error = error.trim().ifBlank { null },
                exitCode = process.exitValue(),
                duration = duration
            )
        } catch (e: Exception) {
            return CommandResult(
                command = command,
                output = "",
                error = e.message,
                exitCode = -1,
                duration = System.currentTimeMillis() - start
            )
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'"'"'") + "'"

    private fun buildProcess(command: String, workDir: File?): Pair<String, ProcessBuilder> {
        return when {
            // Runtime Termux embarqué (Phase 14) : bash/coreutils/apt/pkg intégrés
            // dans l'APK, exécutés via proot (remappe /data/data/com.termux/files/usr).
            TermuxRuntimeManager.isRuntimeInstalled -> {
                val pb = TermuxRuntimeManager.buildProcessBuilder(command, workDir)
                if (pb != null) Pair(command, pb) else buildFallback(command, workDir)
            }
            else -> buildFallback(command, workDir)
        }
    }

    private fun buildFallback(command: String, workDir: File?): Pair<String, ProcessBuilder> {
        return when {
            isRootAvailable -> {
                val pb = ProcessBuilder("su", "-c", command)
                pb.redirectErrorStream(false)
                pb.directory(workDir ?: File("/"))
                Pair("su -c $command", pb)
            }
            isTermuxRuntimeAvailable -> {
                val pb = ProcessBuilder(TERMUX_BIN.resolve("sh").absolutePath, "-c", command)
                pb.directory(workDir ?: File(TERMUX_HOME))
                val env = pb.environment()
                env["PATH"] = "$TERMUX_BIN:/system/bin:/system/xbin:/usr/bin:/bin"
                env["HOME"] = TERMUX_HOME
                env["PREFIX"] = TERMUX_PREFIX
                env["LD_LIBRARY_PATH"] = "$TERMUX_PREFIX/lib"
                Pair(command, pb)
            }
            else -> {
                val pb = ProcessBuilder("sh", "-c", command)
                pb.directory(workDir ?: File("/"))
                Pair(command, pb)
            }
        }
    }
}
