package transpiler.cobol.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Expected values were observed with GnuCOBOL 3.2 (cobc -x, default dialect).
class NumericFieldTest {

    @ParameterizedTest
    @CsvSource({
        "3, 2, false, 12.34, 012.34",
        "3, 2, true, -12.34, -012.34",
        "3, 0, true, 5, +005",
        "3, 0, true, -5, -005",
        "5, 0, false, 0, 00000",
    })
    void displayMatchesGnuCobol(int intDigits, int scale, boolean signed, String value, String expected) {
        NumericField field = new NumericField(intDigits + scale, scale, signed, value);
        assertEquals(expected, field.display());
    }

    @Test
    void moveTruncatesHighOrderDigits() {
        NumericField n2 = new NumericField(2, 0, false);
        n2.move(new BigDecimal("12345"));
        assertEquals("45", n2.display());
    }

    @Test
    void moveToUnsignedDropsSignAndFraction() {
        NumericField n2 = new NumericField(2, 0, false);
        n2.move(new BigDecimal("-12.99"));
        assertEquals("12", n2.display());
    }

    @Test
    void storeWithoutSizeErrorPhraseTruncates() {
        NumericField n2 = new NumericField(2, 0, false, "12");
        assertTrue(n2.store(new BigDecimal("107"), false, false));
        assertEquals("07", n2.display());
    }

    @Test
    void storeWithSizeErrorPhraseLeavesReceiverUnchanged() {
        NumericField n2 = new NumericField(2, 0, false, "7");
        assertTrue(n2.store(new BigDecimal("102"), false, true));
        assertEquals("07", n2.display());
    }

    @Test
    void divisionByZeroLeavesReceiverUnchanged() {
        NumericField d = new NumericField(3, 0, false, "7");
        assertTrue(d.store(Arithmetic.divide(d.get(), BigDecimal.ZERO, 0, false), false, false));
        assertEquals("007", d.display());
    }

    @Test
    void roundedRoundsHalfAwayFromZero() {
        NumericField s = new NumericField(3, 1, true);
        assertFalse(s.store(Arithmetic.divide(new BigDecimal("-1"), new BigDecimal("4"), 1, true), true, false));
        assertEquals("-00.3", s.display());
        NumericField d = new NumericField(3, 0, false, "7");
        d.store(Arithmetic.divide(d.get(), new BigDecimal("3"), 0, true), true, false);
        assertEquals("002", d.display());
    }

    @Test
    void divideTruncatesAtReceiverScale() {
        NumericField q = new NumericField(5, 2, false);
        q.store(Arithmetic.divide(BigDecimal.TEN, new BigDecimal("3"), 2, false), false, false);
        assertEquals("003.33", q.display());
    }

    @Test
    void digitsOmitSign() {
        assertEquals("005", new NumericField(3, 0, true, "-5").digits());
    }
}
