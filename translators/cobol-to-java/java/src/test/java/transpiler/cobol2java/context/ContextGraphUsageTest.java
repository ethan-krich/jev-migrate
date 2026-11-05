package transpiler.cobol2java.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static transpiler.cobol2java.TestSupport.parse;
import static transpiler.cobol2java.TestSupport.program;

import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntry;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a piece of procedure code reads before it writes, and what it writes. */
class ContextGraphUsageTest {
    @TempDir
    Path dir;

    private static final String DATA = String.join("\n",
            "01 TOTAL PIC 9(4) VALUE 0.", "01 STEP PIC 9 VALUE 1.", "01 OUT PIC 9(4).", "01 A PIC 9(4).", "01 B PIC 9(4).",
            "01 C PIC 9(4).", "01 D PIC 9(4).", "01 PTR PIC 99.", "01 TXT PIC X(10).", "01 CNT PIC 99.");

    private ContextGraph.Usage usage(String... statements) throws Exception {
        var unit = parse(dir, program(DATA, concat("MAIN.", statements)));
        var paragraph = unit.getProcedureDivision().getParagraphs().getFirst();
        return ContextGraph.usage(paragraph.getCtx(), unit.getProgram());
    }

    private static String[] concat(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    private static List<String> names(List<DataDescriptionEntry> entries) {
        return entries.stream().map(DataDescriptionEntry::getName).toList();
    }

    @Test
    void aReceiverCombinedWithItsIncomingValueIsReadAndWritten() throws Exception {
        var u = usage("ADD STEP TO TOTAL.");
        assertEquals(List.of("STEP", "TOTAL"), names(u.reads()));
        assertEquals(List.of("TOTAL"), names(u.writes()));
        u = usage("SUBTRACT STEP FROM A.", "MULTIPLY 2 BY B.", "DIVIDE 2 INTO C.");
        assertEquals(List.of("STEP", "A", "B", "C"), names(u.reads()));
        assertEquals(List.of("A", "B", "C"), names(u.writes()));
        u = usage("STRING 'x' DELIMITED BY SIZE INTO TXT WITH POINTER PTR END-STRING.", "INSPECT TXT TALLYING CNT FOR ALL 'x'.");
        // TXT is written by the STRING before INSPECT reads it, so it is not an input; PTR and CNT are.
        assertEquals(List.of("PTR", "CNT"), names(u.reads()));
    }

    @Test
    void aReceiverTheStatementOnlyOverwritesIsNotRead() throws Exception {
        var u = usage("ADD STEP TO TOTAL GIVING OUT.", "COMPUTE D = 1.", "MOVE STEP TO A.", "DIVIDE STEP INTO B GIVING C.");
        assertEquals(List.of("STEP", "TOTAL", "B"), names(u.reads()));
        assertEquals(List.of("OUT", "D", "A", "C"), names(u.writes()));
    }

    @Test
    void anItemWrittenEarlierIsNotAnInput() throws Exception {
        var u = usage("MOVE 1 TO TOTAL.", "ADD STEP TO TOTAL.");
        assertEquals(List.of("STEP"), names(u.reads()));
        assertEquals(List.of("TOTAL"), names(u.writes()));
    }
}
