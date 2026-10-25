package transpiler.cobol.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// Byte images and DISPLAY forms observed with GnuCOBOL 3.2 (cobc -x, and -fno-binary-truncate).
class UsageTest {
    private static String hex(Storage s, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append(String.format("%02X", s.bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    @Test
    void packedDecimal() {
        Storage s = new Storage(8);
        NumericField p1 = NumericField.packed(s, 0, 5, 0, true).initial("-123");
        NumericField p2 = NumericField.packed(s, 3, 3, 0, false).initial("123");
        NumericField p3 = NumericField.packed(s, 5, 5, 1, true).initial("12.5");
        assertEquals("00123D123F00125C", hex(s, 0, 8));
        assertEquals("-00123 123 +0012.5", p1.display() + " " + p2.display() + " " + p3.display());
        p1.move(new BigDecimal("1234567"));
        assertEquals("+34567", p1.display());
        assertTrue(p1.isNumeric());
    }

    @Test
    void binaryTruncatedToPicture() {
        Storage s = new Storage(17);
        NumericField b1 = NumericField.binary(s, 0, 4, 0, true, true).initial("-2");
        NumericField b2 = NumericField.binary(s, 2, 4, 0, false, true).initial("258");
        NumericField b3 = NumericField.binary(s, 4, 9, 0, true, true).initial("65536");
        NumericField b4 = NumericField.binary(s, 8, 2, 0, false, true).initial("7");
        NumericField b5 = NumericField.binary(s, 9, 18, 0, true, true).initial("-1");
        assertEquals("FFFE01020001000007FFFFFFFFFFFFFFFF", hex(s, 0, 17));
        assertEquals("-0002 0258 +000065536 07 -000000000000000001",
                b1.display() + " " + b2.display() + " " + b3.display() + " " + b4.display() + " " + b5.display());
        b2.move(new BigDecimal("12345"));
        assertEquals("2345", b2.display());
        b2.move(new BigDecimal("9999"));
        assertTrue(b2.store(new BigDecimal("10000"), false, true));
        assertEquals("9999", b2.display());
    }

    @Test
    void binaryWithoutTruncationAndNative() {
        Storage s = new Storage(4);
        NumericField k = NumericField.binary(s, 0, 4, 0, false, false);
        k.move(new BigDecimal("12345"));
        assertEquals("2345", k.display());
        k.move(new BigDecimal("9999"));
        assertFalse(k.store(new BigDecimal("10000"), false, true));
        assertEquals("0000", k.display());
        assertEquals(new BigDecimal("10000"), k.get());
        NumericField c5 = NumericField.nativeBinary(s, 2, 4, 0, false).initial("258");
        assertEquals("0201", hex(s, 2, 4));
        assertEquals("00258", c5.display());
        c5.move(new BigDecimal("70000"));
        assertEquals("04464", c5.display());
        c5.move(new BigDecimal("-5"));
        assertEquals("00005", c5.display());
        c5.move(new BigDecimal("9999"));
        assertFalse(c5.store(new BigDecimal("10000"), false, true));
        c5.move(new BigDecimal("65535"));
        assertTrue(c5.store(new BigDecimal("65536"), false, true));
        assertEquals("65535", c5.display());
        NumericField ks = NumericField.nativeBinary(s, 0, 4, 0, true);
        ks.move(new BigDecimal("40000"));
        assertEquals("-25536", ks.display());
    }
}
