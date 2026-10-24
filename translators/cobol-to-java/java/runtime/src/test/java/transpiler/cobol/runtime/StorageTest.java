package transpiler.cobol.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// Byte images and readings observed with GnuCOBOL 3.2 through REDEFINES (build/scratch probes).
class StorageTest {
    private static String bytes(Storage s) {
        return s.text(0, s.length());
    }

    @Test
    void zonedSignIsOverpunchedAsGnuCobolDoesOnAscii() {
        Storage s = new Storage(18);
        new NumericField(s, 0, 3, 0, true).initial("-123");
        new NumericField(s, 3, 3, 0, true).initial("45");
        new NumericField(s, 6, 4, 1, false).initial("1.5");
        new NumericField(s, 10, 3, 0, true, true, true).initial("-7");
        new NumericField(s, 14, 3, 0, true, false, true).initial("7");
        assertEquals("12s0450015-007007+", bytes(s));
        Storage lead = new Storage(2);
        new NumericField(lead, 0, 2, 0, true, true, false).initial("-12");
        assertEquals("q2", bytes(lead));
    }

    @Test
    void invalidDigitsReadAsGnuCobolReadsThem() {
        Storage s = new Storage(12);
        new AlphanumericField(s, 0, 12).set("1 3AB412p12}");
        NumericField m1 = new NumericField(s, 0, 3, 0, false);
        NumericField m2 = new NumericField(s, 3, 3, 0, true);
        NumericField m3 = new NumericField(s, 6, 3, 0, true);
        NumericField m4 = new NumericField(s, 9, 3, 0, true);
        assertEquals("1 3 +AB4 -120 +120", m1.display() + " " + m2.display() + " " + m3.display() + " " + m4.display());
        assertEquals(new BigDecimal("103"), m1.get());
        assertEquals(new BigDecimal("124"), m2.get());
        assertEquals(new BigDecimal("-120"), m3.get());
        assertEquals(new BigDecimal("120"), m4.get());
    }

    @Test
    void displayToDisplayMoveCopiesDigitPositions() {
        Storage s = new Storage(2);
        AlphanumericField raw = new AlphanumericField(s, 0, 2);
        NumericField out = new NumericField(4, 0, true);
        raw.set("!1");
        out.move(new NumericField(s, 0, 2, 0, false));
        assertEquals("+00!1", out.display());
        raw.set("1!");
        out.move(new NumericField(s, 0, 2, 0, true));
        assertEquals("+0010", out.display());
        raw.set("1p");
        out.move(new NumericField(s, 0, 2, 0, true));
        assertEquals("-0010", out.display());
    }

    @Test
    void viewsShareBytes() {
        Storage s = new Storage(5);
        AlphanumericField group = new AlphanumericField(s, 0, 5);
        AlphanumericField a = new AlphanumericField(s, 0, 2).initial("AB");
        NumericField n = new NumericField(s, 2, 3, 0, false).initial();
        assertEquals("AB000", group.get());
        n.move(new BigDecimal("42"));
        assertEquals("AB042", group.get());
        group.set("XY9");
        assertEquals("XY", a.get());
        assertEquals("9  ", n.digits());
    }
}
