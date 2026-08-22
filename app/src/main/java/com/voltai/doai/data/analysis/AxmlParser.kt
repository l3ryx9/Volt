package com.voltai.doai.data.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AxmlNode(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<AxmlNode>
)

class AxmlParser {

    fun parse(bytes: ByteArray): AxmlNode? {
        if (bytes.size < 8) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val rootChunkType = buf.short.toInt() and 0xFFFF
        buf.short // headerSize
        buf.int // size
        if (rootChunkType != 0x0003) return null

        val stringPool = mutableListOf<String>()
        var rootNode: AxmlNode? = null
        val stack = ArrayDeque<AxmlNode>()

        while (buf.remaining() >= 8) {
            val chunkStart = buf.position()
            val type = buf.short.toInt() and 0xFFFF
            val headerSize = buf.short.toInt() and 0xFFFF
            val chunkSize = buf.int
            if (chunkSize < headerSize || chunkSize < 8) break

            when (type) {
                0x0001 -> {
                    val pool = parseStringPool(buf)
                    stringPool.clear()
                    stringPool.addAll(pool)
                }
                0x0102 -> {
                    val node = parseStartElement(buf, headerSize, stringPool)
                    if (stack.isEmpty()) rootNode = node
                    stack.addLast(node)
                }
                0x0103 -> {
                    if (buf.remaining() >= 8) {
                        buf.int // ns
                        buf.int // name
                    }
                    if (stack.isNotEmpty()) {
                        val node = stack.removeLast()
                        if (stack.isNotEmpty()) {
                            @Suppress("UNCHECKED_CAST")
                            (stack.last().children as MutableList<AxmlNode>).add(node)
                        }
                    }
                }
                0x0104 -> {
                    if (buf.remaining() >= 12) {
                        buf.int // ns
                        buf.int // name
                        val size = buf.short.toInt() and 0xFFFF
                        if (size > 4 && buf.remaining() >= size - 4) {
                            buf.position(buf.position() + size - 4)
                        }
                    }
                }
                0x0100, 0x0101 -> {
                    if (buf.remaining() >= 8) {
                        buf.int
                        buf.int
                    }
                }
                else -> {
                    val toSkip = chunkSize - 8
                    if (toSkip > 0 && buf.remaining() >= toSkip) {
                        buf.position(buf.position() + toSkip)
                    } else {
                        buf.position(buf.limit())
                    }
                }
            }

            val chunkEnd = chunkStart + chunkSize
            if (chunkEnd <= buf.limit()) {
                buf.position(chunkEnd)
            } else {
                buf.position(buf.limit())
            }
        }

        return rootNode
    }

    private fun parseStartElement(buf: ByteBuffer, headerSize: Int, pool: List<String>): AxmlNode {
        buf.int // lineNumber
        buf.int // comment ref
        buf.int // ns (élément)
        val nameRef = buf.int
        val attributeStart = buf.short.toInt() and 0xFFFF
        buf.short // attributeSize
        val attributeCount = buf.short.toInt() and 0xFFFF
        buf.short // idIndex
        buf.short // classIndex
        buf.short // styleIndex

        // L'attribut attributeStart est relatif au début du struct attributeExt,
        // qui commence à l'offset headerSize du chunk (après header + lineNumber + comment).
        val consumedFromChunk = 36 // 8 header + 28 champs struct
        val attrsFromChunk = headerSize + attributeStart
        val toSkip = attrsFromChunk - consumedFromChunk
        if (toSkip > 0 && buf.remaining() >= toSkip) buf.position(buf.position() + toSkip)

        val name = pool.getOrElse(nameRef) { "unknown" }
        val attributes = mutableMapOf<String, String>()

        repeat(attributeCount) {
            val attrNs = buf.int
            val attrNameRef = buf.int
            val rawValue = buf.int
            val valueSize = buf.short.toInt() and 0xFFFF
            buf.get() // res0
            val dataType = buf.get().toInt() and 0xFF
            val data = buf.int
            if (valueSize > 8 && buf.remaining() >= valueSize - 8) {
                buf.position(buf.position() + valueSize - 8)
            }

            val attrName = pool.getOrElse(attrNameRef) { "attr$it" }
            val attrNamespace = if (attrNs >= 0 && attrNs < pool.size) pool[attrNs] else ""
            val value = if (rawValue != -1 && rawValue < pool.size) {
                pool[rawValue]
            } else {
                formatTypedValue(dataType, data)
            }
            val key = when {
                attrNamespace == ANDROID_NS -> "android:$attrName"
                attrNamespace.isNotBlank() -> "$attrNamespace:$attrName"
                else -> attrName
            }
            attributes[key] = value
        }

        return AxmlNode(name = name, attributes = attributes, children = mutableListOf())
    }

    private fun formatTypedValue(dataType: Int, data: Int): String {
        return when (dataType) {
            0x10 -> data.toString()
            0x11 -> "0x${Integer.toHexString(data)}"
            0x12 -> if (data != 0) "true" else "false"
            0x01, 0x02 -> if (data == 0) "" else "@$data"
            0x03 -> data.toString()
            0x04 -> java.lang.Float.intBitsToFloat(data).toString()
            else -> data.toString()
        }
    }

    private fun parseStringPool(buf: ByteBuffer): List<String> {
        val chunkStart = buf.position() - 8

        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesStart
        val isUtf8 = flags and 0x100 != 0

        val offsets = IntArray(stringCount) { buf.int }
        if (styleCount > 0) {
            repeat(styleCount) { buf.int }
        }

        val payloadStart = chunkStart + stringsStart
        val limit = buf.limit()
        val result = mutableListOf<String>()

        for (offset in offsets) {
            val abs = payloadStart + offset
            if (abs < 0 || abs >= limit) {
                result.add("")
                continue
            }
            if (isUtf8) {
                var pos = abs
                // charLen (non utilisé pour la lecture des octets)
                val charLen0 = buf.get(pos).toInt() and 0xFF
                pos += if (charLen0 and 0x80 != 0) 2 else 1
                var byteLen = buf.get(pos).toInt() and 0xFF
                if (byteLen and 0x80 != 0) {
                    byteLen = (byteLen and 0x7F) shl 8 or (buf.get(pos + 1).toInt() and 0xFF)
                    pos += 2
                } else {
                    pos += 1
                }
                if (pos + byteLen > limit) {
                    result.add("")
                    continue
                }
                val bytes = ByteArray(byteLen)
                for (i in 0 until byteLen) bytes[i] = buf.get(pos + i)
                result.add(String(bytes, Charsets.UTF_8))
            } else {
                val charCount = buf.getShort(abs).toInt() and 0xFFFF
                val sb = StringBuilder(charCount)
                for (i in 0 until charCount) {
                    val idx = abs + 2 + i * 2
                    if (idx + 1 >= limit) break
                    val lo = buf.get(idx).toInt() and 0xFF
                    val hi = buf.get(idx + 1).toInt() and 0xFF
                    sb.append((lo or (hi shl 8)).toChar())
                }
                result.add(sb.toString())
            }
        }
        return result
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
