package transpiler.cobol2java.translate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static transpiler.cobol2java.TestSupport.compile;
import static transpiler.cobol2java.TestSupport.defaultRules;
import static transpiler.cobol2java.TestSupport.program;
import static transpiler.cobol2java.TestSupport.translate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import transpiler.cobol2java.TestSupport;
import transpiler.cobol2java.decision.Decision;
import transpiler.cobol2java.decision.Reason;

class ProgramTranslatorTest {
    @TempDir
    Path dir;

    private static String java(ProgramTranslator.Result r) {
        ProgramTranslator.finish(r.compilationUnit());
        return r.compilationUnit().toString();
    }

    @Test
    void generatesCompilableClassWithFieldsAndEntryPoints() throws Exception {
        var r = translate(dir, program("01 WS-NAME PIC X(5) VALUE 'AB'.\n01 WS-N PIC S9(3)V99 VALUE -1.5.",
                "MAIN-PARA.", "    MOVE WS-NAME TO WS-NAME.", "    STOP RUN."), defaultRules());
        String src = java(r);
        assertTrue(r.decisions().isEmpty(), () -> r.decisions().toString());
        assertTrue(src.contains("private final AlphanumericField wsName = new AlphanumericField(5, \"AB\");"), src);
        assertTrue(src.contains("private final NumericField wsN = new NumericField(5, 2, true, \"-1.5\");"), src);
        assertTrue(src.contains("public static void main(String[] args)"), src);
        assertTrue(src.contains("paraMainPara();"), src);
        assertEquals("", compile("test.gen.T", src));
    }

    @Test
    void moveUsesRuntimeSemanticsNotAssignment() throws Exception {
        var r = translate(dir, program("01 A PIC X(3).\n01 N PIC S9(3) VALUE -5.\n01 M PIC 9(3).",
                "MOVE 'ABCDEF' TO A.", "MOVE N TO A.", "MOVE SPACES TO A.", "MOVE N TO M.", "MOVE ZERO TO M."), defaultRules());
        String src = java(r);
        assertTrue(src.contains("a.set(\"ABCDEF\");"), src);
        assertTrue(src.contains("a.set(n);"), src);
        assertTrue(src.contains("a.fill(' ');"), src);
        assertTrue(src.contains("m.move(n);"), src);
        assertTrue(src.contains("m.move(BigDecimal.ZERO);"), src);
        assertFalse(src.substring(src.indexOf("procedureDivision()")).contains(" = "), src);
        assertEquals("", compile("test.gen.T", src));
    }

    @Test
    void moveWithSeveralReceiversIsMappedPerReceiver() throws Exception {
        var r = translate(dir, program("01 A PIC X(3).\n01 M PIC 9(3).", "MOVE ZERO TO A M."), defaultRules());
        String src = java(r);
        assertTrue(src.contains("a.fill('0');") && src.contains("m.move(BigDecimal.ZERO);"), src);
        assertEquals(2, r.statements());
    }

    @Test
    void arithmeticWithSizeErrorEvaluatesOperandsOnce() throws Exception {
        var r = translate(dir, program("01 A PIC 99.\n01 B PIC 99.",
                "ADD A TO A B ON SIZE ERROR DISPLAY 'E'", "    NOT ON SIZE ERROR DISPLAY 'K' END-ADD."), defaultRules());
        String src = java(r);
        assertTrue(src.contains("BigDecimal addend1 = a.get();"), src);
        assertTrue(src.contains("sizeError1 |= a.store(a.get().add(addend1), false, true);"), src);
        assertTrue(src.contains("if (sizeError1)"), src);
        assertEquals("", compile("test.gen.T", src));
    }

    @Test
    void divideTruncatesOrRoundsAtEachReceiverScale() throws Exception {
        var r = translate(dir, program("01 Q PIC 9V9.\n01 P PIC 9V99.", "DIVIDE 10 BY 3 GIVING Q P ROUNDED."), defaultRules());
        String src = java(r);
        assertTrue(src.contains("Arithmetic.divide(dividend1, divisor1, 1, false), false, false"), src);
        assertTrue(src.contains("Arithmetic.divide(dividend1, divisor1, 2, true), true, false"), src);
    }

