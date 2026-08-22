package com.voltai.doai.data.llama

/**
 * Échappement / déséchappement des chaînes JSON (utilisé par le client Qwen
 * et le parseur de réponses de llama-server).
 */
object LlamaJsonParser {

    /** Échappe une chaîne pour l'inclure dans du JSON. */
    fun escape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        sb.append("\\u").append(String.format("%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    /** Déséchappe une chaîne extraite du JSON. */
    fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'u' -> {
                        if (i + 5 < value.length) {
                            val hex = value.substring(i + 2, i + 6)
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: n)
                            i += 6
                        } else {
                            sb.append(c); i++
                        }
                    }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}