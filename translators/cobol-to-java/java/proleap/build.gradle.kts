// Builds ProLeap COBOL Parser 4.0.0 (MIT) from the git submodule at third_party/proleap-cobol-parser.
// ProLeap is not published to Maven Central, so we generate its ANTLR parser and compile it here.
plugins { `java-library` }

val proleapSrc = rootProject.file("../../../third_party/proleap-cobol-parser/src/main")
val antlrTool: Configuration by configurations.creating

dependencies {
    antlrTool("org.antlr:antlr4:4.7.2")
    api("org.antlr:antlr4-runtime:4.7.2")
    implementation("org.slf4j:slf4j-api:2.0.9")
}

val generatedDir = layout.buildDirectory.dir("generated/antlr")
val patchedGrammarDir = layout.buildDirectory.dir("grammar")

// Grammar extensions, applied to a copy (the submodule stays pristine). Each anchor must occur
// exactly once, so a ProLeap update that moves it fails the build instead of silently dropping
// the extension. The ASG is unchanged: the translator reads the extra words from the parse tree.
val grammarExtensions = mapOf(
    // EXIT PARAGRAPH / SECTION / PERFORM [CYCLE] (COBOL 2002; PARAGRAPH and CYCLE lex as words).
    "exitStatement\n   : EXIT PROGRAM?\n" to "exitStatement\n   : EXIT (PROGRAM | SECTION | PERFORM cobolWord? | cobolWord)?\n",
    // Level-88 VALUE ... WHEN SET TO FALSE IS literal (COBOL 2002).
    "dataValueClause\n   : ((VALUE | VALUES) (IS | ARE)?)? dataValueInterval (COMMACHAR? dataValueInterval)*\n" to
        "dataValueClause\n   : ((VALUE | VALUES) (IS | ARE)?)? dataValueInterval (COMMACHAR? dataValueInterval)* (WHEN? SET TO FALSE IS? literal)?\n",
    // INITIALIZE ... [WITH FILLER] [ALL TO VALUE] [REPLACING ...] [THEN TO DEFAULT] (COBOL 2002).
    "initializeStatement\n   : INITIALIZE identifier+ initializeReplacingPhrase?\n" to
        "initializeStatement\n   : INITIALIZE identifier+ (WITH? FILLER)? (ALL TO? VALUE)? THEN? initializeReplacingPhrase? (THEN? TO? DEFAULT)?\n",
    // USAGE COMP-X / COMPUTATIONAL-X (Micro Focus / GnuCOBOL binary).
    "COMP_5 : C O M P MINUSCHAR '5';\n" to "COMP_5 : C O M P MINUSCHAR '5';\nCOMP_X : C O M P MINUSCHAR X;\nCOMPUTATIONAL_X : C O M P U T A T I O N A L MINUSCHAR X;\n",
    "| COMPUTATIONAL_5 | CONTROL_POINT |" to "| COMPUTATIONAL_5 | COMP_X | COMPUTATIONAL_X | CONTROL_POINT |",
    // READ ... PREVIOUS [RECORD] (COBOL 2002; PREVIOUS lexes as a word).
    "readStatement\n   : READ fileName NEXT? RECORD?" to "readStatement\n   : READ fileName (NEXT | cobolWord)? RECORD?",
    // ACCEPT ... FROM ENVIRONMENT name (GnuCOBOL).
    "acceptStatement\n   : ACCEPT identifier (acceptFromDateStatement | acceptFromEscapeKeyStatement | acceptFromMnemonicStatement | acceptMessageCountStatement)?" to
        "acceptStatement\n   : ACCEPT identifier (acceptFromDateStatement | acceptFromEscapeKeyStatement | acceptFromMnemonicStatement | acceptMessageCountStatement"
            + " | FROM ENVIRONMENT (literal | qualifiedDataName))?",
    // START ... KEY IS < / <= / NOT > (COBOL 2002).
    "| GREATER THAN? OR EQUAL TO? | MORETHANOREQUAL) qualifiedDataName" to
        "| GREATER THAN? OR EQUAL TO? | MORETHANOREQUAL | LESS THAN? | LESSTHANCHAR | LESS THAN? OR EQUAL TO? | LESSTHANOREQUAL"
            + " | NOT GREATER THAN? | NOT MORETHANCHAR) qualifiedDataName",
)

val patchGrammar by tasks.registering {
    val source = proleapSrc.resolve("antlr4/io/proleap/cobol")
    inputs.dir(source)
    inputs.property("extensions", grammarExtensions)
    outputs.dir(patchedGrammarDir)
    doLast {
        val out = patchedGrammarDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        source.listFiles()!!.filter { it.isFile }.forEach { f ->
            var text = f.readText()
            if (f.name == "Cobol.g4") {
                for ((anchor, replacement) in grammarExtensions) {
                    val count = text.split(anchor).size - 1
                    check(count == 1) { "grammar anchor found $count times in Cobol.g4: $anchor" }
                    text = text.replace(anchor, replacement)
                }
            }
            out.resolve(f.name).writeText(text)
        }
    }
}

// Invoke the ANTLR tool directly with absolute paths; Gradle's antlr plugin mis-resolves
// grammars that live outside the project directory.
val generateGrammar by tasks.registering(JavaExec::class) {
    dependsOn(patchGrammar)
    val grammarDir = patchedGrammarDir.get().asFile
    inputs.dir(patchedGrammarDir)
    outputs.dir(generatedDir)
    classpath = antlrTool
    mainClass = "org.antlr.v4.Tool"
    args(
        "-o", generatedDir.get().dir("io/proleap/cobol").asFile.absolutePath,
        "-Xexact-output-dir", "-package", "io.proleap.cobol", "-visitor", "-listener",
        "-lib", grammarDir.absolutePath,
        grammarDir.resolve("CobolPreprocessor.g4").absolutePath,
        grammarDir.resolve("Cobol.g4").absolutePath,
    )
}

sourceSets.main { java.srcDirs(proleapSrc.resolve("java"), generateGrammar) }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-nowarn") }
