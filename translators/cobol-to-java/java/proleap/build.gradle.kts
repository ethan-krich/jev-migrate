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

// Invoke the ANTLR tool directly with absolute paths; Gradle's antlr plugin mis-resolves
// grammars that live outside the project directory.
val generateGrammar by tasks.registering(JavaExec::class) {
    val grammarDir = proleapSrc.resolve("antlr4/io/proleap/cobol")
    inputs.dir(grammarDir)
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
