package com.voltai.doai.data.llama

import com.voltai.doai.domain.interfaces.MemoryManager
import com.voltai.doai.domain.models.LlamaMemory
import com.voltai.doai.domain.models.MemoryKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mémoire persistante de Qwen : réussites, erreurs, solutions et commandes
 * efficaces, au format Demande → Action → Résultat → Modification → Validation.
 */
class MemoryManagerImpl(
    private val memoryFile: File
) : MemoryManager {

    private val entries = mutableListOf<LlamaMemory>()
    private val lock = Any()

    init {
        load()
    }

    override suspend fun record(entry: LlamaMemory) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            entries.add(entry)
            persistLocked()
        }
    }

    override fun successes(): List<LlamaMemory> = synchronized(lock) { filterKind(MemoryKind.SUCCESS) }

    override fun errors(): List<LlamaMemory> = synchronized(lock) { filterKind(MemoryKind.ERROR) }

    override fun solutions(): List<LlamaMemory> = synchronized(lock) { filterKind(MemoryKind.SOLUTION) }

    override fun effectiveCommands(): List<LlamaMemory> =
        synchronized(lock) { filterKind(MemoryKind.EFFECTIVE_COMMAND) }

    override fun recall(query: String, limit: Int): List<LlamaMemory> {
        val words = query.lowercase()
            .split(Regex("[^a-z0-9àâäéèêëîïôöùûüç\\-]"))
            .map { it.trim('-') }
            .filter { it.length > 2 }
            .toSet()
        synchronized(lock) {
            if (words.isEmpty()) return entries.reversed().take(limit)
            val scored = entries.map { memory ->
                val haystack = (memory.demande + " " + memory.action + " " + memory.resultat + " " +
                    memory.modification + " " + memory.validation).lowercase()
                val score = words.count { word -> haystack.contains(word) }
                memory to score
            }
            return scored
                .filter { it.second > 0 }
                .sortedWith(compareByDescending<Pair<LlamaMemory, Int>> { it.second }
                    .thenByDescending { it.first.timestamp })
                .map { it.first }
                .take(limit)
        }
    }

    override fun all(): List<LlamaMemory> = synchronized(lock) { entries.reversed().toList() }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                entries.clear()
                memoryFile.delete()
            }
        }
    }

    private fun filterKind(kind: MemoryKind): List<LlamaMemory> =
        entries.asReversed().filter { it.kind == kind }.toList()

    private fun load() {
        if (!memoryFile.isFile) return
        runCatching {
            memoryFile.readLines().forEach { line ->
                val fields = line.split('|')
                if (fields.size >= 5) {
                    entries.add(
                        LlamaMemory(
                            kind = fields[1].let { MemoryKind.entries.firstOrNull { k -> k.name == it } ?: MemoryKind.SUCCESS },
                            demande = unescape(fields[2]),
                            action = unescape(fields[3]),
                            resultat = unescape(fields[4]),
                            modification = fields.getOrNull(5)?.let { unescape(it) } ?: "",
                            validation = fields.getOrNull(6)?.let { unescape(it) } ?: "",
                            timestamp = fields[0].toLongOrNull() ?: 0L
                        )
                    )
                }
            }
        }
    }

    private fun persistLocked() {
        runCatching {
            memoryFile.parentFile?.mkdirs()
            val tmp = File(memoryFile.parentFile, "${memoryFile.name}.tmp")
            tmp.writeText(buildString {
                entries.forEach { memory ->
                    append(memory.timestamp)
                        .append('|').append(memory.kind.name)
                        .append('|').append(escape(memory.demande))
                        .append('|').append(escape(memory.action))
                        .append('|').append(escape(memory.resultat))
                        .append('|').append(escape(memory.modification))
                        .append('|').append(escape(memory.validation))
                        .append('\n')
                }
            })
            tmp.renameTo(memoryFile)
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("|", "\\p")
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'p' -> { sb.append('|'); i += 2 }
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
