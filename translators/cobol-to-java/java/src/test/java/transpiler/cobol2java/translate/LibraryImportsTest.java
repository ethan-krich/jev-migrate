package transpiler.cobol2java.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class LibraryImportsTest {
    private static CompilationUnit unit() {
        return StaticJavaParser.parse("""
                package p;

                import p.runtime.Display;

                /** Translated from X. */
                public final class Billing {
                    private com.acme.dates.DateFormatter f;

                    void run() {
                        a.move(com.acme.dates.Dates.addDays(1, 2));
                        b.set(new com.acme.dates.DateFormatter().format(3));
                        c.set(com.acme.util.Display.show(4));
                        d.set(com.acme.dates.Format.Style.of(5));
                        e.set(com.acme.dates.Dates.addDays(6, 7));
                        g.set(org.other.Dates.x());
                    }
                }
                """);
    }

    @Test
    void importsFreeNamesAndKeepsClashesQualified() {
        CompilationUnit cu = unit();
        LibraryImports.apply(cu, List.of("com.acme.dates.Dates", "com.acme.dates.DateFormatter", "com.acme.util.Display",
                "com.acme.dates.Format", "com.acme.dates.Dates"), "Uses libraries (not equivalence-checked against the COBOL): acme.");
        String java = cu.toString();
        assertTrue(java.contains("import com.acme.dates.Dates;") && java.contains("import com.acme.dates.DateFormatter;")
                && java.contains("import com.acme.dates.Format;"), java);
        assertTrue(java.contains("a.move(Dates.addDays(1, 2));") && java.contains("e.set(Dates.addDays(6, 7));"), java);
        assertTrue(java.contains("private DateFormatter f;") && java.contains("new DateFormatter().format(3)"), java);
        assertTrue(java.contains("d.set(Format.Style.of(5));"), java);
        // Display is a runtime class of the file: the library's Display stays qualified and is not imported.
        assertTrue(java.contains("c.set(com.acme.util.Display.show(4));") && !java.contains("import com.acme.util.Display;"), java);
        // Not a library type of this file: untouched.
        assertTrue(java.contains("org.other.Dates.x()"), java);
        assertTrue(java.contains("<p>Uses libraries (not equivalence-checked against the COBOL): acme."), java);
    }

    @Test
    void twoLibrariesSharingASimpleNameAreBothQualified() {
        CompilationUnit cu = unit();
        LibraryImports.apply(cu, List.of("com.acme.dates.Dates", "org.other.Dates"), null);
        String java = cu.toString();
        assertTrue(java.contains("com.acme.dates.Dates.addDays(1, 2)") && java.contains("org.other.Dates.x()"), java);
        assertEquals(1, cu.getImports().size());
    }
}
