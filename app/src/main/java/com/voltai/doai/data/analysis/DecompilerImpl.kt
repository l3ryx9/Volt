package com.voltai.doai.data.analysis

import com.voltai.doai.domain.interfaces.CommandExecutor
import com.voltai.doai.domain.interfaces.Decompiler
import com.voltai.doai.domain.models.CommandResult

class DecompilerImpl(private val commandExecutor: CommandExecutor) : Decompiler {

    override fun decompileToSmali(apkPath: String, outDir: String): CommandResult {
        return commandExecutor.executeCommand("apktool d -f \"$apkPath\" -o \"$outDir\"")
    }

    override fun decompileToJava(apkPath: String, outDir: String): CommandResult {
        return commandExecutor.executeCommand("jadx -d \"$outDir\" \"$apkPath\"")
    }

    override fun decompileWithAndroguard(apkPath: String, outDir: String): CommandResult {
        val script = "from androguard.core.apk import APK; " +
            "from androguard.core.dex import DEX; " +
            "import os; " +
            "apk=APK('$apkPath'); " +
            "d=DEX(apk.get_dex()); " +
            "[print('CLASS ' + c.get_name()) for c in d.get_classes()]"
        return commandExecutor.executeCommand("python3 -c \"$script\"")
    }
}
