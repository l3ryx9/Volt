package com.voltai.doai.data.analysis

import com.voltai.doai.domain.interfaces.CommandExecutor
import com.voltai.doai.domain.interfaces.Decompiler
import com.voltai.doai.domain.models.CommandResult

class DecompilerImpl(private val commandExecutor: CommandExecutor) : Decompiler {

    override fun decompileToSmali(apkPath: String, outDir: String): CommandResult {
        return executeTool("apktool d -f ${shellQuote(apkPath)} -o ${shellQuote(outDir)}", apkPath, outDir)
    }

    override fun decompileToJava(apkPath: String, outDir: String): CommandResult {
        return executeTool("jadx -d ${shellQuote(outDir)} ${shellQuote(apkPath)}", apkPath, outDir)
    }

    override fun decompileWithAndroguard(apkPath: String, outDir: String): CommandResult {
        val script = "from androguard.core.apk import APK; " +
            "from androguard.core.dex import DEX; " +
            "import os; " +
            "apk=APK('$apkPath'); " +
            "d=DEX(apk.get_dex()); " +
            "[print('CLASS ' + c.get_name()) for c in d.get_classes()]"
        return executeTool("python3 -c ${shellQuote(script)}", apkPath, outDir)
    }

    private fun executeTool(command: String, apkPath: String, outDir: String): CommandResult {
        val apk = java.io.File(apkPath)
        val output = java.io.File(outDir)
        if (!apk.isFile) {
            return CommandResult(command, "", "APK introuvable: ${apk.absolutePath}", -1, 0L)
        }
        if (!output.exists() && !output.mkdirs()) {
            return CommandResult(command, "", "Impossible de créer le dossier de sortie: ${output.absolutePath}", -1, 0L)
        }
        return commandExecutor.executeCommand(command)
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\'''") + "'"
}
