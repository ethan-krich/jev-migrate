package transpiler.cobol2java.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static transpiler.cobol2java.TestSupport.compile;
import static transpiler.cobol2java.TestSupport.defaultRules;
import static transpiler.cobol2java.TestSupport.dialect;
import static transpiler.cobol2java.TestSupport.program;
import static transpiler.cobol2java.TestSupport.translate;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transpiler.cobol2java.context.ContextGraph;
import transpiler.cobol2java.decision.Candidate;
import transpiler.cobol2java.decision.Decision;
import transpiler.cobol2java.decision.Importance;
import transpiler.cobol2java.decision.Reason;

/** JDK twins of builtin handlers, offered as runtime_choice decisions with options.jdk_alternatives. */
class JdkAlternativesTest {
    @TempDir
    Path dir;

    private static final String DATA = String.join("\n",
            "01 A PIC 9(4)V99 VALUE 10.50.",
            "01 B PIC 9(4)V99 VALUE 2.",
            "01 C PIC 9(6)V99.",
            "01 N PIC 9(3) VALUE 0.",
            "01 S PIC X(10) VALUE 'ab,cd'.",
            "01 T PIC X(20).",
            "01 P1 PIC X(5).",
            "01 P2 PIC X(5).");

    private static final String[] PROCEDURE = {
            "ADD A TO B.",
            "SUBTRACT 1 FROM B GIVING C ROUNDED.",
            "MULTIPLY A BY B.",
            "DIVIDE A BY 3 GIVING C.",
            "COMPUTE C = (A + B) * 2 - A / 4.",
            "IF A > B AND S = 'ab,cd' DISPLAY 'big ' A END-IF.",
            "STRING S DELIMITED BY ',' '-' DELIMITED BY SIZE INTO T.",
            "UNSTRING S DELIMITED BY ',' INTO P1 P2.",
            "INSPECT S TALLYING N FOR ALL 'b'.",
            "INSPECT S REPLACING ALL 'a' BY 'x'.",
            "STOP RUN."};

    private ProgramTranslator.Result translateWith(boolean alternatives, String data, String... procedure) throws Exception {
        return translate(dir, program(data, procedure), defaultRules(), Map.of(),
                dialect("{\"jdk_alternatives\": " + alternatives + "}"));
    }

    /** Resolves every decision, the nested ones an applied candidate raises included, with {@code choose}. */
    private static void resolveAll(ProgramTranslator.Result r, Function<Decision, String> choose) {
        Deque<Decision> pending = new ArrayDeque<>(r.decisions());
        int n = 0;
        while (!pending.isEmpty()) {
            Decision d = pending.pop();
            d.register("decision-" + ++n, "java", "sha256:0", Importance.empty(), ContextGraph.Budget.DEFAULT);
            pending.addAll(d.resolve(choose.apply(d), null));
        }
    }

    private static String uses(Decision d, String kind) {
        return d.candidates().stream().filter(c -> kind.equals(c.uses())).map(Candidate::id).findFirst().orElseThrow();
    }

    @Test
    void withoutTheOptionNothingChanges() throws Exception {
        var r = translateWith(false, DATA, PROCEDURE);
        assertEquals(List.of(), r.decisions());
        String src = r.compilationUnit().toString();
        assertFalse(src.contains("System.out"), src);
        assertTrue(src.contains("Display.line("), src);
    }

    @Test
    void eachTwinnedStatementIsARuntimeChoiceWithBothCandidates() throws Exception {
        var r = translateWith(true, DATA, PROCEDURE);
        // ADD, SUBTRACT, MULTIPLY, DIVIDE, COMPUTE, IF, STRING, UNSTRING and both INSPECTs; STOP RUN has no twin.
        assertEquals(10, r.decisions().size());
        for (Decision d : r.decisions()) {
            assertEquals(Reason.RUNTIME_CHOICE, d.reason());
            assertEquals(List.of("runtime", "jdk"), d.candidates().stream().map(Candidate::uses).toList(), d.operation());
            Candidate jdk = d.candidates().get(1);
            assertTrue(jdk.id().endsWith("_jdk"), jdk.id());
            assertTrue(jdk.description().contains("approximate"), jdk.description());
        }
        Candidate add = r.decisions().getFirst().candidates().get(1);
        assertEquals("b.store(BigDecimal.valueOf(b.get().doubleValue() + a.get().doubleValue()), false, false);", add.preview());
        Candidate compute = r.decisions().get(4).candidates().get(1);
        assertTrue(compute.preview().contains("(a.get().doubleValue() + b.get().doubleValue()) * 2.0 - a.get().doubleValue() / 4.0"),
                compute.preview());
        Candidate ifJdk = r.decisions().get(5).candidates().get(1);
        assertTrue(ifJdk.preview().contains("a.get().doubleValue() > b.get().doubleValue() && s.get().equals(\"ab,cd\")"),
                ifJdk.preview());
    }

