package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ApkAnalysisReport
import com.voltai.doai.domain.models.CommandResult

interface APKAnalyzer {
    suspend fun analyzeApk(apkPath: String): ApkAnalysisReport
    fun extractApk(apkPath: String): String
}

interface Decompiler {
    fun decompileToSmali(apkPath: String, outDir: String): CommandResult
    fun decompileToJava(apkPath: String, outDir: String): CommandResult
    fun decompileWithAndroguard(apkPath: String, outDir: String): CommandResult
}

interface CodeExplorer {
    fun findClasses(root: String, query: String? = null, maxResults: Int = 100): List<String>
    fun findMethods(root: String, query: String? = null, maxResults: Int = 100): List<String>
    fun findStrings(root: String, query: String, maxResults: Int = 100): List<String>
}

interface ReportGenerator {
    fun generateReport(report: ApkAnalysisReport): String
    fun generateDecryptionReport(sourceFile: String, decryptedStrings: List<String>): String
}
