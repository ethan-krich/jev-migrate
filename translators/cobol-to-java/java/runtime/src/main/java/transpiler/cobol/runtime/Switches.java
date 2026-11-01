package transpiler.cobol.runtime;

/**
 * GnuCOBOL's switches SWITCH-0 .. SWITCH-36: each starts ON when the environment variable
 * {@code COB_SWITCH_n} begins with {@code 1} or is {@code ON} (any case), as libcob reads them at
 * startup; SET switch TO ON / OFF changes it for the rest of the run unit.
 */
public final class Switches {
    private static final boolean[] ON = new boolean[37];

    static {
        for (int i = 0; i < ON.length; i++) {
            String s = System.getenv("COB_SWITCH_" + i);
            ON[i] = s != null && (s.startsWith("1") || s.equalsIgnoreCase("ON"));
        }
    }

    private Switches() {}

    public static boolean get(int n) {
        return ON[n];
    }

    public static void set(int n, boolean on) {
        ON[n] = on;
    }
}
