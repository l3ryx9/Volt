package com.voltai.doai.data.storage

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileAndArchiveManagerTest {

    private lateinit var tempDir: File
    private val fileManager = FileManagerImpl()
    private val archiveManager = ArchiveManagerImpl()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("voltai_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun File.write(content: String): File {
        parentFile?.mkdirs()
        writeText(content)
        return this
    }

    @Test
    fun fileManager_listsDirectory_dirsFirstThenFiles() {
        File(tempDir, "notes.txt").write("hello")
        File(tempDir, "app.apk").write("pk")
        File(tempDir, "subdir").mkdirs()

        val entries = fileManager.listDirectory(tempDir.absolutePath)
        assertEquals(3, entries.size)
        assertTrue(entries[0].isDirectory)
        assertFalse(entries[1].isDirectory)
        assertEquals("app.apk", entries[1].name)
        assertEquals("apk", entries[1].extension)
        assertTrue(entries[1].size > 0)
    }

    @Test
    fun fileManager_detectsTextFiles() {
        assertTrue(fileManager.isTextFile("/tmp/Main.kt"))
        assertTrue(fileManager.isTextFile("/tmp/AndroidManifest.xml"))
        assertTrue(fileManager.isTextFile("/tmp/classes.dex"))
        assertFalse(fileManager.isTextFile("/tmp/unknown.bin"))
    }

    @Test
    fun fileManager_readsTextFile() {
        val f = File(tempDir, "readme.txt").write("contenu test")
        assertEquals("contenu test", fileManager.readTextFile(f.absolutePath))
    }

    @Test
    fun fileManager_searchesFiles_recursively() {
        File(tempDir, "src/Main.kt").write("fun main() {}")
        File(tempDir, "src/utils/Helper.kt").write("fun helper() {}")
        File(tempDir, "res/layout.xml").write("<xml/>")

        val results = fileManager.searchFiles("kt", tempDir.absolutePath)
        assertEquals(2, results.size)
        assertTrue(results.all { it.name.endsWith(".kt") })

        val none = fileManager.searchFiles("zebra", tempDir.absolutePath)
        assertTrue(none.isEmpty())
    }

    @Test
    fun archiveManager_detectsSupportedFormats() {
        assertTrue(archiveManager.isSupportedArchive("/tmp/app.apk"))
        assertTrue(archiveManager.isSupportedArchive("/tmp/a.zip"))
        assertTrue(archiveManager.isSupportedArchive("/tmp/a.rar"))
        assertTrue(archiveManager.isSupportedArchive("/tmp/a.7z"))
        assertTrue(archiveManager.isSupportedArchive("/tmp/a.tar.gz"))
        assertFalse(archiveManager.isSupportedArchive("/tmp/movie.mp4"))
    }

    @Test
    fun archiveManager_getArchiveType() {
        assertEquals("zip", archiveManager.getArchiveType("/tmp/app.apk"))
        assertEquals("zip", archiveManager.getArchiveType("/tmp/a.jar"))
        assertEquals("rar", archiveManager.getArchiveType("/tmp/a.rar"))
        assertEquals("7z", archiveManager.getArchiveType("/tmp/a.7z"))
        assertEquals("tar", archiveManager.getArchiveType("/tmp/a.tar.gz"))
        assertEquals("", archiveManager.getArchiveType("/tmp/movie.mp4"))
    }

    @Test
    fun archiveManager_createAndExtractZip_roundTrip() = runBlocking {
        val source = File(tempDir, "src")
        File(source, "Main.kt").write("fun main() {}")
        File(source, "res/values/strings.xml").write("<string>hi</string>")
        val zipPath = File(tempDir, "out/archive.zip").absolutePath

        val createProgress = archiveManager.createArchive(source.absolutePath, zipPath).toList()
        val lastCreate = createProgress.last()
        assertTrue("Création doit finir sans erreur: ${lastCreate.error}", lastCreate.error == null)
        assertTrue(lastCreate.isFinished)
        assertEquals(100f, lastCreate.percentage, 0.1f)
        assertEquals(2, lastCreate.totalFiles)

        val dest = File(tempDir, "out/extracted")
        val extractProgress = archiveManager.extractArchive(zipPath, dest.absolutePath).toList()
        val lastExtract = extractProgress.last()
        assertTrue("Extraction doit finir sans erreur: ${lastExtract.error}", lastExtract.error == null)
        assertTrue(lastExtract.isFinished)
        assertEquals(100f, lastExtract.percentage, 0.1f)
        assertEquals(2, lastExtract.processedFiles)

        assertTrue(File(dest, "Main.kt").exists())
        assertTrue(File(dest, "res/values/strings.xml").exists())
    }

    @Test
    fun archiveManager_createAndExtractZip_emitsProgress() = runBlocking {
        val source = File(tempDir, "prog")
        File(source, "a.txt").write("a")
        File(source, "b.txt").write("b")
        File(source, "c.txt").write("c")
        val zipPath = File(tempDir, "prog.zip").absolutePath

        val progress = archiveManager.createArchive(source.absolutePath, zipPath).toList()
        assertTrue(progress.size >= 3)
        val running = progress.filter { !it.isFinished }
        assertTrue(running.any { it.processedFiles in 1..3 })
    }

    @Test
    fun archiveManager_extract_unsupportedFormat_emitsError() = runBlocking {
        val f = File(tempDir, "movie.mp4").write("bytes")
        val progress = archiveManager.extractArchive(f.absolutePath, tempDir.absolutePath).toList()
        val last = progress.last()
        assertTrue(last.isFinished)
        assertTrue(last.error != null)
    }
}
