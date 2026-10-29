package transpiler.cobol.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Environment variables and command-line arguments as GnuCOBOL 3.2 gives them to a program:
 * DISPLAY ... UPON ENVIRONMENT-NAME / ENVIRONMENT-VALUE, ACCEPT ... FROM ENVIRONMENT-VALUE /
 * ENVIRONMENT name, ARGUMENT-NUMBER, ARGUMENT-VALUE and COMMAND-LINE. Names and values are taken
 * without trailing spaces; a variable set by the program is seen by it from then on.
 */
public final class Environment {
    private static final Map<String, String> set = new HashMap<>();
    private static String[] arguments = new String[0];
    /** The next ARGUMENT-VALUE (1 is the first argument). */
    private static int current = 1;
    private static String name;

    private Environment() {}

    static void arguments(String[] args) {
        arguments = args.clone();
        current = 1;
    }

    /** DISPLAY ... UPON ENVIRONMENT-NAME. */
    public static void name(String value) {
        name = trimmed(value);
    }

    /** DISPLAY ... UPON ENVIRONMENT-VALUE: sets the variable named last (nothing when none is named). */
    public static void value(String value) {
        if (name != null && !name.isEmpty()) {
            set.put(name, trimmed(value));
        }
    }

    /** ACCEPT ... FROM ENVIRONMENT-VALUE: the variable named last, or null when it is not set. */
    public static String value() {
        return name == null ? null : get(name);
    }

    /** ACCEPT ... FROM ENVIRONMENT name: the variable, or null when it is not set. */
    public static String get(String variable) {
        String n = trimmed(variable);
        if (n.isEmpty()) {
            return null;
        }
        return set.containsKey(n) ? set.get(n) : System.getenv(n);
    }

    /** ACCEPT ... FROM ARGUMENT-NUMBER. */
    public static int argumentCount() {
        return arguments.length;
    }

    /** DISPLAY ... UPON ARGUMENT-NUMBER: the next ARGUMENT-VALUE is that argument (out of range: no change). */
    public static void argumentNumber(int n) {
        if (n >= 0 && n <= arguments.length) {
            current = n;
        }
    }

    /** ACCEPT ... FROM ARGUMENT-VALUE: the next argument, or null after the last (the item is left unchanged). */
    public static String nextArgument() {
        if (current > arguments.length) {
            return null;
        }
        if (current == 0) {
            Bounds.fail("ARGUMENT-VALUE 0 (the program's own path) has no target");
        }
        return arguments[current++ - 1];
    }

    /** ACCEPT ... FROM COMMAND-LINE: the arguments separated by spaces (a space when there are none). */
    public static String commandLine() {
        return arguments.length == 0 ? " " : String.join(" ", arguments);
    }

    private static String trimmed(String value) {
        int end = value.length();
        while (end > 0 && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == 0)) {
            end--;
        }
        return value.substring(0, end);
    }
}
