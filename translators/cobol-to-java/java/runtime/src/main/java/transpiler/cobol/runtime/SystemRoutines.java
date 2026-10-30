package transpiler.cobol.runtime;

/**
 * GnuCOBOL's byte-level system routines (libcob's cob_sys_*), called as {@code CALL "CBL_..."}.
 * Case changes follow the "C" locale libcob sets for LC_CTYPE: only ASCII letters change. Each
 * returns 0, which becomes RETURN-CODE. A length past the end of an item, where libcob would
 * write into the next storage, ends the run unit.
 */
public final class SystemRoutines {
    private SystemRoutines() {}

    /** CBL_TOUPPER item, length. */
    public static int toUpper(AlphanumericField item, int length) {
        byte[] b = bytes(item, length, "CBL_TOUPPER");
        for (int i = 0; i < length; i++) {
            int c = b[item.offset() + i] & 0xFF;
            if (c >= 'a' && c <= 'z') {
                b[item.offset() + i] = (byte) (c - 32);
            }
        }
        return 0;
    }

    /** CBL_TOLOWER item, length. */
    public static int toLower(AlphanumericField item, int length) {
        byte[] b = bytes(item, length, "CBL_TOLOWER");
        for (int i = 0; i < length; i++) {
            int c = b[item.offset() + i] & 0xFF;
            if (c >= 'A' && c <= 'Z') {
                b[item.offset() + i] = (byte) (c + 32);
            }
        }
        return 0;
    }

    /** CBL_NOT item, length: every bit inverted. */
    public static int not(AlphanumericField item, int length) {
        byte[] b = bytes(item, length, "CBL_NOT");
        for (int i = 0; i < length; i++) {
            b[item.offset() + i] = (byte) ~b[item.offset() + i];
        }
        return 0;
    }

    /**
     * CBL_AND / OR / XOR / EQ / IMP / NIMP source, target, length: each target byte combined
     * with the source byte, as libcob does.
     */
    public static int bits(String routine, AlphanumericField source, AlphanumericField target, int length) {
        byte[] s = bytes(source, length, routine);
        byte[] t = bytes(target, length, routine);
        for (int i = 0; i < length; i++) {
            int a = s[source.offset() + i];
            int b = t[target.offset() + i];
            int r = switch (routine) {
                case "CBL_AND" -> b & a;
                case "CBL_OR" -> b | a;
                case "CBL_XOR" -> b ^ a;
                case "CBL_EQ" -> ~(a ^ b);
                case "CBL_IMP" -> ~a | b;
                case "CBL_NIMP" -> a & ~b;
                default -> throw new IllegalArgumentException(routine);
            };
            t[target.offset() + i] = (byte) r;
        }
        return 0;
    }

    private static byte[] bytes(AlphanumericField item, int length, String routine) {
        if (length > item.length()) {
            Bounds.fail(routine + ": length " + length + " is past the end of an item of " + item.length() + " bytes");
        }
        return item.storage().bytes;
    }
}