    @Test
    void jdkCandidatesCompileAndUseNoRuntimeHelpers() throws Exception {
        var r = translateWith(true, DATA, PROCEDURE);
        resolveAll(r, d -> uses(d, "jdk"));
        String src = r.compilationUnit().toString();
        assertFalse(src.contains("UNRESOLVED"), src);
        assertTrue(src.contains("System.out.println(\"big \" + a.display());"), src);
        assertTrue(src.contains("new StringBuilder().append(s.get().split(java.util.regex.Pattern.quote(\",\"), 2)[0])"), src);
        assertTrue(src.contains("s.get().split(java.util.regex.Pattern.quote(\",\"), -1)"), src);
        assertTrue(src.contains("s.set(s.get().replace(\"a\", \"x\"));"), src);
        for (String helper : List.of("Arithmetic.", "Compare.", "Display.line", "StringOps.")) {
            assertFalse(src.contains(helper), helper + " in\n" + src);
        }
        assertEquals("", compile("test.gen.T", src));
    }

    @Test
    void runtimeCandidatesGiveTheTranslationWithoutTheOption() throws Exception {
        var r = translateWith(true, DATA, PROCEDURE);
        resolveAll(r, d -> uses(d, "runtime"));
        String chosen = r.compilationUnit().toString();
        String plain = translateWith(false, DATA, PROCEDURE).compilationUnit().toString();
        assertEquals(plain, chosen);
    }

    @Test
    void aStatementNestedInAnIfHasItsOwnChoice() throws Exception {
        var r = translateWith(true, DATA, "IF A > 1 ADD 1 TO B END-IF.", "STOP RUN.");
        assertEquals(1, r.decisions().size());
        Decision ifChoice = r.decisions().getFirst();
        ifChoice.register("decision-1", "java", "sha256:0", Importance.empty(), ContextGraph.Budget.DEFAULT);
        List<Decision> nested = new ArrayList<>(ifChoice.resolve(uses(ifChoice, "jdk"), null));
        assertEquals(1, nested.size());
        assertEquals("ADD", nested.getFirst().operation());
        assertEquals(Reason.RUNTIME_CHOICE, nested.getFirst().reason());
    }

    @Test
    void aFloatFunctionHasOnlyTheJdkCandidate() throws Exception {
        var r = translateWith(true, "01 X PIC 9(3)V9(4) VALUE 2.\n01 Y PIC 9(3)V9(4).", "COMPUTE Y = FUNCTION SQRT(X) + X ** 0.5.",
                "STOP RUN.");
        assertEquals(1, r.decisions().size());
        Decision d = r.decisions().getFirst();
        assertEquals(Reason.RUNTIME_CHOICE, d.reason());
        assertTrue(d.message().contains("cannot translate this statement exactly"), d.message());
        assertEquals(List.of("builtin:compute_gnucobol_jdk"), d.candidates().stream().map(Candidate::id).toList());
        assertEquals("y.store(BigDecimal.valueOf(Math.sqrt(x.get().doubleValue()) + Math.pow(x.get().doubleValue(), 0.5)), false, false);",
                d.candidates().getFirst().preview());
        resolveAll(r, x -> x.candidates().getFirst().id());
        assertEquals("", compile("test.gen.T", r.compilationUnit().toString()));
    }

    @Test
    void withoutTheOptionAFloatFunctionStaysUnsupported() throws Exception {
        var r = translateWith(false, "01 X PIC 9(3)V9(4) VALUE 2.\n01 Y PIC 9(3)V9(4).", "COMPUTE Y = FUNCTION SQRT(X).", "STOP RUN.");
        assertEquals(1, r.decisions().size());
        assertEquals(Reason.UNSUPPORTED_CONSTRUCT, r.decisions().getFirst().reason());
        assertEquals(List.of(), r.decisions().getFirst().candidates());
    }

    @Test
    void aStatementWithoutAJdkFormIsTranslatedWithTheRuntime() throws Exception {
        var r = translateWith(true, DATA, "STRING S DELIMITED BY ',' INTO T WITH POINTER N.", "INSPECT S CONVERTING 'ab' TO 'AB'.",
                "DIVIDE A BY 3 GIVING C REMAINDER B.", "STOP RUN.");
        assertEquals(List.of(), r.decisions());
    }

    @Test
    void conditionTwinsCoverSignClassAndFigurativeTests() throws Exception {
        var r = translateWith(true, DATA, "IF A POSITIVE AND S ALPHABETIC", "   AND T = SPACES AND S IS NOT NUMERIC",
                "   DISPLAY 'ok' END-IF.", "STOP RUN.");
        Decision d = r.decisions().getFirst();
        assertEquals(Reason.RUNTIME_CHOICE, d.reason(), d.message());
        String preview = d.candidates().get(1).preview();
        assertTrue(preview.contains("a.get().doubleValue() > 0.0"), preview);
        assertTrue(preview.contains("s.get().chars().allMatch(ch -> Character.isLetter(ch) || ch == ' ')"), preview);
        assertTrue(preview.contains("t.get().chars().allMatch(ch -> ch == ' ')"), preview);
        assertTrue(preview.contains("!s.get().chars().allMatch(Character::isDigit)"), preview);
        resolveAll(r, x -> uses(x, "jdk"));
        assertEquals("", compile("test.gen.T", r.compilationUnit().toString()));
    }
}
