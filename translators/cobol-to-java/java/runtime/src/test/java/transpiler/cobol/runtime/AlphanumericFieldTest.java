package transpiler.cobol.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AlphanumericFieldTest {

    @Test
    void initialisedToSpaces() {
        assertEquals("     ", new AlphanumericField(5).display());
    }

    @Test
    void valueIsTruncatedOnTheRight() {
        assertEquals("ABC", new AlphanumericField(3, "ABCDEF").get());
    }

    @Test
    void moveIsPaddedWithSpaces() {
        AlphanumericField x = new AlphanumericField(5, "HELLO");
        x.set("AB");
        assertEquals("AB   ", x.get());
    }

    @Test
    void moveFromSignedIntegerMovesDigitsOnly() {
        AlphanumericField x = new AlphanumericField(5);
        x.set(new NumericField(3, 0, true, "-5"));
        assertEquals("005  ", x.get());
    }

    @Test
    void figurativeFill() {
        AlphanumericField x = new AlphanumericField(4);
        x.fill('0');
        assertEquals("0000", x.get());
    }

    @Test
    void comparisonPadsShorterOperand() {
        assertEquals(0, Compare.alphanumeric("ABC", "ABC  "));
        assertEquals(-1, Compare.alphanumeric("AB", "ABC"));
    }
}
