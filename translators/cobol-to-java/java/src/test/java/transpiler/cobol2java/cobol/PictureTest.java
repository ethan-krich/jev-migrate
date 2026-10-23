package transpiler.cobol2java.cobol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PictureTest {
    @ParameterizedTest
    @CsvSource({"X(10), 10", "XXX, 3", "x(2)X, 3"})
    void alphanumeric(String pic, int length) {
        assertEquals(new Picture.Alphanumeric(length), Picture.parse(pic).picture().orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"9(5), 5, 0, false", "S9(3)V99, 5, 2, true", "999V9(3), 6, 3, false", "S9, 1, 0, true"})
    void numeric(String pic, int digits, int scale, boolean signed) {
        assertEquals(new Picture.Numeric(digits, scale, signed), Picture.parse(pic).picture().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZ9", "9(3).99", "A(3)", "N(2)", "99PP", "-999", "S", "9(0)", "9(40)", "X(", "$9"})
    void outsideTheSubsetIsUnsupported(String pic) {
        var r = Picture.parse(pic);
        assertTrue(r.picture().isEmpty());
        assertTrue(r.unsupportedReason() != null && !r.unsupportedReason().isEmpty());
    }
}
