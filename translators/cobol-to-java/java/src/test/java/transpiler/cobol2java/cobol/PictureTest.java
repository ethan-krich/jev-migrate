package transpiler.cobol2java.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PictureTest {
    @ParameterizedTest
    @CsvSource({"X(10), 10", "XXX, 3", "x(2)X, 3", "A(3), 3"})
    void alphanumeric(String pic, int length) {
        assertEquals(new Picture.Alphanumeric(length), Picture.parse(pic).picture().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"9(5), 5, 0, false", "S9(3)V99, 5, 2, true", "999V9(3), 6, 3, false", "S9, 1, 0, true"})
    void numeric(String pic, int digits, int scale, boolean signed) {
        assertEquals(new Picture.Numeric(digits, scale, signed), Picture.parse(pic).picture().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"ZZ9, ZZ9, true, 3", "'Z,ZZ9.99', 'Z,ZZ9.99', true, 8", "$(3)9CR, $$$9CR, true, 6",
        "XXBX/X, XXBX/X, false, 6"})
    void edited(String pic, String expanded, boolean numeric, int length) {
        assertEquals(new Picture.Edited(expanded, numeric, length), Picture.parse(pic).picture().orElseThrow());
    }

    @org.junit.jupiter.api.Test
    void scalingPositions() {
        assertEquals(new Picture.Numeric(2, -2, false), Picture.parse("99PP").picture().orElseThrow());
        assertEquals(new Picture.Numeric(2, 5, true), Picture.parse("SPPP99").picture().orElseThrow());
        assertEquals(new Picture.Numeric(1, 3, false), Picture.parse("VPP9").picture().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"N(2)", "Z9PP", "9P9", "S", "9(0)", "9(40)", "X(", "-$$9.99", "S9.99", "ZZ9Q"})
    void outsideTheSubsetIsUnsupported(String pic) {
        var r = Picture.parse(pic);
        assertTrue(r.picture().isEmpty());
        assertTrue(r.unsupportedReason() != null && !r.unsupportedReason().isEmpty());
    }
}
