package com.voltai.doai.data.qwen

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket

class QwenClientTest {

    @Test
    fun `checkModel_retourne_true_si_modele_qwen_disponible`() = runBlocking {
        TestLlmServer { request, _, _ ->
            if (request == "GET /v1/models") {
                """{"object":"list","data":[{"id":"qwen2.5-coder-7b-instruct-uncensored","object":"model"}]}"""
            } else {
                """{"error":{"message":"unexpected"}}"""
            }
        }.use { server ->
            val client = QwenClient(baseUrlProvider = { "http://127.0.0.1:${server.port}" })
            assertTrue(client.checkModel())
        }
    }

    @Test
    fun `checkModel_retourne_false_si_pas_de_modele_qwen`() = runBlocking {
        TestLlmServer { request, _, _ ->
            """{"object":"list","data":[{"id":"llama-3-8b","object":"model"}]}"""
        }.use { server ->
            val client = QwenClient(baseUrlProvider = { "http://127.0.0.1:${server.port}" })
            assertFalse(client.checkModel())
        }
    }

    @Test
    fun `checkModel_retourne_false_sans_url`() = runBlocking {
        val client = QwenClient(baseUrlProvider = { "" })
        assertFalse(client.checkModel())
    }

    @Test
    fun `complete_retourne_le_contenu_assistant`() = runBlocking {
        TestLlmServer { request, body, _ ->
            if (request == "POST /v1/chat/completions") {
                """{"choices":[{"message":{"content":"Réponse de Qwen"}}]}"""
            } else {
                """{"object":"list","data":[{"id":"qwen2.5-coder-7b-instruct-uncensored","object":"model"}]}"""
            }
        }.use { server ->
            val client = QwenClient(baseUrlProvider = { "http://127.0.0.1:${server.port}" })
            val result = client.complete("Bonjour", "Tu es Qwen", 128)
            assertEquals("Réponse de Qwen", result.text)
        }
    }

    @Test
    fun `complete_retourne_erreur_sur_http_500`() = runBlocking {
        TestLlmServer { request, _, _ ->
            if (request == "POST /v1/chat/completions") {
                """{"error":{"message":"serveur surchargé"}}"""
            } else {
                """{"object":"list","data":[{"id":"qwen2.5-coder-7b","object":"model"}]}"""
            }
        }.use { server ->
            val client = QwenClient(baseUrlProvider = { "http://127.0.0.1:${server.port}" })
            val result = client.complete("Bonjour", "", 64)
            assertTrue(result.text.contains("serveur surchargé"))
        }
    }

    @Test
    fun `streamGenerate_accumule_les_deltas_sse`() = runBlocking {
        TestLlmServer { request, _, _ ->
            if (request == "POST /v1/chat/completions") {
                "data: {\"choices\":[{\"delta\":{\"content\":\"Bon\"}}]}\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"jour\"}}]}\n" +
                    "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n" +
                    "data: [DONE]\n"
            } else {
                """{"object":"list","data":[{"id":"qwen2.5-coder-7b","object":"model"}]}"""
            }
        }.use { server ->
            val client = QwenClient(baseUrlProvider = { "http://127.0.0.1:${server.port}" })
            val chunks = client.streamGenerate("Salut").toList()
            assertEquals(listOf("Bon", "jour"), chunks)
        }
    }

    /** Petit serveur HTTP local simulant llama-server. */
    private class TestLlmServer(
        private val handler: (request: String, body: String, query: String) -> String
    ) : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        private val running = java.util.concurrent.atomic.AtomicBoolean(true)
        private val thread = Thread { serve() }

        init {
            thread.isDaemon = true
            thread.start()
        }

        private fun serve() {
            while (running.get()) {
                runCatching {
                    val socket = server.accept()
                    Thread {
                        runCatching {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                            val requestLine = reader.readLine() ?: return@Thread
                            val parts = requestLine.split(" ")
                            val method = parts.getOrNull(0) ?: "GET"
                            val pathQuery = parts.getOrNull(1) ?: "/"
                            val path = pathQuery.substringBefore('?')
                            val query = pathQuery.substringAfter('?', "")
                            val headers = mutableMapOf<String, String>()
                            var line: String?
                            while (true) {
                                line = reader.readLine() ?: break
                                if (line.isBlank()) break
                                val idx = line.indexOf(':')
                                if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                            }
                            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                            val body = if (contentLength > 0) {
                                CharArray(contentLength).also { chars ->
                                    var read = 0
                                    while (read < contentLength) {
                                        val r = reader.read(chars, read, contentLength - read)
                                        if (r == -1) break
                                        read += r
                                    }
                                }.joinToString("")
                            } else ""

                            val key = "$method $path"
                            val response = handler(key, body, query)
                            val out = socket.getOutputStream()
                            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                            out.write(response.toByteArray(Charsets.UTF_8))
                            out.flush()
                            socket.close()
                        }
                    }.start()
                }.onFailure { if (it is IOException) return }
            }
        }

        override fun close() {
            running.set(false)
            runCatching { server.close() }
        }
    }
}