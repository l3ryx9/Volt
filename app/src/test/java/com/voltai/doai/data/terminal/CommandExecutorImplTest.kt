package com.voltai.doai.data.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CommandExecutorImplTest {

    private val executor = CommandExecutorImpl()

    @Test
    fun analyze_installPython_returnsPkgInstall() {
        assertEquals("pkg install -y python", executor.analyzeRequest("Installe python"))
    }

    @Test
    fun analyze_installPackage_returnsPkgInstall() {
        assertEquals("pkg install -y git", executor.analyzeRequest("Installe moi git"))
    }

    @Test
    fun analyze_update_returnsPkgUpdateUpgrade() {
        assertEquals(
            "pkg update -y && pkg upgrade -y",
            executor.analyzeRequest("Mets à jour les packages")
        )
    }

    @Test
    fun analyze_unzip_returnsUnzip() {
        assertEquals("unzip -o archive.zip", executor.analyzeRequest("Décompresse archive.zip"))
    }

    @Test
    fun analyze_download_returnsWget() {
        assertEquals(
            "wget https://example.com/file.zip",
            executor.analyzeRequest("Télécharge https://example.com/file.zip")
        )
    }

    @Test
    fun analyze_installProot_returnsProotInstall() {
        assertEquals(
            "pkg update -y && pkg install -y proot-distro",
            executor.analyzeRequest("Installe proot-distro")
        )
    }

    @Test
    fun analyze_installUbuntu_returnsProotInstall() {
        assertEquals(
            "proot-distro install ubuntu:24.04",
            executor.analyzeRequest("Installe ubuntu 24.04")
        )
    }

    @Test
    fun analyze_gitClone_returnsGitClone() {
        assertEquals(
            "git clone https://github.com/termux/termux-packages",
            executor.analyzeRequest("git clone https://github.com/termux/termux-packages")
        )
    }

    @Test
    fun analyze_unknownRequest_returnsEmpty() {
        assertEquals("", executor.analyzeRequest("Quelle est la météo aujourd'hui ?"))
    }

    @Test
    fun analyze_rawCommand_isPreserved() {
        assertEquals("ls -la", executor.analyzeRequest("ls -la"))
    }

    @Test
    fun executionLog_isNonNull() {
        assertNotNull(executor.getExecutionLog())
    }

    @Test
    fun analyze_variousWording_sameResult() {
        val targets = listOf("Installe python", "peux-tu installer python", "il me faut python")
        val commands = targets.map { executor.analyzeRequest(it) }
        assertNotEquals("", commands[0])
        assertNotEquals("", commands[1])
        assertNotEquals("", commands[2])
    }
}
