package transpiler.cobol2java.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetProjectTest {
    private static final String GAV = "com.acme:acme-dates:2.3.1";

    private static String pom(String parent, String version) {
        return "<project><modelVersion>4.0.0</modelVersion>" + parent + "<dependencies>"
                + "<dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4</version></dependency>"
                + "<dependency><groupId>com.acme</groupId><artifactId>acme-dates</artifactId>" + version + "</dependency>"
                + "</dependencies></project>";
    }

    @Test
    void mavenDirectDependencyWithALiteralVersion(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), pom("", "<version>2.3.1</version>"));
        assertEquals(true, TargetProject.check(tmp, GAV).declared());
        Files.writeString(tmp.resolve("pom.xml"), pom("", "<version>2.0</version>"));
        var other = TargetProject.check(tmp, GAV);
        assertEquals(false, other.declared());
        assertTrue(other.note().contains("2.0, not 2.3.1"), other.note());
    }

    @Test
    void propertyVersionsAndParentsCannotBeConfirmed(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), pom("", "<version>${dates.version}</version>"));
        assertNull(TargetProject.check(tmp, GAV).declared());
        Files.writeString(tmp.resolve("pom.xml"), pom("", ""));
        assertTrue(TargetProject.check(tmp, GAV).note().contains("no version"));
        Files.writeString(tmp.resolve("pom.xml"), "<project><parent><artifactId>p</artifactId></parent></project>");
        assertNull(TargetProject.check(tmp, GAV).declared());
        Files.writeString(tmp.resolve("pom.xml"), "<project></project>");
        assertEquals(false, TargetProject.check(tmp, GAV).declared());
    }

    @Test
    void gradleLockfilesOfTheProjectAndItsSubprojects(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("build.gradle.kts"), "dependencies { implementation(\"com.acme:acme-dates:2.3.1\") }");
        var unlocked = TargetProject.check(tmp, GAV);
        assertNull(unlocked.declared());
        assertTrue(unlocked.note().contains("--write-locks"));
        Files.createDirectories(tmp.resolve("app"));
        Files.writeString(tmp.resolve("app/gradle.lockfile"), "com.acme:acme-dates:2.3.1=compileClasspath,runtimeClasspath\n");
        assertEquals(true, TargetProject.check(tmp, GAV).declared());
        assertEquals(false, TargetProject.check(tmp, "com.acme:acme-dates:9").declared());
    }

    @Test
    void sourcePathLibrariesAndEmptyProjectsAreUnconfirmed(@TempDir Path tmp) {
        assertNull(TargetProject.check(tmp, null).declared());
        assertNull(TargetProject.check(tmp, GAV).declared());
    }
}
