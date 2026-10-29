package transpiler.cobol.runtime;

import java.util.Arrays;

/**
 * The active PERFORMs of a program, innermost last, each holding the procedure whose end
 * returns from it. As in GnuCOBOL only the innermost PERFORM is checked when a procedure ends,
 * so a GO TO out of a performed range leaves that PERFORM waiting for its end procedure.
 */
public final class PerformStack {
    private int[] ends = new int[16];
    private int depth;

    public void push(int end) {
        if (depth == ends.length) {
            ends = Arrays.copyOf(ends, depth * 2);
        }
        ends[depth++] = end;
    }

    /** Whether procedure {@code procedure} ending returns from the innermost PERFORM (which is then removed). */
    public boolean returnsAt(int procedure) {
        if (depth > 0 && ends[depth - 1] == procedure) {
            depth--;
            return true;
        }
        return false;
    }

    /** Discards every pending PERFORM; called when a program invocation starts, as a GOBACK from inside a PERFORM leaves its marker. */
    public void reset() {
        depth = 0;
    }

    public int depth() {
        return depth;
    }
}
