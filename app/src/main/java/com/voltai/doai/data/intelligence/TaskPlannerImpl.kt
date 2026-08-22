package com.voltai.doai.data.intelligence

import com.voltai.doai.domain.interfaces.Complexity
import com.voltai.doai.domain.interfaces.Priority
import com.voltai.doai.domain.interfaces.TaskPlanner
import com.voltai.doai.domain.interfaces.TaskStatus
import com.voltai.doai.domain.models.Intent
import com.voltai.doai.domain.models.Task
import com.voltai.doai.domain.models.TaskStep
import java.util.UUID

class TaskPlannerImpl : TaskPlanner {

    override fun createPlan(intent: Intent): Task {
        val steps = when (intent.action) {
            IntentAnalyzerImpl.ACTION_ANALYZE_APK -> analyzeApkPlan(intent)
            IntentAnalyzerImpl.ACTION_DECOMPILE -> decompilePlan(intent)
            IntentAnalyzerImpl.ACTION_DECRYPT -> decryptPlan(intent)
            IntentAnalyzerImpl.ACTION_REVERSE_ENGINEER -> reverseEngineeringPlan(intent)
            IntentAnalyzerImpl.ACTION_EXTRACT_ARCHIVE -> extractArchivePlan(intent)
            IntentAnalyzerImpl.ACTION_CREATE_ARCHIVE -> createArchivePlan(intent)
            IntentAnalyzerImpl.ACTION_MODIFY_CODE -> modifyCodePlan(intent)
            IntentAnalyzerImpl.ACTION_EXPLAIN_CODE -> explainCodePlan(intent)
            IntentAnalyzerImpl.ACTION_SEARCH_CODE -> searchCodePlan(intent)
            IntentAnalyzerImpl.ACTION_INSTALL_PACKAGE -> installPackagePlan(intent)
            IntentAnalyzerImpl.ACTION_UPDATE_PACKAGES -> updatePackagesPlan()
            IntentAnalyzerImpl.ACTION_LIST_FILES -> listFilesPlan()
            IntentAnalyzerImpl.ACTION_DOWNLOAD -> downloadPlan(intent)
            IntentAnalyzerImpl.ACTION_GIT_CLONE -> gitClonePlan(intent)
            else -> genericPlan(intent)
        }
        val task = Task(
            id = UUID.randomUUID().toString(),
            description = intent.action,
            intent = intent,
            steps = steps,
            estimatedDuration = 0L,
            priority = priorityFor(intent.complexity),
            status = TaskStatus.PENDING
        )
        val optimized = optimizePlan(task)
        return optimized.copy(estimatedDuration = estimateExecutionTime(optimized))
    }

    override fun addStep(task: Task, step: TaskStep): Task {
        return task.copy(steps = task.steps + step)
    }

    override fun validatePlan(task: Task): Boolean {
        if (task.steps.isEmpty()) return false
        val ids = task.steps.map { it.id }.toSet()
        return task.steps.all { step ->
            step.tool.isNotBlank() &&
                step.command.isNotBlank() &&
                step.dependencies.all { it in ids }
        }
    }

    override fun optimizePlan(task: Task): Task {
        val byCommand = LinkedHashMap<String, TaskStep>()
        task.steps.forEach { step -> byCommand[step.command.trim()] = step }
        val steps = byCommand.values.toList()
        val idByStep = steps.mapIndexed { index, step -> step.id to index }.toMap()
        return task.copy(
            steps = steps.map { step ->
                step.copy(
                    dependencies = step.dependencies
                        .mapNotNull { idByStep[it]?.let { idx -> steps[idx].id } }
                        .distinct()
                )
            }
        )
    }

    override fun estimateExecutionTime(task: Task): Long {
        val base = when (task.intent.complexity) {
            Complexity.SIMPLE -> 5_000L
            Complexity.MEDIUM -> 15_000L
            Complexity.COMPLEX -> 45_000L
            Complexity.EXPERT -> 120_000L
        }
        return base * task.steps.size.coerceAtLeast(1)
    }

