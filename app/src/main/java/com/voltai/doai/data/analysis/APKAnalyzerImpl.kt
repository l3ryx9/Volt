package com.voltai.doai.data.analysis

import com.voltai.doai.domain.interfaces.APKAnalyzer
import com.voltai.doai.domain.models.ApkAnalysisReport
import com.voltai.doai.domain.models.DexInfo
import com.voltai.doai.domain.models.ManifestInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withContext
import java.io.File

class APKAnalyzerImpl(private val archiveManager: com.voltai.doai.domain.interfaces.ArchiveManager) : APKAnalyzer {

    private val axmlParser = AxmlParser()
    private val dexParser = DexParser()

    override fun extractApk(apkPath: String): String {
        val baseName = File(apkPath).nameWithoutExtension
        val dest = File(File(apkPath).parentFile ?: File("/tmp"), "$baseName-analysis")
        dest.mkdirs()
        val last = kotlinx.coroutines.runBlocking {
            archiveManager.extractArchive(apkPath, dest.absolutePath).last()
        }
        if (last.error != null) throw IllegalStateException(last.error)
        return dest.absolutePath
    }

    override suspend fun analyzeApk(apkPath: String): ApkAnalysisReport {
        val start = System.currentTimeMillis()
        return withContext(Dispatchers.Default) {
            val extracted = extractApk(apkPath)
            val manifestNode = parseManifest(File(extracted, "AndroidManifest.xml"))
            val manifest = buildManifestInfo(manifestNode)
            val dexInfo = parseDexFiles(extracted)
            val nativeLibs = findNativeLibraries(extracted)
            val resourcesCount = countResources(extracted)

            ApkAnalysisReport(
                apkPath = apkPath,
                apkSize = File(apkPath).length(),
                extractedPath = extracted,
                manifest = manifest,
                dex = dexInfo,
                nativeLibraries = nativeLibs,
                resourcesCount = resourcesCount,
                analysisDurationMs = System.currentTimeMillis() - start
            )
        }
    }

    private fun parseManifest(file: File): AxmlNode? {
        if (!file.exists()) return null
        return try {
            axmlParser.parse(file.readBytes())
        } catch (e: Exception) {
            null
        }
    }

    private fun buildManifestInfo(root: AxmlNode?): ManifestInfo {
        if (root == null) {
            return ManifestInfo("inconnu", "", 0, 0, 0, "", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        val receivers = mutableListOf<String>()
        val providers = mutableListOf<String>()
        val features = mutableListOf<String>()

        fun collect(node: AxmlNode) {
            when (node.name) {
                "uses-permission" -> node.attributes["android:name"]?.let { permissions.add(it) }
                "uses-feature" -> node.attributes["android:name"]?.let { features.add(it) }
                "activity" -> node.attributes["android:name"]?.let { activities.add(it) }
                "service" -> node.attributes["android:name"]?.let { services.add(it) }
                "receiver" -> node.attributes["android:name"]?.let { receivers.add(it) }
                "provider" -> node.attributes["android:name"]?.let { providers.add(it) }
            }
            node.children.forEach { collect(it) }
        }
        root.children.forEach { collect(it) }

        val minSdk = root.attributes["android:minSdkVersion"]?.toIntOrNull()
            ?: root.children.firstOrNull { it.name == "uses-sdk" }?.attributes?.get("android:minSdkVersion")?.toIntOrNull() ?: 0
        val targetSdk = root.attributes["android:targetSdkVersion"]?.toIntOrNull()
            ?: root.children.firstOrNull { it.name == "uses-sdk" }?.attributes?.get("android:targetSdkVersion")?.toIntOrNull() ?: 0

        val appNode = root.children.firstOrNull { it.name == "application" }

        return ManifestInfo(
            packageName = root.attributes["package"] ?: "inconnu",
            versionName = root.attributes["android:versionName"] ?: "",
            versionCode = root.attributes["android:versionCode"]?.toLongOrNull() ?: 0,
            minSdk = minSdk,
            targetSdk = targetSdk,
            applicationLabel = appNode?.attributes?.get("android:label") ?: "",
            permissions = permissions.distinct(),
            activities = activities.distinct(),
            services = services.distinct(),
            receivers = receivers.distinct(),
            providers = providers.distinct(),
            usesFeatures = features.distinct()
        )
    }

    private fun parseDexFiles(extracted: String): DexInfo {
        val dexFiles = mutableListOf<String>()
        var totalStrings = 0
        val classDescriptors = mutableListOf<String>()
        val encryptionStrings = mutableListOf<String>()
        val obfuscation = LinkedHashSet<String>()

        File(extracted).listFiles()?.filter { it.name.matches(Regex("classes\\d*\\.dex")) }?.sortedBy { it.name }
            ?.forEach { dexFile ->
                dexFiles.add(dexFile.name)
                val result = dexParser.parse(dexFile.readBytes())
                totalStrings += result.totalStrings
                classDescriptors.addAll(result.classDescriptors)
                encryptionStrings.addAll(result.encryptionStrings)
                obfuscation.addAll(result.obfuscationTechniques)
            }

        return DexInfo(
            dexFiles = dexFiles,
            totalStrings = totalStrings,
            classDescriptors = classDescriptors.distinct().take(500),
            encryptionStrings = encryptionStrings.distinct().take(200),
            obfuscationTechniques = obfuscation.toList()
        )
    }

    private fun findNativeLibraries(extracted: String): List<String> {
        val libDir = File(extracted, "lib")
        if (!libDir.exists()) return emptyList()
        val libs = mutableListOf<String>()
        var count = 0
        libDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".so")) {
                libs.add(file.path.removePrefix(extracted).trimStart('/'))
                count++
                if (count >= 200) return@forEach
            }
        }
        return libs
    }

    private fun countResources(extracted: String): Int {
        val resDir = File(extracted, "res")
        if (!resDir.exists()) return 0
        var count = 0
        resDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                count++
                if (count >= 50000) return@forEach
            }
        }
        return count
    }
}
