// COBOL-to-Java translator worker (persistent NDJSON process driven by the C++ orchestrator).
plugins {
    java
    application
}

allprojects {
    repositories { mavenCentral() }
    plugins.withType<JavaPlugin> {
        tasks.withType<JavaCompile>().configureEach {
            options.release = 21
            options.encoding = "UTF-8"
        }
        dependencyLocking { lockAllConfigurations() }
    }
}

dependencies {
    implementation(project(":proleap"))
    implementation("com.github.javaparser:javaparser-core:3.28.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.9")

    testImplementation(project(":runtime"))
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "transpiler.cobol2java.worker.WorkerMain"
    applicationName = "cobol-to-java-worker"
    // ProLeap's ANTLR parser recurses deeply on larger programs.
    applicationDefaultJvmArgs = listOf("-Xss16m")
}

// The runtime's sources ship inside the worker jar; finalize copies them into the output tree.
tasks.processResources {
    from(project(":runtime").file("src/main/java")) { into("runtime-src") }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Xss16m")
    systemProperty("fixtures.dir", file("../fixtures").absolutePath)
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
