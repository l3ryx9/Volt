package com.voltai.doai.data.tools

/**
 * Parse les lignes émises par les scripts voltai-setup.sh / voltai-fix.sh.
 *
 * Format :
 *   [VOLTAI|PROGRESS|<0-100>|<message>]
 *   [VOLTAI|ERROR|<message>]
 *   [VOLTAI|FIXED|<message>]
 *   [VOLTAI|DONE]
 *   [VOLTAI|VERIFY|<outil> OK|ABSENT]
 *
 * [feed] reçoit la sortie cumulative du processus : les lignes incomplètes
 * coupées entre deux lectures sont mises en attente jusqu'au prochain appel.
 */
class VoltaiProgressParser(
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
    private val onError: (String) -> Unit = {},
    private val onFixed: (String) -> Unit = {},
    private val onDone: () -> Unit = {}
) {

    private var pending = ""

    var lastError: String? = null
        private set

    var done: Boolean = false
        private set

    val fixed: MutableList<String> = mutableListOf()

    fun feed(output: String) {
        pending += output
        val lastNewline = pending.lastIndexOf('\n')
        if (lastNewline < 0) return
        val complete = pending.substring(0, lastNewline)
        pending = pending.substring(lastNewline + 1)
        for (line in complete.split('\n')) {
            parseLine(line.trim())
        }
    }

    fun reset() {
        pending = ""
        lastError = null
        done = false
        fixed.clear()
    }

    private fun parseLine(line: String) {
        when {
            line.startsWith(PROGRESS_PREFIX) -> {
                val body = line.removePrefix(PROGRESS_PREFIX).removeSuffix("]")
                val parts = body.split("|", limit = 2)
                val n = parts.firstOrNull()?.trim()?.toIntOrNull()
                if (n != null) {
                    onProgress(n.coerceIn(0, 100) / 100f, parts.getOrNull(1)?.trim().orEmpty())
                }
            }

            line.startsWith(ERROR_PREFIX) -> {
                lastError = line.removePrefix(ERROR_PREFIX).removeSuffix("]").trim()
                    .ifBlank { "Erreur inconnue pendant l'installation" }
                onError(lastError!!)
            }

            line.startsWith(FIXED_PREFIX) -> {
                fixed += line.removePrefix(FIXED_PREFIX).removeSuffix("]").trim()
                onFixed(fixed.last())
            }

            line.startsWith(DONE_PREFIX) -> {
                done = true
                onDone()
            }
        }
    }

    private companion object {
        const val PROGRESS_PREFIX = "[VOLTAI|PROGRESS|"
        const val ERROR_PREFIX = "[VOLTAI|ERROR|"
        const val FIXED_PREFIX = "[VOLTAI|FIXED|"
        const val DONE_PREFIX = "[VOLTAI|DONE]"
    }
}