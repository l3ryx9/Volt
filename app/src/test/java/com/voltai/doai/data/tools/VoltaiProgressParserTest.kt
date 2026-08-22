package com.voltai.doai.data.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoltaiProgressParserTest {

    @Test
    fun `parses progress lines in order`() {
        val events = mutableListOf<Pair<Float, String>>()
        val parser = VoltaiProgressParser(onProgress = { p, m -> events.add(p to m) })

        parser.feed("[VOLTAI|PROGRESS|10|Décompression du bootstrap…]\n")
        parser.feed("[VOLTAI|PROGRESS|35|Installation d'Ubuntu 24.04…]\n[VOLTAI|PROGRESS|100|Installation terminée]\n")

        assertEquals(3, events.size)
        assertEquals(0.1f, events[0].first, 0.001f)
        assertEquals("Décompression du bootstrap…", events[0].second)
        assertEquals(0.35f, events[1].first, 0.001f)
        assertEquals(1.0f, events[2].first, 0.001f)
        assertFalse(parser.done)
    }

    @Test
    fun `buffers partial lines across feeds`() {
        val events = mutableListOf<Pair<Float, String>>()
        val parser = VoltaiProgressParser(onProgress = { p, m -> events.add(p to m) })

        parser.feed("[VOLTAI|PROGRESS|50|Étape")
        parser.feed(" suivante]\n")

        assertEquals(1, events.size)
        assertEquals(0.5f, events[0].first, 0.001f)
        assertEquals("Étape suivante", events[0].second)
    }

    @Test
    fun `handles multiline raw output mixed with markers`() {
        val events = mutableListOf<Pair<Float, String>>()
        val parser = VoltaiProgressParser(onProgress = { p, m -> events.add(p to m) })

        parser.feed("Downloading... 42%\n[VOLTAI|PROGRESS|60|Extraction des outils…]\nGET /ok 200\n")

        assertEquals(1, events.size)
        assertEquals(0.6f, events[0].first, 0.001f)
    }

    @Test
    fun `detects done error and fixed`() {
        val parser = VoltaiProgressParser(onProgress = { _, _ -> })

        parser.feed("[VOLTAI|DONE]\n")
        assertTrue(parser.done)

        val parser2 = VoltaiProgressParser(onProgress = { _, _ -> })
        parser2.feed("[VOLTAI|ERROR|Réseau indisponible]\n")
        assertFalse(parser2.done)
        assertEquals("Réseau indisponible", parser2.lastError)

        val parser3 = VoltaiProgressParser(onProgress = { _, _ -> })
        parser3.feed("[VOLTAI|FIXED|zip installé]\n")
        assertTrue(parser3.fixed.contains("zip installé"))
    }

    @Test
    fun `verify lines do not fire progress`() {
        val events = mutableListOf<Pair<Float, String>>()
        val parser = VoltaiProgressParser(onProgress = { p, m -> events.add(p to m) })

        parser.feed("[VOLTAI|VERIFY|python3.14 OK]\n[VOLTAI|VERIFY|jadx ABSENT]\n")

        assertTrue(events.isEmpty())
    }

    @Test
    fun `unknown output leaves progress untouched`() {
        val events = mutableListOf<Pair<Float, String>>()
        val parser = VoltaiProgressParser(onProgress = { p, m -> events.add(p to m) })

        parser.feed("some raw log\nline\n")
        assertTrue(events.isEmpty())
        assertNull(parser.lastError)
    }
}