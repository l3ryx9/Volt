package com.voltai.doai.data.qwen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenJsonParserTest {

    @Test
    fun `parseModels_extrait_les_identifiants`() {
        val json = """{"object":"list","data":[{"id":"qwen2.5-coder-7b-instruct-uncensored","object":"model"},{"id":"qwen2.5-coder-3b","object":"model"}]}"""
        val models = QwenJsonParser.parseModels(json)
        assertEquals(2, models.size)
        assertEquals("qwen2.5-coder-7b-instruct-uncensored", models[0])
    }

    @Test
    fun `hasQwen_detecte_un_modele_qwen`() {
        val json = """{"object":"list","data":[{"id":"QWEN2.5-coder-7b","object":"model"}]}"""
        assertTrue(QwenJsonParser.hasQwen(json))
        assertFalse(QwenJsonParser.hasQwen("""{"object":"list","data":[{"id":"llama-3","object":"model"}]}"""))
        assertFalse(QwenJsonParser.hasQwen("{}"))
    }

    @Test
    fun `parseContent_extrait_le_message_assistant`() {
        val json = """{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"Bonjour \"monde\"\nRéponse."},"finish_reason":"stop"}]}"""
        assertEquals("Bonjour \"monde\"\nRéponse.", QwenJsonParser.parseContent(json))
    }

    @Test
    fun `parse_retourne_resultat_complet`() {
        val json = """{"choices":[{"message":{"content":"Réponse finale"}}]}"""
        val result = QwenJsonParser.parse(json)
        assertEquals("Réponse finale", result.text)
    }

    @Test
    fun `error_extrait_le_message_objet_error`() {
        val json = """{"error":{"message":"Le modèle n'existe pas","type":"invalid_request_error"}}"""
        assertEquals("Le modèle n'existe pas", QwenJsonParser.error(json))
    }

    @Test
    fun `error_retourne_null_si_absent`() {
        assertNull(QwenJsonParser.error("""{"choices":[{"message":{"content":"ok"}}]}"""))
    }

    @Test
    fun `parse_retourne_erreur_si_json_contient_error`() {
        val result = QwenJsonParser.parse("""{"error":{"message":"serveur occupé"}}""")
        assertTrue(result.text.startsWith("(Erreur Qwen"))
    }

    @Test
    fun `parseSseLine_extrait_delta_content`() {
        val line = """data: {"choices":[{"delta":{"content":"Bon"},"finish_reason":null}]}"""
        assertEquals("Bon", QwenJsonParser.parseSseLine(line))
    }

    @Test
    fun `parseSseLine_ignore_lignes_vides_et_done`() {
        assertNull(QwenJsonParser.parseSseLine(""))
        assertNull(QwenJsonParser.parseSseLine("data: [DONE]"))
        assertNull(QwenJsonParser.parseSseLine(":commentaire"))
        assertNull(QwenJsonParser.parseSseLine("data: {\"choices\":[{\"delta\":{}}]}"))
    }

    @Test
    fun `isDone_detecte_la_fin_du_flux`() {
        assertTrue(QwenJsonParser.isDone("data: [DONE]"))
        assertFalse(QwenJsonParser.isDone("data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}]}"))
    }
}