    @Test
    void conditionsGroupAndBeforeOr() throws Exception {
        var r = translate(dir, program("01 A PIC 9.\n01 B PIC X.",
                "IF A = 1 OR A = 2 AND B = 'X' DISPLAY 'Y' END-IF."), defaultRules());
        String src = java(r);
        assertTrue(src.contains("Compare.numeric(a.get(), BigDecimal.ONE) == 0 || Compare.numeric(a.get(), "
                + "new BigDecimal(\"2\")) == 0 && Compare.alphanumeric(b.get(), \"X\") == 0"), src);
    }

    @Test
    void unsupportedConstructsBecomeDecisionsNotApproximations() throws Exception {
        var r = translate(dir, program("01 F COMP-1.\n01 P POINTER.\n01 N PIC 99.",
                "COMPUTE N = N ** 0.5.", "CANCEL 'X'.", "    STOP RUN."), defaultRules());
        List<String> got = r.decisions().stream().map(d -> d.reason().wire() + ":" + d.operation()).toList();
        assertEquals(List.of("unsupported_construct:DATA", "unsupported_construct:DATA", "unsupported_construct:COMPUTE",
                "missing_mapping:CANCEL"), got);
        assertTrue(java(r).contains("// UNRESOLVED CANCEL: CANCEL 'X'"));
    }

    @Test
    void renamesOfAPackedItemIsADecision() throws Exception {
        var r = translate(dir, program("01 R.\n   05 P PIC S9(3) COMP-3.\n   05 X PIC X.\n66 RP RENAMES P.\n66 RX RENAMES P THRU X.",
                "DISPLAY RX.", "STOP RUN."), defaultRules());
        // GnuCOBOL 3.2 reads RP as if it were USAGE DISPLAY; the THRU range is a plain group.
        assertEquals(List.of("unsupported_construct:DATA"), r.decisions().stream().map(d -> d.reason().wire() + ":" + d.operation()).toList());
    }

    @Test
    void assignToADataItemIsADecision() throws Exception {
        String src = transpiler.cobol2java.TestSupport.cobol("IDENTIFICATION DIVISION.", "PROGRAM-ID. T.", "ENVIRONMENT DIVISION.",
                "INPUT-OUTPUT SECTION.", "FILE-CONTROL.", "    SELECT F ASSIGN TO FNAME.", "DATA DIVISION.", "FILE SECTION.", "FD F.",
                "01 F-REC PIC X.", "WORKING-STORAGE SECTION.", "01 FNAME PIC X(8) VALUE 'f.txt'.", "PROCEDURE DIVISION.", "    STOP RUN.");
        var r = translate(dir, src, defaultRules());
        // GnuCOBOL takes the file name from FNAME at run time; it is not an external name.
        assertEquals("unsupported_construct:ENVIRONMENT", r.decisions().getFirst().reason().wire() + ":" + r.decisions().getFirst().operation());
    }

    @Test
    void callResolvesOnlyWithinTheProject() throws Exception {
        String src = program("", "CALL 'SUB'.", "STOP RUN.");
        var found = translate(dir, src, defaultRules(), Map.of("SUB", "test.gen.Sub"));
        assertTrue(java(found).contains("ProgramSupport.call(this, test.gen.Sub.instance(), false);"));
        var otherCase = translate(dir, program("", "CALL 'sub'.", "STOP RUN."), defaultRules(), Map.of("SUB", "test.gen.Sub"));
        assertEquals(Reason.MISSING_TARGET_API, otherCase.decisions().getFirst().reason(), "PROGRAM-IDs match case-sensitively");
        var missing = translate(dir, src, defaultRules());
        assertEquals(Reason.MISSING_TARGET_API, missing.decisions().getFirst().reason());
    }

