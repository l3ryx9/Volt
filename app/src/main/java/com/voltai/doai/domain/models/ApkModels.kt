package com.voltai.doai.domain.models

data class ManifestInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val applicationLabel: String,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val usesFeatures: List<String>
)

data class DexInfo(
    val dexFiles: List<String>,
    val totalStrings: Int,
    val classDescriptors: List<String>,
    val encryptionStrings: List<String>,
    val obfuscationTechniques: List<String>
) {
    val classesCount: Int
        get() = classDescriptors.size
}

data class ApkAnalysisReport(
    val apkPath: String,
    val apkSize: Long,
    val extractedPath: String,
    val manifest: ManifestInfo,
    val dex: DexInfo,
    val nativeLibraries: List<String>,
    val resourcesCount: Int,
    val analysisDurationMs: Long
)
