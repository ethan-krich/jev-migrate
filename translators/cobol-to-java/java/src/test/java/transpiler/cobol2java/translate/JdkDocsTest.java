package transpiler.cobol2java.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transpiler.cobol2java.decision.Candidate;

/** The docs a JDK twin's candidate carries, read from the JDK's lib/src.zip. */
class JdkDocsTest {
    @TempDir
    Path dir;

    private Path zip() throws Exception {
        Path zip = dir.resolve("src.zip");
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("java.base/java/lang/Math.java"));
            z.write("""
                    package java.lang;
                    public final class Math {
                        /**
                         * Returns the correctly rounded positive square root of a {@code double} value.
                         *
                         * <p>Special cases follow.
                         */
                        public static double sqrt(double a) { return a; }
                        private static double hidden(double a) { return a; }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("java.base/java/lang/AbstractStringBuilder.java"));
            z.write("""
                    package java.lang;
                    abstract sealed class AbstractStringBuilder permits StringBuilder {
                        /** Appends the string representation of the {@code Object} argument. */
                        public AbstractStringBuilder append(Object obj) { return this; }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("java.base/java/io/PrintStream.java"));
            z.write("""
                    package java.io;
                    public class PrintStream {
                        /** Prints a String and then terminates the line. */
                        public void println(String x) {}
                    }
                    """.getBytes(StandardCharsets.UTF_8));
        }
        return zip;
    }

    @Test
    void docsComeFromTheJdkSourcesForStaticInstanceAndSystemOutCalls() throws Exception {
        JavaDocs jdk = JavaDocs.of(JavaDocs.jdkSources(zip()));
        var stmts = List.of(
                StaticJavaParser.parseStatement("y.store(BigDecimal.valueOf(Math.sqrt(x.get().doubleValue())), false, false);"),
                StaticJavaParser.parseStatement("t.set(new StringBuilder().append(s.get()).toString());"),
                StaticJavaParser.parseStatement("System.out.println(\"a\" + b.display());"));
        List<String> signatures = jdk.calledByAnyScope(stmts).stream().map(Candidate.Doc::signature).toList();
        assertEquals(List.of("double Math.sqrt(double a)", "AbstractStringBuilder StringBuilder.append(Object obj)",
                "void PrintStream.println(String x)"), signatures);
        assertEquals("Returns the correctly rounded positive square root of a double value.",
                jdk.calledByAnyScope(stmts).getFirst().summary());
    }

    @Test
    void theRunningJdksOwnSourcesWhenItHasThem() {
        // lib/src.zip of java.home (or TRANSPILER_JDK_SRC); a JDK installed without its sources has none.
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(JavaDocs.jdkSourceZip()));
        var stmts = List.of(StaticJavaParser.parseStatement("y.store(BigDecimal.valueOf(Math.sqrt(x)), false, false);"));
        List<String> signatures = JavaDocs.jdk().calledByAnyScope(stmts).stream().map(Candidate.Doc::signature).toList();
        assertTrue(signatures.contains("double Math.sqrt(double a)"), signatures.toString());
        assertTrue(signatures.contains("BigDecimal BigDecimal.valueOf(double val)"), signatures.toString());
    }

    @Test
    void withoutTheJdkSourcesThereAreNoDocs() {
        assertTrue(JavaDocs.jdkSources(dir.resolve("missing.zip")).isEmpty());
    }
}
