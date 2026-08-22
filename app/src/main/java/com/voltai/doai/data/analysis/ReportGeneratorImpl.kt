package com.voltai.doai.data.analysis

import com.voltai.doai.domain.interfaces.ReportGenerator
import com.voltai.doai.domain.models.ApkAnalysisReport

class ReportGeneratorImpl : ReportGenerator {

    override fun generateReport(report: ApkAnalysisReport): String {
        val sb = StringBuilder()
        val m = report.manifest
        val d = report.dex

        sb.appendLine("════════════════════════════════════════════")
        sb.appendLine("  RAPPORT D'ANALYSE APK")
        sb.appendLine("════════════════════════════════════════════")
        sb.appendLine("Fichier   : ${report.apkPath}")
        sb.appendLine("Taille    : ${formatBytes(report.apkSize)}")
        sb.appendLine("Durée     : ${report.analysisDurationMs} ms")
        sb.appendLine()

        sb.appendLine("── MANIFEST ────────────────────────────────")
        sb.appendLine("Package   : ${m.packageName}")
        if (m.versionName.isNotBlank()) sb.appendLine("Version   : ${m.versionName} (code ${m.versionCode})")
        if (m.minSdk > 0 || m.targetSdk > 0) sb.appendLine("SDK       : min ${m.minSdk} / target ${m.targetSdk}")
        if (m.applicationLabel.isNotBlank()) sb.appendLine("Libellé   : ${m.applicationLabel}")
        sb.appendLine()
        sb.appendLine("Permissions (${m.permissions.size}) :")
        m.permissions.forEach { sb.appendLine("  • $it") }
        sb.appendLine()
        sb.appendLine("Activités (${m.activities.size}) :")
        m.activities.forEach { sb.appendLine("  • $it") }
        sb.appendLine()
        sb.appendLine("Services (${m.services.size}) :")
        m.services.forEach { sb.appendLine("  • $it") }
        sb.appendLine()
        sb.appendLine("Receivers (${m.receivers.size}) :")
        m.receivers.forEach { sb.appendLine("  • $it") }
        sb.appendLine()
        sb.appendLine("Providers (${m.providers.size}) :")
        m.providers.forEach { sb.appendLine("  • $it") }
        if (m.usesFeatures.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Fonctionnalités requises (${m.usesFeatures.size}) :")
            m.usesFeatures.forEach { sb.appendLine("  • $it") }
        }

        sb.appendLine()
        sb.appendLine("── CODE DEX ────────────────────────────────")
        sb.appendLine("Fichiers DEX : ${d.dexFiles.joinToString(", ").ifEmpty { "aucun" }}")
        sb.appendLine("Chaînes      : ${d.totalStrings}")
        sb.appendLine("Classes      : ${d.classesCount}")
        if (d.encryptionStrings.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⚠ Chiffrement détecté (${d.encryptionStrings.size}) :")
            d.encryptionStrings.take(40).forEach { sb.appendLine("  • $it") }
        }
        if (d.obfuscationTechniques.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⚠ Obfuscation détectée (${d.obfuscationTechniques.size}) :")
            d.obfuscationTechniques.take(20).forEach { sb.appendLine("  • $it") }
        }

        sb.appendLine()
        sb.appendLine("── BIBLIOTHÈQUES NATIVES ───────────────────")
        if (report.nativeLibraries.isEmpty()) {
            sb.appendLine("  Aucune")
        } else {
            sb.appendLine("Libraries .so (${report.nativeLibraries.size}) :")
            report.nativeLibraries.take(50).forEach { sb.appendLine("  • $it") }
        }

        sb.appendLine()
        sb.appendLine("── RESSOURCES ──────────────────────────────")
        sb.appendLine("Fichiers res/ : ${report.resourcesCount}")
        sb.appendLine("Extraction   : ${report.extractedPath}")

        sb.appendLine()
        sb.appendLine("════════════════════════════════════════════")
        sb.appendLine("Analyse terminée. Qwen peut maintenant")
        sb.appendLine("décompiler, explorer le code ou déchiffrer")
        sb.appendLine("les chaînes selon votre demande.")
        return sb.toString()
    }

    override fun generateDecryptionReport(sourceFile: String, decryptedStrings: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("════════════════════════════════════════════")
        sb.appendLine("  RAPPORT DE DÉCHIFFREMENT")
        sb.appendLine("════════════════════════════════════════════")
        sb.appendLine("Source : $sourceFile")
        sb.appendLine()
        if (decryptedStrings.isEmpty()) {
            sb.appendLine("Aucune chaîne déchiffrée détectée.")
        } else {
            sb.appendLine("Chaînes déchiffrées (${decryptedStrings.size}) :")
            decryptedStrings.forEach { sb.appendLine("  • $it") }
        }
        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.size - 1) {
            value /= 1024
            index++
        }
        return if (index == 0) "$bytes B" else String.format("%.1f %s", value, units[index])
    }
}
