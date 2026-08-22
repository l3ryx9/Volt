package com.voltai.doai.data.code

import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.SymbolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodeComprehensionTest {

    private val detector = LanguageDetectorImpl()
    private val resolver = SymbolResolverImpl()
    private val indexer = CodeIndexerImpl(resolver)
    private val deps = DependencyAnalyzerImpl()

    // ------------------------------------------------------------------
    // LanguageDetector
    // ------------------------------------------------------------------

    @Test
    fun detector_identifiesLanguagesByExtension() {
        assertEquals(CodeLanguage.KOTLIN, detector.detectLanguage("Main.kt"))
        assertEquals(CodeLanguage.JAVA, detector.detectLanguage("Auth.java"))
        assertEquals(CodeLanguage.PYTHON, detector.detectLanguage("script.py"))
        assertEquals(CodeLanguage.C, detector.detectLanguage("kernel.c"))
        assertEquals(CodeLanguage.CPP, detector.detectLanguage("main.cpp"))
        assertEquals(CodeLanguage.RUST, detector.detectLanguage("lib.rs"))
        assertEquals(CodeLanguage.GO, detector.detectLanguage("main.go"))
        assertEquals(CodeLanguage.JAVASCRIPT, detector.detectLanguage("app.js"))
        assertEquals(CodeLanguage.SMALI, detector.detectLanguage("MainActivity.smali"))
        assertEquals(CodeLanguage.XML, detector.detectLanguage("AndroidManifest.xml"))
        assertEquals(CodeLanguage.JSON, detector.detectLanguage("config.json"))
    }

    @Test
    fun detector_identifiesLanguagesByContent() {
        val java = "package com.example;\npublic class Main { void run() {} }"
        assertEquals(CodeLanguage.JAVA, detector.detectLanguage("unknown.txt", java))

        val kotlin = "package com.example\nclass Main { fun run() {} }"
        assertEquals(CodeLanguage.KOTLIN, detector.detectLanguage("unknown.txt", kotlin))

        val python = "import os\ndef main():\n    pass"
        assertEquals(CodeLanguage.PYTHON, detector.detectLanguage("unknown.txt", python))

        val xml = "<?xml version=\"1.0\"?>\n<manifest></manifest>"
        assertEquals(CodeLanguage.XML, detector.detectLanguage("unknown.txt", xml))
    }

    // ------------------------------------------------------------------
    // Extraction de symboles (repli AST sur JVM)
    // ------------------------------------------------------------------

    @Test
    fun extractor_findsKotlinClassMethodsAndFields() {
        val source = """
            package com.example

            class BankAccount(private val owner: String) {
                private var balance: Double = 0.0

                fun deposit(amount: Double) {
                    balance += amount
                }

                fun balanceOf(): Double = balance
            }
        """.trimIndent()

        val symbols = resolver.extractSymbols(source, CodeLanguage.KOTLIN, "Bank.kt")

        assertTrue("classe", symbols.any { it.name == "BankAccount" && it.type == SymbolType.CLASS })
        assertTrue("méthode deposit", symbols.any { it.name == "deposit" && it.type == SymbolType.FUNCTION })
        assertTrue("propriété balance", symbols.any { it.name == "balance" && it.type == SymbolType.PROPERTY })
        assertTrue("fonction balanceOf", symbols.any { it.name == "balanceOf" && it.type == SymbolType.FUNCTION })
    }

    @Test
    fun extractor_findsJavaClassInterfaceAndMethod() {
        val source = """
            package com.example;

            public interface Payable {
                void pay(double amount);
            }

            public class Invoice implements Payable {
                private double total = 0.0;

                public Invoice() {
                }

                @Override
                public void pay(double amount) {
                    total += amount;
                }

                public double total() {
                    return total;
                }
            }
        """.trimIndent()

        val symbols = resolver.extractSymbols(source, CodeLanguage.JAVA, "Invoice.java")

        assertTrue(symbols.any { it.name == "Payable" && it.type == SymbolType.INTERFACE })
        assertTrue(symbols.any { it.name == "Invoice" && it.type == SymbolType.CLASS })
        assertTrue(symbols.any { it.name == "pay" && it.type == SymbolType.METHOD })
        assertTrue(symbols.any { it.name == "total" && it.type == SymbolType.FIELD })
    }

    @Test
    fun extractor_findsPythonFunctionsAndClasses() {
        val source = """
            import math

            CONFIG = "dev"

            class Parser:
                def parse(self, text):
                    return text

            def main():
                p = Parser()
        """.trimIndent()

        val symbols = resolver.extractSymbols(source, CodeLanguage.PYTHON, "main.py")

        assertTrue(symbols.any { it.name == "Parser" && it.type == SymbolType.CLASS })
        assertTrue(symbols.any { it.name == "parse" && it.type == SymbolType.FUNCTION })
        assertTrue(symbols.any { it.name == "main" && it.type == SymbolType.FUNCTION })
        assertTrue(symbols.any { it.name == "math" && it.type == SymbolType.IMPORT })
    }

    @Test
    fun extractor_findsCSmaliAndRust() {
        val c = "#include <stdio.h>\nint add(int a, int b) {\n    return a + b;\n}"
        val cSymbols = resolver.extractSymbols(c, CodeLanguage.C, "calc.c")
        assertTrue(cSymbols.any { it.name == "add" && it.type == SymbolType.FUNCTION })

        val smali = """
            .class public Lnet/example/Check;
            .field public static flag:I
            .method public static verify()Z
                const/4 v0, 0x1
                return v0
            .end method
        """.trimIndent()
        val smaliSymbols = resolver.extractSymbols(smali, CodeLanguage.SMALI, "Check.smali")
        assertTrue(smaliSymbols.any { it.name == "Check" && it.type == SymbolType.CLASS })
        assertTrue(smaliSymbols.any { it.name == "verify" && it.type == SymbolType.METHOD })
        assertTrue(smaliSymbols.any { it.name == "flag" && it.type == SymbolType.FIELD })

        val rust = "struct Point { x: f64, y: f64 }\nimpl Point {\n    fn distance(&self) -> f64 { 1.0 }\n}"
        val rustSymbols = resolver.extractSymbols(rust, CodeLanguage.RUST, "point.rs")
        assertTrue(rustSymbols.any { it.name == "Point" && it.type == SymbolType.STRUCT })
        assertTrue(rustSymbols.any { it.name == "distance" && it.type == SymbolType.FUNCTION })
    }

    @Test
    fun extractor_findsXmlElementsAndJsonKeys() {
        val xml = "<manifest package=\"com.example\"><application><activity android:name=\".Main\"/></application></manifest>"
        val xmlSymbols = resolver.extractSymbols(xml, CodeLanguage.XML, "AndroidManifest.xml")
        assertTrue(xmlSymbols.any { it.name == "manifest" && it.type == SymbolType.ELEMENT })
        assertTrue(xmlSymbols.any { it.name == "activity" && it.type == SymbolType.ELEMENT })
        assertTrue(xmlSymbols.any { it.name == "package" && it.type == SymbolType.PROPERTY })

        val json = "{\n  \"version\": \"1.0\",\n  \"features\": [\"a\", \"b\"]\n}"
        val jsonSymbols = resolver.extractSymbols(json, CodeLanguage.JSON, "config.json")
        assertTrue(jsonSymbols.any { it.name == "version" && it.type == SymbolType.PROPERTY })
        assertTrue(jsonSymbols.any { it.name == "features" && it.type == SymbolType.PROPERTY })
    }

    @Test
    fun extractor_marksObfuscatedShortNames() {
        val source = "class a { fun f(x: Int): Int = x * 2 }"
        val symbols = resolver.extractSymbols(source, CodeLanguage.KOTLIN, "a.kt")
        val a = symbols.firstOrNull { it.name == "a" }
        assertNotNull("classe a détectée", a)
        assertTrue("classe courte = obfusquée", a!!.obfuscated)
        val f = symbols.firstOrNull { it.name == "f" }
        assertNotNull("fonction f détectée", f)
        assertTrue("fonction courte = obfusquée", f!!.obfuscated)

        val longName = "class Authenticator { fun validate(): Boolean = true }"
        val ok = resolver.extractSymbols(longName, CodeLanguage.KOTLIN, "Auth.kt")
        assertFalse("nom long non obfusqué", ok.first { it.name == "Authenticator" }.obfuscated)
    }

    // ------------------------------------------------------------------
    // SymbolResolver
    // ------------------------------------------------------------------

    @Test
    fun resolver_findsFunctionAndClass() {
        val source = """
            package com.example

            class AuthService(private val token: String) {
                fun getToken(): String = token
                private fun refresh(expired: Boolean): String = "new"
            }
        """.trimIndent()

        val fn = resolver.findFunction(source, CodeLanguage.KOTLIN, "getToken", "Auth.kt")
        assertNotNull(fn)
        assertEquals("getToken", fn!!.name)
        assertEquals("AuthService", fn.parentName)

        val cls = resolver.findClass(source, CodeLanguage.KOTLIN, "AuthService", "Auth.kt")
        assertNotNull(cls)
        assertEquals("AuthService", cls!!.name)
    }

    @Test
    fun resolver_explainClass_showsInheritanceFieldsAndMethods() {
        val source = """
            open class Base(val id: Int)

            class Derived(id: Int) : Base(id) {
                private val name: String = "x"
                fun describe(): String = "n=${'$'}name"
            }
        """.trimIndent()

        val explain = resolver.explainClass(source, CodeLanguage.KOTLIN, "Derived", "Derived.kt")
        assertTrue(explain.contains("Derived"))
        assertTrue(explain.contains("Base"))
        assertTrue(explain.contains("name"))
        assertTrue(explain.contains("describe"))
    }

    @Test
    fun resolver_explainFile_listsSymbolsImportsAndDependencies() {
        val source = """
            package com.example

            import kotlin.math.sqrt

            class Util {
                fun compute(x: Double): Double = sqrt(x)
            }
        """.trimIndent()

        val explain = resolver.explainFile(source, CodeLanguage.KOTLIN, "Util.kt")
        assertTrue(explain.contains("Kotlin"))
        assertTrue(explain.contains("Util"))
        assertTrue(explain.contains("compute"))
        assertTrue(explain.contains("sqrt"))
    }

    @Test
    fun resolver_unknownClass_returnsNotFoundMessage() {
        val explain = resolver.explainClass("class X {}", CodeLanguage.KOTLIN, "Missing")
        assertTrue(explain.contains("introuvable"))
    }

    // ------------------------------------------------------------------
    // CodeIndexer
    // ------------------------------------------------------------------

    @Test
    fun indexer_indexesFile() {
        val f = File.createTempFile("sample", ".kt")
        f.writeText("class Sample { fun run() {} }")

        val index = indexer.indexFile(f)
        assertNotNull(index)
        assertEquals(CodeLanguage.KOTLIN, index!!.language)
        assertEquals("Sample", index.classes.single().name)
        assertTrue(index.functions.any { it.name == "run" })
        f.delete()
    }

    @Test
    fun indexer_indexesDirectoryByLanguage() {
        val dir = File.createTempFile("code", "")
        dir.delete()
        dir.mkdirs()
        File(dir, "Main.kt").writeText("class Main { fun go() {} }")
        File(dir, "Main.java").writeText("class JavaMain { void go() {} }")
        File(dir, "notes.txt").writeText("ignore me")

        val index = indexer.indexDirectory(dir)
        assertEquals(2, index.size)
        assertEquals(1, index[CodeLanguage.KOTLIN]!!.fileCount)
        assertEquals(1, index[CodeLanguage.JAVA]!!.fileCount)
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    // ------------------------------------------------------------------
    // DependencyAnalyzer
    // ------------------------------------------------------------------

    @Test
    fun deps_findsImportsCallsAndInheritance() {
        val source = """
            package com.example

            import kotlinx.coroutines.launch
            import java.util.List

            open class Animal {
                fun eat() {}
            }

            class Dog : Animal() {
                fun bark() {
                    eat()
                    println("woof")
                }
            }
        """.trimIndent()

        val dependencies = deps.analyzeDependencies(source, CodeLanguage.KOTLIN, "Dog.kt")
        assertTrue(dependencies.any { it.type.name == "IMPORT" && it.to.contains("kotlinx") })
        assertTrue(dependencies.any { it.type.name == "INHERITANCE" && it.to == "Animal" })
        assertTrue(dependencies.any { it.type.name == "CALL" && it.to == "eat" })
    }

    @Test
    fun deps_detectsArchitectureCycles() {
        val files = linkedMapOf(
            "Alpha.kt" to "class Alpha { fun run() = Beta() }",
            "Beta.kt" to "class Beta { fun run() = Alpha() }",
            "Gamma.kt" to "class Gamma { fun run() = 1 }"
        )

        val arch = deps.analyzeArchitecture(files)
        assertTrue(arch.cycles.any { cycle -> cycle.any { it == "Alpha" } && cycle.any { it == "Beta" } })
        assertTrue(arch.description.isNotBlank())
    }

    @Test
    fun deps_analyzeError_categorizesMessages() {
        val source = "class A { fun go() {} }"

        val unresolved = deps.analyzeError(source, CodeLanguage.KOTLIN, "Unresolved reference: missing", "A.kt")
        assertEquals("Compilation — symbole introuvable", unresolved.category)

        val npe = deps.analyzeError(source, CodeLanguage.JAVA, "NullPointerException at A.java:12", "A.java")
        assertEquals("Exécution — pointeur nul", npe.category)
        assertEquals("Critique", npe.severity)

        val syntax = deps.analyzeError(source, CodeLanguage.PYTHON, "SyntaxError: invalid syntax", "a.py")
        assertEquals("Syntaxe", syntax.category)

        val unknown = deps.analyzeError(source, CodeLanguage.KOTLIN, "weird custom error 42", "a.kt")
        assertEquals("Non classifiée", unknown.category)
    }

    @Test
    fun deps_analyzeError_referencesExistingSymbol() {
        val source = "class AuthService { fun getToken(): String = \"t\" }"
        val result = deps.analyzeError(
            source, CodeLanguage.KOTLIN,
            "Unresolved reference: getToken at AuthService.kt:3", "AuthService.kt"
        )
        assertTrue(result.suggestedFix.contains("getToken"))
        assertTrue(result.hints.any { it.contains("getToken") })
        assertTrue(result.hints.any { it.contains("AuthService.kt") })
    }

    @Test
    fun deps_obfuscationCatalog_isComplete() {
        val techniques = deps.obfuscationTechniques()
        val names = techniques.map { it.name }
        assertTrue(names.any { it.contains("Renommage") })
        assertTrue(names.any { it.contains("Encodage") })
        assertTrue(names.any { it.contains("Chiffrement") })
        assertTrue(names.any { it.contains("Contrôle de flux") })
        assertTrue(names.any { it.contains("réflexif") })
        assertTrue(names.any { it.contains("Nativisation") })
        assertTrue(names.any { it.contains("Minification") })
        assertTrue(names.any { it.contains("Smali") })
    }

    @Test
    fun deps_detectObfuscation_flagsShortNames() {
        val source = "class a { fun f(x: Int): Int = x * 2 }"
        val detected = deps.detectObfuscation(source, CodeLanguage.KOTLIN)
        assertTrue(detected.any { it.name.contains("Renommage") })

        val clean = "class Authenticator { fun validate(token: String): Boolean = true }"
        val cleanDetected = deps.detectObfuscation(clean, CodeLanguage.KOTLIN)
        assertFalse(cleanDetected.any { it.name.contains("Renommage") })
    }

    @Test
    fun deps_deobfuscate_decodesBase64AndHex() {
        val payload = java.util.Base64.getEncoder().encodeToString("hello-qwen-0123456789abcdef".toByteArray())
        val source = "fun decode() { val s = \"$payload\" }"
        val result = deps.deobfuscate(source, CodeLanguage.KOTLIN)
        assertTrue(result.contains("hello-qwen-0123456789abcdef"))

        val hexSource = "fun bytes() { val b = byteArrayOf(0x68, 0x65, 0x6c, 0x6c, 0x6f) }"
        val hexResult = deps.deobfuscate(hexSource, CodeLanguage.KOTLIN)
        assertTrue(hexResult.contains("hello"))
    }

    @Test
    fun deps_deobfuscate_cleanSource_noTechniques() {
        val source = "class Authenticator { fun validate(token: String): Boolean = true }"
        val result = deps.deobfuscate(source, CodeLanguage.KOTLIN)
        assertTrue(result.contains("Aucune technique"))
    }
}
