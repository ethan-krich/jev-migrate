package transpiler.cobol.runtime;

/** Unwinds the whole run unit for STOP RUN (and GOBACK in the main program). */
public final class StopRun extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;

    public StopRun(int status) {
        super(null, null, false, false);
        this.status = status;
    }

    /** RETURN-CODE of the program that ended the run unit. */
    public int status() {
        return status;
    }
}
