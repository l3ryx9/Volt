package com.voltai.doai.data.qwen

import com.voltai.doai.data.llama.LlamaJsonParser
import com.voltai.doai.domain.models.GenerationResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Client HTTP/HTTPS vers llama-server exécuté sur Google Colab (Qwen 2.5
 * Coder 7B). Communique directement avec l'URL publique du tunnel :
 *   - GET  {URL}/v1/models            -> disponibilité du modèle
 *   - POST {URL}/v1/chat/completions  -> génération (complète ou SSE)
 *
 * Aucune clé API n'est envoyée. Toutes les opérations sont suspendues
 * (Dispatchers.IO) : l'interface reste fluide.
 */
class QwenClient(
    private val baseUrlProvider: () -> String,
    private val connectTimeoutMs: Int = 15_000
) {

    private val readTimeoutMs = 0

    /** URL publique du tunnel, sans slash final. */
    private fun baseUrl(): String = baseUrlProvider().trim().trimEnd('/')

    /** Vérifie que le serveur répond et qu'un modèle Qwen est disponible. */
    suspend fun checkModel(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = baseUrl() + "/v1/models"
            if (url == "/v1/models") return@withContext false
            val conn = open(url, "GET")
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext false
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                QwenJsonParser.hasQwen(body)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    /** Mini-requête de vie : GET /health (maintenir tunnel + serveur actifs). */
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val url = baseUrl() + "/health"
            if (url == "/health") return@withContext false
            val conn = open(url, "GET")
            try {
                conn.responseCode == HttpURLConnection.HTTP_OK
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    /** Génération complète (non streaming). */
    suspend fun complete(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512
    ): GenerationResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = baseUrl() + "/v1/chat/completions"
            if (url == "/v1/chat/completions") return@withContext GenerationResult("(URL Colab non configurée)")
            val conn = open(url, "POST")
            try {
                conn.outputStream.use { it.write(buildBody(prompt, systemPrompt, maxTokens, stream = false).toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val err = QwenJsonParser.error(body) ?: "HTTP $code"
                    GenerationResult("(Erreur Qwen : $err)")
                } else {
                    QwenJsonParser.parse(body)
                }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e -> GenerationResult("(Erreur Qwen : ${e.message ?: "réseau indisponible"})") }
    }

    /** Génération en streaming SSE, token par token. */
    fun streamGenerate(
        prompt: String,
        systemPrompt: String = "",
        maxTokens: Int = 512
    ): Flow<String> = flow {
        val url = baseUrl() + "/v1/chat/completions"
        if (url == "/v1/chat/completions") {
            emit("(URL Colab non configurée)")
            return@flow
        }
        val conn = open(url, "POST")
        try {
            conn.outputStream.use { it.write(buildBody(prompt, systemPrompt, maxTokens, stream = true).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            if (code !in 200..299) {
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val err = QwenJsonParser.error(body) ?: "HTTP $code"
                emit("(Erreur Qwen : $err)")
                return@flow
            }
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            var line: String?
            while (true) {
                line = reader.readLine() ?: break
                if (QwenJsonParser.isDone(line)) break
                val chunk = QwenJsonParser.parseSseLine(line) ?: continue
                if (chunk.isNotBlank()) emit(chunk)
            }
        } catch (e: Exception) {
            emit("(Erreur Qwen : ${e.message ?: "connexion interrompue"})")
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("User-Agent", "VoltAI-Qwen/1.0")
            if (method == "POST") doOutput = true
        }
        return conn
    }

    /** Construit le corps de la requête /v1/chat/completions. */
    private fun buildBody(prompt: String, systemPrompt: String, maxTokens: Int, stream: Boolean): String {
        val sb = StringBuilder()
        sb.append("{\"model\":\"qwen2.5-coder-7b-instruct-uncensored\",\"messages\":[")
        if (systemPrompt.isNotBlank()) {
            sb.append("{\"role\":\"system\",\"content\":\"").append(escape(systemPrompt)).append("\"},")
        }
        sb.append("{\"role\":\"user\",\"content\":\"").append(escape(prompt)).append("\"}],")
        sb.append("\"max_tokens\":").append(maxTokens)
        sb.append(",\"stream\":").append(stream)
        sb.append("}")
        return sb.toString()
    }

    private fun escape(value: String): String = LlamaJsonParser.escape(value)

    companion object {
        const val MODEL_ID = "qwen2.5-coder-7b-instruct-uncensored"
    }
}