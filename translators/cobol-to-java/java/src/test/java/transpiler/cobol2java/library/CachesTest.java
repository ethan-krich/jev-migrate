package transpiler.cobol2java.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses fake cache directories, never the real ~/.m2 or ~/.gradle. */
class CachesTest {
    @Test
    void findsMavenSourcesJarAtItsFixedPath(@TempDir Path tmp) throws Exception {
        Path jar = touch(tmp.resolve("m2/com/acme/acme-dates/2.3.1/acme-dates-2.3.1-sources.jar"));
        assertEquals(jar, new Caches(tmp.resolve("m2"), tmp.resolve("gradle")).sourcesJar("com.acme:acme-dates:2.3.1"));
    }

    @Test
    void findsGradleSourcesJarInTheFirstHashFolder(@TempDir Path tmp) throws Exception {
        Path base = tmp.resolve("gradle/com.acme/acme-dates/2.3.1");
        touch(base.resolve("bbb/acme-dates-2.3.1-sources.jar"));
        Path first = touch(base.resolve("aaa/acme-dates-2.3.1-sources.jar"));
        touch(base.resolve("ccc/acme-dates-2.3.1.jar"));
        assertEquals(first, new Caches(tmp.resolve("m2"), tmp.resolve("gradle")).sourcesJar("com.acme:acme-dates:2.3.1"));
    }

    @Test
    void aJarWithoutSourcesSaysHowToFetchThem(@TempDir Path tmp) throws Exception {
        touch(tmp.resolve("m2/com/acme/acme-dates/2.3.1/acme-dates-2.3.1.jar"));
        var e = assertThrows(Caches.Unavailable.class,
                () -> new Caches(tmp.resolve("m2"), null).sourcesJar("com.acme:acme-dates:2.3.1"));
        assertTrue(e.getMessage().contains("without its sources jar"), e.getMessage());
        assertTrue(e.getMessage().contains("mvn dependency:sources") && e.getMessage().contains("downloadSources = true"));
    }

    @Test
    void aMissingDependencyNamesTheCachesSearched(@TempDir Path tmp) {
        var e = assertThrows(Caches.Unavailable.class,
                () -> new Caches(tmp.resolve("m2"), tmp.resolve("gradle")).sourcesJar("com.acme:nope:1"));
        assertTrue(e.getMessage().contains("not installed in the local caches") && e.getMessage().contains(tmp.resolve("m2").toString()));
        assertThrows(Caches.Unavailable.class, () -> new Caches(null, null).sourcesJar("com.acme:nope"));
    }

    private static Path touch(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, "");
        return p;
    }
}