    @Test
    void removedMappingOffersValidatedCandidatesAndApplies() throws Exception {
        var rules = defaultRules();
        rules.removeIf(n -> n.get("source").asText().equals("DISPLAY"));
        var r = translate(dir, program("01 A PIC 9 VALUE 1.", "IF A = 1 DISPLAY 'one' END-IF."), rules);
        Decision d = r.decisions().getFirst();
        d.register("decision-1", "java", "sha256:0");
        assertEquals(Reason.MISSING_MAPPING, d.reason());
        assertEquals(List.of("builtin:display"), d.candidates().stream().map(c -> c.id()).toList());
        assertTrue(d.candidates().getFirst().preview().contains("Display.line(\"one\")"));
        assertTrue(d.fingerprint().startsWith("sha256:"));
        assertTrue(d.resolve("builtin:display").isEmpty());
        String src = java(r);
        assertFalse(src.contains("UNRESOLVED"), src);
        assertTrue(src.contains("Display.line(\"one\");"), src);
        assertEquals("", compile("test.gen.T", src));
    }

    @Test
    void applyingAStructuralCandidateSurfacesNestedDecisions() throws Exception {
        var rules = defaultRules();
        rules.removeIf(n -> n.get("source").asText().equals("IF") || n.get("source").asText().equals("DISPLAY"));
        var r = translate(dir, program("01 A PIC 9.", "IF A = 1 DISPLAY 'one' END-IF."), rules);
        assertEquals(1, r.decisions().size(), "the nested DISPLAY is only raised once IF is resolved");
        List<Decision> nested = r.decisions().getFirst().resolve("builtin:if");
        assertEquals(1, nested.size());
        assertEquals("DISPLAY", nested.getFirst().operation());
        nested.getFirst().resolve("builtin:display");
        assertFalse(java(r).contains("UNRESOLVED"));
    }

    @Test
    void namesAreDeterministic() {
        assertEquals("PayCalc", Names.className("PAY-CALC"));
        assertEquals("_1abc", Names.fieldName("1ABC"));
        assertEquals("class_", Names.fieldName("CLASS"));
        assertEquals("para100Main", Names.methodName("para", "100-MAIN"));
        assertEquals("month_end", Names.packageSegment("Month-End"));
        assertEquals("_2024", Names.packageSegment("2024"));
        assertFalse(Names.isValidPackage("com.example.class"));
    }

    private static List<String> operations(ProgramTranslator.Result r) {
        return r.decisions().stream().map(Decision::operation).toList();
    }

    @Test
    void anUnsetDialectSettingIsOneDecisionPerProgram() throws Exception {
        String src = program("01 A PIC X(2) VALUE HIGH-VALUES.\n01 B PIC X(2).",
                "MOVE LOW-VALUES TO B.", "IF A = HIGH-VALUES DISPLAY 'h' END-IF.", "STOP RUN.");
        var unset = translate(dir, src, defaultRules());
        assertEquals(List.of("dialect:encoding"), operations(unset));
        Decision d = unset.decisions().getFirst();
        assertEquals(Reason.MISSING_MAPPING, d.reason());
        assertTrue(d.candidates().isEmpty());
        var set = translate(dir, src, defaultRules(), Map.of(), TestSupport.dialect("{\"encoding\": \"ascii\"}"));
        assertTrue(set.decisions().isEmpty(), () -> set.decisions().toString());
        assertEquals("", compile("test.gen.T", java(set)));
    }

    @Test
    void orderedAlphanumericComparisonNeedsTheCollatingSequence() throws Exception {
        String src = program("01 A PIC X(2).", "IF A < 'B' DISPLAY 'x' END-IF.", "IF A = 'B' DISPLAY 'y' END-IF.");
        assertEquals(List.of("dialect:collating_sequence"), operations(translate(dir, src, defaultRules())));
        var ebcdic = translate(dir, src, defaultRules(), Map.of(), TestSupport.dialect("{\"collating_sequence\": \"ebcdic\"}"));
        assertEquals(List.of(Reason.UNSUPPORTED_CONSTRUCT), ebcdic.decisions().stream().map(Decision::reason).toList());
    }

