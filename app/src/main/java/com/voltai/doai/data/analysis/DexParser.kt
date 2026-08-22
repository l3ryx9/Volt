package com.voltai.doai.data.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexParseResult(
    val totalStrings: Int,
    val classDescriptors: List<String>,
    val encryptionStrings: List<String>,
    val obfuscationTechniques: List<String>
)

class DexParser {

    private val encryptionKeywords = listOf(
        "AES", "DESede", "DES/", "RSA/", "Cipher", "SecretKeySpec", "IvParameterSpec",
        "Base64", "MD5", "SHA-256", "SHA256", "PBKDF2", "PBEKeySpec", "encrypt", "decrypt",
        "RC4", "Blowfish", "ChaCha20", "Crypto", "XOR", "keystore", "signature", "cipher"
    )

    private val obfuscationMarkers = listOf(
        "OLLVM", "ollvm", "obfuscat", "Lcom/meitu/advsafe", "Lcom/wrapper", "Lcom/duk",
        "Lcom/secneo", "Lcom/qihoo/util", "Lcom/tencent/stub", "Lcom/bangcle", "Lcom/alipay/android",
        "StubApp", "StubProxy", "tlibc", "libnq", "libjiagu", "libsecexe", "libprotectClass",
        "libDexHelper", "libddog", "libshell", "libapp", "secneo", "bangcle", "jiagu",
        "liugu", "libnesec", "libmsaoaidsec", "lib/arm", "reflect", "classloader"
    )

    fun parse(bytes: ByteArray): DexParseResult {
        if (bytes.size < 112) return DexParseResult(0, emptyList(), emptyList(), emptyList())
        val magic = String(bytes, 0, 4, Charsets.US_ASCII)
        if (magic != "dex\n") return DexParseResult(0, emptyList(), emptyList(), emptyList())

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val stringIdsSize = buf.getInt(56)
        val stringIdsOff = buf.getInt(60)
        if (stringIdsSize < 0 || stringIdsOff < 0 || stringIdsOff + stringIdsSize * 4 > bytes.size) {
            return DexParseResult(0, emptyList(), emptyList(), emptyList())
        }

        val strings = mutableListOf<String>()
        val classDescriptors = mutableListOf<String>()
        val encryptionStrings = mutableListOf<String>()

        val seenObfuscation = LinkedHashSet<String>()
        val seenEncryption = LinkedHashSet<String>()

        for (i in 0 until stringIdsSize) {
            val stringOffset = buf.getInt(stringIdsOff + i * 4)
            if (stringOffset < 0 || stringOffset >= bytes.size) continue

            var pos = stringOffset
            var utf16Length = 0
            var lengthShift = 0
            while (true) {
                val b = bytes[pos++].toInt() and 0xFF
                utf16Length = utf16Length or ((b and 0x7F) shl lengthShift)
                if (b and 0x80 == 0) break
                lengthShift += 7
                if (pos >= bytes.size) break
            }

            var end = pos
            while (end < bytes.size && bytes[end] != 0.toByte()) end++
            if (end == bytes.size) continue

            val str = try {
                String(bytes, pos, end - pos, Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
            if (str.isEmpty()) continue
            strings.add(str)

            if (str.length > 2 && str[0] == 'L' && str[str.length - 1] == ';') {
                classDescriptors.add(str)
            }
            val lower = str.lowercase()
            val isEnc = encryptionKeywords.any { lower.contains(it.lowercase()) }
            if (isEnc && str.length in 4..80) {
                seenEncryption.add(str)
                encryptionStrings.add(str)
            }
            val isObf = obfuscationMarkers.any { marker ->
                str.contains(marker, ignoreCase = true)
            }
            if (isObf) {
                val technique = str
                    .substringAfterLast('/')
                    .substringBefore(';')
                    .take(60)
                if (technique.isNotEmpty()) seenObfuscation.add(technique)
            }
        }

        return DexParseResult(
            totalStrings = strings.size,
            classDescriptors = classDescriptors.distinct().take(500),
            encryptionStrings = seenEncryption.toList().take(200),
            obfuscationTechniques = seenObfuscation.toList().take(30)
        )
    }
}