    private fun analyzeApkPlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        return plan(
            StepSpec("apktool", "apktool d -f $target -o output/apktool", "Décodage des ressources APK", 600L),
            StepSpec("jadx", "jadx --threads-count 1 -d output/java $target", "Décompilation vers Java", 600L),
            StepSpec("androguard", "androguard analyze -i $target", "Analyse du manifeste et des permissions", 180L),
            StepSpec("ai", "echo Analyse des résultats (manifeste, resources, dex, smali)", "Synthèse et analyse IA", 120L, deps = listOf(0, 1, 2))
        )
    }

    private fun decompilePlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        return plan(
            StepSpec("jadx", "jadx --threads-count 1 -d output/java $target", "Décompilation des DEX vers Java", 600L),
            StepSpec("baksmali", "baksmali d $target -o output/smali", "Décompilation vers Smali", 180L)
        )
    }

    private fun decryptPlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        val smaliOut = "output/apktool/smali"
        return plan(
            StepSpec("apktool", "apktool d -f $target -o output/apktool", "Décodage de l'APK pour obtenir les smali", 600L),
            StepSpec("paranoid", "paranoid deobfuscate $smaliOut", "Désobfuscation Paranoid/LSParanoid des smali", 300L, deps = listOf(0)),
            StepSpec("dalivm", "dalivm $target \"Lcom/app/Secret;->decrypt\" --limit 10", "Exécution de la méthode de déchiffrement via l'émulateur Dalvik", 300L, deps = listOf(0)),
            StepSpec("jadx-string-decrypt", "jadx --threads-count 1 -d output/java $target -P string-decrypt:enabled=true", "Décompilation Java avec plugin string-decrypt", 600L, deps = listOf(0)),
            StepSpec("proguard-retrace", "proguard-retrace mapping.txt trace.txt", "Retraçage ProGuard des traces (mapping)", 120L, deps = listOf(0))
        )
    }

    /**
     * Workflow complet de reverse engineering (specs embarquées dans
     * assets/decrypt/) : 16 agents produisent chacun un fichier dans
     * output/analysis/, la synthèse finale (outil ai) est rédigée par Qwen.
     */
    private fun reverseEngineeringPlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        return plan(
            StepSpec("7z", "echo \"Reconnaissance : inventaire APK $target\"", "Reconnaissance initiale (reconnaissance.json)", 60L),
            StepSpec("apktool", "apktool d -f $target -o output/apktool", "Décodage des ressources et smali", 600L, deps = listOf(0)),
            StepSpec("jadx", "echo \"Cartographie des classes\"", "Cartographie des classes (classes.json)", 60L, deps = listOf(1)),
            StepSpec("androguard", "echo \"Analyse des méthodes\"", "Analyse des méthodes (methods.json)", 60L, deps = listOf(2)),
            StepSpec("python", "echo \"Désobfuscation des noms (paranoid, jadx-string-decrypt, dalivm)\"", "Désobfuscation et noms (renames.json)", 120L, deps = listOf(2, 3)),
            StepSpec("python", "echo \"Dépendances internes et externes\"", "Dépendances (dependencies.json)", 60L, deps = listOf(2)),
            StepSpec("python", "echo \"Identification crypto (AES/RSA/Base64/XOR...)\"", "Cryptographie (crypto.json)", 120L, deps = listOf(2, 3, 5)),
            StepSpec("python", "echo \"Classification fonctionnelle\"", "Classification fonctionnelle (functions.json)", 60L, deps = listOf(2, 3)),
            StepSpec("androguard", "echo \"Points d'entrée (manifeste, activities, services)\"", "Points d'entrée (entrypoints.json)", 60L, deps = listOf(1)),
            StepSpec("python", "echo \"Chaînes importantes et désobfuscation\"", "Chaînes importantes (strings.json)", 120L, deps = listOf(4, 6)),
            StepSpec("python", "echo \"Reconstruction des flux de données\"", "Flux de données (flows.json)", 120L, deps = listOf(2, 3, 6)),
            StepSpec("python", "echo \"Détection des similarités et wrappers\"", "Similarités (similarity.json)", 60L, deps = listOf(2, 3)),
            StepSpec("baksmali", "echo \"Analyse du bytecode smali complexe\"", "Smali complexe (smali.json)", 120L, deps = listOf(1)),
            StepSpec("python", "echo \"Analyse native JNI (lib/*.so)\"", "Natif/JNI (native.json)", 120L, deps = listOf(1, 2)),
            StepSpec("python", "echo \"Validation de la clé de déchiffrement\"", "Clé de déchiffrement (sections 8/16)", 120L, deps = listOf(6, 10)),
            StepSpec("ai", "echo \"Synthèse du rapport complet de reverse engineering\"", "Synthèse et rapport final (report.md)", 180L, deps = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14))
        )
    }

    private fun extractArchivePlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        val tool = intent.tools.firstOrNull() ?: "unzip"
        val command = when (tool) {
            "unrar" -> "unrar x $target"
            "7z" -> "7z x $target"
            "tar" -> "tar -xvf $target"
            else -> "unzip -o $target"
        }
        return plan(StepSpec(tool, command, "Extraction de l'archive $target", 180L))
    }

    private fun createArchivePlan(intent: Intent): List<TaskStep> {
        val target = intent.target.ifBlank { "archive.zip" }
        val sources = if (intent.files.isEmpty()) "." else intent.files.joinToString(" ")
        return plan(StepSpec("zip", "zip -r $target $sources", "Création de l'archive $target", 180L))
    }

    private fun modifyCodePlan(intent: Intent): List<TaskStep> {
        val files = intent.files.joinToString(" ")
        val target = files.ifBlank { intent.target }
        return plan(
            StepSpec("sed", "echo Préparation de la modification sur $target", "Préparation de la modification", 30L),
            StepSpec("python", "python3 -c \"print('Modification de $target')\"", "Application de la modification", 120L, deps = listOf(0))
        )
    }

    private fun explainCodePlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        return plan(
            StepSpec("tree-sitter", "echo Parsing AST de $target", "Construction de l'AST", 60L),
            StepSpec("ai", "echo Explication de l'architecture et des symboles", "Explication du code", 120L, deps = listOf(0))
        )
    }

    private fun searchCodePlan(intent: Intent): List<TaskStep> {
        return plan(StepSpec("grep", "grep -rn \"${intent.target}\" .", "Recherche du motif dans le code", 120L))
    }

    private fun installPackagePlan(intent: Intent): List<TaskStep> {
        return plan(StepSpec("pkg", "pkg install -y ${intent.target}", "Installation du package ${intent.target}", 300L))
    }

    private fun updatePackagesPlan(): List<TaskStep> {
        return plan(StepSpec("pkg", "pkg update -y && pkg upgrade -y", "Mise à jour des packages", 600L))
    }

    private fun listFilesPlan(): List<TaskStep> {
        return plan(StepSpec("find", "find . -maxdepth 3 -type f | head -200", "Liste des fichiers", 60L))
    }

    private fun downloadPlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        val tool = intent.tools.firstOrNull()
        return when (tool) {
            "git" -> plan(StepSpec("git", "git clone $target", "Clonage du dépôt $target", 300L))
            "openssl" -> plan(StepSpec("openssl", "openssl enc -d -aes-256-cbc -in $target", "Déchiffrement avec OpenSSL", 120L))
            else -> plan(StepSpec("curl", "curl -L -o output/telechargement $target", "Téléchargement de $target via curl", 300L))
        }
    }

    private fun gitClonePlan(intent: Intent): List<TaskStep> {
        return plan(StepSpec("git", "git clone ${intent.target}", "Clonage du dépôt ${intent.target}", 300L))
    }

    private fun genericPlan(intent: Intent): List<TaskStep> {
        val target = intent.target
        val command = if (target.isNotBlank()) {
            when (intent.tools.firstOrNull()) {
                "pkg", "apt" -> "pkg install -y $target"
                "wget" -> "wget $target"
                "git" -> "git clone $target"
                "grep" -> "grep -rn \"$target\" ."
                else -> target
            }
        } else {
            intent.action
        }
        return plan(StepSpec("shell", command, "Exécution de la commande", 180L))
    }

    private fun priorityFor(complexity: Complexity): Priority {
        return when (complexity) {
            Complexity.SIMPLE -> Priority.LOW
            Complexity.MEDIUM -> Priority.MEDIUM
            Complexity.COMPLEX -> Priority.HIGH
            Complexity.EXPERT -> Priority.CRITICAL
        }
    }

    private fun plan(vararg specs: StepSpec): List<TaskStep> {
        return specs.mapIndexed { index, spec ->
            TaskStep(
                id = "step-$index",
                description = spec.description,
                tool = spec.tool,
                command = spec.command,
                dependencies = spec.deps.map { "step-$it" },
                expectedOutput = null,
                timeout = spec.timeout
            )
        }
    }

    private data class StepSpec(
        val tool: String,
        val command: String,
        val description: String,
        val timeout: Long,
        val deps: List<Int> = emptyList()
    )
}
