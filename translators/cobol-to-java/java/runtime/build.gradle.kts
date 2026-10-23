// COBOL data semantics used by generated Java. No dependencies: sources are copied verbatim
// (with the package rewritten) into every generated project.
plugins { `java-library` }

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