    @Test
    void exitsThatLeaveAPerformedRangeAreDecisions() throws Exception {
        String src = program("01 N PIC 9.",
                "MAIN SECTION.", "P1.", "    PERFORM P2.", "    EXIT PERFORM.", "    STOP RUN.",
                "P2.", "    EXIT SECTION.");
        var r = translate(dir, src, defaultRules());
        assertEquals(List.of("EXIT", "EXIT"), operations(r));
        assertTrue(r.decisions().get(0).toJson("java").get("message").asText().contains("outside an inline PERFORM"));
        assertTrue(r.decisions().get(1).toJson("java").get("message").asText().contains("PERFORMed on its own"));
    }

    @Test
    void correspondingWithSizeErrorIsOneDecision() throws Exception {
        String src = program("01 A.\n 05 X PIC 9.\n 05 Y PIC 9.\n01 B.\n 05 X PIC 9.\n 05 Y PIC 9.",
                "ADD CORR A TO B ON SIZE ERROR DISPLAY 'e' END-ADD.", "ADD CORR A TO B.", "MOVE CORR A TO B.");
        var r = translate(dir, src, defaultRules());
        assertEquals(List.of("ADD"), operations(r));
        String out = java(r);
        // ADD CORR A TO B adds each pair; MOVE CORR moves each pair.
        assertTrue(out.contains("x_2.store(x_2.get().add(x.get()), false, false);"), out);
        assertTrue(out.contains("y_2.store(y_2.get().add(y.get()), false, false);"), out);
        assertTrue(out.contains("x_2.move(x);") && out.contains("y_2.move(y);"), out);
    }

    @Test
    void dispatcherRefusesRangesItCannotReturnFrom() throws Exception {
        var r = translate(dir, program("",
                "MAIN-PARA.", "    PERFORM A THRU B.", "    PERFORM B THRU C.", "    PERFORM D.", "    STOP RUN.",
                "A.", "    DISPLAY 'a'.", "B.", "    DISPLAY 'b'.", "C.", "    PERFORM E.",
                "D.", "    GO TO A.", "E.", "    PERFORM E."), defaultRules());
        List<String> got = r.decisions().stream().map(d -> d.operation() + ":" + d.message()).toList();
        assertEquals(4, got.size(), got.toString());
        assertTrue(got.get(0).startsWith("PERFORM:PERFORM range overlaps"), got.toString());
        assertTrue(got.get(1).startsWith("PERFORM:PERFORM range overlaps"), got.toString());
        assertTrue(got.get(2).startsWith("GO_TO:GO TO out of a PERFORMed range"), got.toString());
        assertTrue(got.get(3).startsWith("PERFORM:recursive PERFORM"), got.toString());
    }

    @Test
    void programsWithoutJumpsKeepTheMethodForm() throws Exception {
        String out = java(translate(dir, program("", "MAIN-PARA.", "    PERFORM A.", "    STOP RUN.", "A.", "    DISPLAY 'a'."),
                defaultRules()));
        assertTrue(out.contains("private void paraA()"), out);
        assertTrue(!out.contains("PerformStack"), out);
    }

    @Test
    void computeFollowsGnuCobolAndRefusesExponentsItCannotReproduce() throws Exception {
        String out = java(translate(dir, program("01 N PIC S9(3)V99.", "COMPUTE N = 1 / 3 * 3 + 2 ** 3 ** 2."), defaultRules()));
        assertTrue(out.contains("Arithmetic.over(BigDecimal.ONE, new BigDecimal(\"3\"))"), out);
        assertTrue(out.contains("Arithmetic.power(new BigDecimal(\"2\"), Arithmetic.power(new BigDecimal(\"3\"), new BigDecimal(\"2\")))"), out);
        for (String e : List.of("2 ** -2", "2 ** 0.5", "2 ** N")) {
            var r = translate(dir, program("01 N PIC S9(3)V99.", "COMPUTE N = " + e + "."), defaultRules());
            assertEquals(Reason.UNSUPPORTED_CONSTRUCT, r.decisions().getFirst().reason(), e);
        }
    }
}
