package transpiler.cobol2java.protocol;

/** Constants of the newline-delimited JSON worker protocol (see protocol/worker.schema.json). */
public final class Protocol {
    public static final String VERSION = "transpiler-worker/1";

    public static final String STATUS_OK = "ok";
    public static final String STATUS_INCOMPLETE = "incomplete";
    public static final String STATUS_ERROR = "error";

    private Protocol() {}
}
