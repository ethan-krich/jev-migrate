package transpiler.cobol.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompareArithmeticTest {
    @Test
    void allLiteralRepeatsAndCuts() {
        AlphanumericField f = new AlphanumericField(5);
        f.fill("xy");
        assertEquals("xyxyx", f.get());
        assertEquals(0, Compare.figurative("xyxyx", "xy"));
        assertTrue(Compare.figurative("xyxyy", "xy") > 0);
    }

    @Test
    void classTestsReadEachCharacter() {
        assertTrue(Compare.isNumeric("0123"));
        assertFalse(Compare.isNumeric("01 3"));
        assertTrue(Compare.isAlphabetic("ab C"));
        assertFalse(Compare.isAlphabeticUpper("aB"));
        assertTrue(Compare.isAlphabeticLower("ab "));
        assertTrue(Compare.inClass("AEI", "AEIOU"));
    }

    @Test
    void remainderUsesTheQuotientTruncatedToItsScale() {
        // -17.3 / 5 = -3.46: quotient -3.4 (even when ROUNDED stores -3.5), remainder -0.3.
        assertEquals(new BigDecimal("-0.30"), Arithmetic.remainder(new BigDecimal("-17.3"), new BigDecimal("5"), 1).setScale(2));
        // The quotient is not cut to the receiver's integer digits: 99 / 5 leaves 0.
        assertEquals(0, Arithmetic.remainder(new BigDecimal("99"), new BigDecimal("5"), 1).signum());
        assertNull(Arithmetic.remainder(BigDecimal.ONE, BigDecimal.ZERO, 0));
    }

    @Test
    void decimalCommaDisplay() {
        assertEquals("-012,50", new NumericField(5, 2, true, "-12.5").display(','));
    }
}
