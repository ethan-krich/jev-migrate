package transpiler.cobol.runtime;

/** Unwinds to the calling program for GOBACK / EXIT PROGRAM in a called program. */
public final class Goback extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public Goback() {
        super(null, null, false, false);
    }
}
