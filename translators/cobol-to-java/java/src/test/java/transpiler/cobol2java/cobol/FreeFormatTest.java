package transpiler.cobol2java.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FreeFormatTest {
    @Test
    void movesTextToColumnEightAndKeepsLines() throws Exception {
        String free = ">>SOURCE FREE\n*> note\nmove 1 to x *> why\ndisplay \"a*>b\"\n>>D display 'd'\n\n";
        assertEquals("      *>>SOURCE FREE\n      **> note\n       move 1 to x \n       display \"a*>b\"\n      D display 'd'\n\n",
                FreeFormat.toVariable(free));
    }

    @Test
    void refusesWhatItCannotCarryOver() {
        assertEquals(2, assertThrows(FreeFormat.Unsupported.class, () -> FreeFormat.toVariable("x\n>>IF A DEFINED\n")).line);
        assertEquals(1, assertThrows(FreeFormat.Unsupported.class, () -> FreeFormat.toVariable("display \"open\n")).line);
        assertEquals(1, assertThrows(FreeFormat.Unsupported.class, () -> FreeFormat.toVariable("$SET SOURCEFORMAT\"FREE\"\n")).line);
        assertThrows(FreeFormat.Unsupported.class, () -> FreeFormat.toVariable(">>SOURCE FORMAT FIXED\n"));
    }
}
