package com.voltai.doai.data.analysis

import com.voltai.doai.data.storage.ArchiveManagerImpl
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AnalysisTest {

    private lateinit var tempDir: File
    private val archiveManager = ArchiveManagerImpl()
    private val analyzer = APKAnalyzerImpl(archiveManager)

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("voltai_analysis").toFile()
    }

    private fun resource(name: String): File {
        val bytes = javaClass.getResourceAsStream("/$name")!!.readBytes()
        val f = File(tempDir, name)
        f.writeBytes(bytes)
        return f
    }

    @Test
    fun axmlParser_parsesBinaryManifest() = runBlocking {
        val apk = resource("full-test.apk")
        val extracted = File(tempDir, "extracted")
        archiveManager.extractArchive(apk.absolutePath, extracted.absolutePath).last()

        val parser = AxmlParser()
        val node = parser.parse(File(extracted, "AndroidManifest.xml").readBytes())
        assertNotNull(node)
        assertEquals("manifest", node!!.name)
        assertEquals("com.example.qwentest", node.attributes["package"])
        assertEquals("42", node.attributes["android:versionCode"])
        assertEquals("2.0.1", node.attributes["android:versionName"])

        val usesSdk = node.children.first { it.name == "uses-sdk" }
        assertEquals("26", usesSdk.attributes["android:minSdkVersion"])

        val permission = node.children.first { it.name == "uses-permission" }
        assertEquals("android.permission.INTERNET", permission.attributes["android:name"])
    }

    @Test
    fun analyzer_pipeline_buildsCompleteReport() = runBlocking {
        val apk = resource("full-test.apk")
        val report = analyzer.analyzeApk(apk.absolutePath)

        assertEquals("com.example.qwentest", report.manifest.packageName)
        assertEquals(42, report.manifest.versionCode)
        assertEquals("2.0.1", report.manifest.versionName)
        assertEquals(26, report.manifest.minSdk)
        assertEquals(35, report.manifest.targetSdk)

        assertTrue(report.manifest.permissions.contains("android.permission.INTERNET"))
        assertTrue(report.manifest.permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals(".MainActivity", report.manifest.activities.first())
        assertEquals(".MainService", report.manifest.services.first())
        assertEquals(".BootReceiver", report.manifest.receivers.first())
        assertEquals(".MyProvider", report.manifest.providers.first())

        assertEquals(listOf("classes.dex"), report.dex.dexFiles)
        assertTrue(report.dex.totalStrings > 0)
        assertTrue(report.dex.classDescriptors.isNotEmpty())
        assertTrue(report.dex.classDescriptors.any { it.contains("Hello") })

        assertTrue(report.apkSize > 0)
        assertTrue(report.extractedPath.isNotBlank())
    }

    @Test
    fun dexParser_extractsStringsAndClasses() {
        val bytes = resource("test-classes.dex").readBytes()
        val result = DexParser().parse(bytes)
        assertTrue(result.totalStrings > 0)
        assertTrue(result.classDescriptors.any { it.startsWith("Lcom/example/Hello;") })
        assertTrue(result.encryptionStrings.isNotEmpty())
    }

    @Test
    fun dexParser_rejectsNonDex() {
        val result = DexParser().parse(ByteArray(200) { 0x42 })
        assertEquals(0, result.totalStrings)
        assertTrue(result.classDescriptors.isEmpty())
    }

    @Test
    fun codeExplorer_findsClassesAndStrings() {
        val root = File(tempDir, "src")
        File(root, "com/app").mkdirs()
        File(root, "com/app/Auth.java").writeText(
            "public class Auth { public String token = \"secret_key_42\"; public String getToken() { return token; } }"
        )
        File(root, "com/app/Main.kt").writeText("fun main() { println(\"hello world\") }")
        File(root, "com/app/data.bin").writeText("not code")

        val explorer = CodeExplorerImpl()
        val classes = explorer.findClasses(root.absolutePath)
        assertTrue(classes.any { it.contains("Auth.java") })
        assertTrue(classes.any { it.contains("Main.kt") })

        val secrets = explorer.findStrings(root.absolutePath, "secret")
        assertTrue(secrets.any { it.contains("secret_key_42") })

        val methods = explorer.findMethods(root.absolutePath, "token")
        assertTrue(methods.isNotEmpty())
    }

    @Test
    fun reportGenerator_formatsFullReport() {
        val manifest = com.voltai.doai.domain.models.ManifestInfo(
            packageName = "com.test.app",
            versionName = "1.0",
            versionCode = 1,
            minSdk = 26,
            targetSdk = 35,
            applicationLabel = "Test",
            permissions = listOf("android.permission.INTERNET"),
            activities = listOf(".MainActivity"),
            services = emptyList(),
            receivers = emptyList(),
            providers = emptyList(),
            usesFeatures = emptyList()
        )
        val dex = com.voltai.doai.domain.models.DexInfo(
            dexFiles = listOf("classes.dex"),
            totalStrings = 100,
            classDescriptors = listOf("Lcom/test/App;"),
            encryptionStrings = listOf("AES/CBC/PKCS5Padding"),
            obfuscationTechniques = listOf("jiagu")
        )
        val report = com.voltai.doai.domain.models.ApkAnalysisReport(
            apkPath = "/tmp/app.apk",
            apkSize = 12345,
            extractedPath = "/tmp/app",
            manifest = manifest,
            dex = dex,
            nativeLibraries = listOf("lib/armeabi-v7a/libnative.so"),
            resourcesCount = 50,
            analysisDurationMs = 1200
        )

        val text = com.voltai.doai.data.analysis.ReportGeneratorImpl().generateReport(report)
        assertTrue(text.contains("com.test.app"))
        assertTrue(text.contains("android.permission.INTERNET"))
        assertTrue(text.contains(".MainActivity"))
        assertTrue(text.contains("AES/CBC/PKCS5Padding"))
        assertTrue(text.contains("jiagu"))
        assertTrue(text.contains("libnative.so"))
    }

    @Test
    fun axmlParser_rejectsInvalidHeader() {
        val parser = AxmlParser()
        assertNull(parser.parse(ByteArray(16)))
    }
}
