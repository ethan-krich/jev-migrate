package transpiler.cobol.runtime;

/**
 * What every file organisation shares: the FILE STATUS item and GnuCOBOL 3.2's handling of an
 * unsuccessful status. A status that is not successful and that the statement does not handle
 * (AT END, INVALID KEY) ends the run unit with status 1, unless the file has a FILE STATUS item.
 */
abstract class FileBase {
    static final int CLOSED = -1;
    /** Files open in the run unit, closed implicitly when it ends (as GnuCOBOL does at STOP RUN). */
    private static final java.util.Set<FileBase> OPEN = new java.util.LinkedHashSet<>();

    final String name;
    final String path;
    final AlphanumericField status;
    private String lastStatus = "00";

    FileBase(String name, String path, AlphanumericField status) {
        this.name = name;
        this.path = path;
        this.status = status;
    }

    static void opened(FileBase f) {
        OPEN.add(f);
    }

    static void closed(FileBase f) {
        OPEN.remove(f);
    }

    /** The implicit CLOSE of every file still open when the run unit ends (no FILE STATUS is set). */
    static void closeAll() {
        for (FileBase f : java.util.List.copyOf(OPEN)) {
            f.closeAtEnd();
        }
        OPEN.clear();
    }

    /** What an implicit CLOSE at the end of the run unit must still write. */
    void closeAtEnd() {}

    /** Whether the last statement on this file succeeded (status 0x): READ ... INTO, NOT AT END, NOT INVALID KEY. */
    public boolean succeeded() {
        return lastStatus.charAt(0) == '0';
    }

    /** Records {@code code}; {@code handled}: the statement has the phrase for this condition. */
    final void finish(String code, boolean handled) {
        lastStatus = code;
        if (status != null) {
            status.set(code);
        }
        if (code.charAt(0) != '0' && !handled && status == null) {
            Bounds.fail(describe(code) + " (status = " + code + ") for file " + name + " ('" + path + "')");
        }
    }

    /** Records {@code code} for a statement with AT END: true when it is an AT END condition (status 1x). */
    final boolean atEnd(String code, boolean atEndPhrase) {
        boolean atEnd = code.charAt(0) == '1';
        finish(code, atEnd && atEndPhrase);
        return atEnd;
    }

    /** Records {@code code} for a statement with INVALID KEY: true when it is an INVALID KEY condition (status 2x). */
    final boolean invalidKey(String code, boolean invalidKeyPhrase) {
        boolean invalid = code.charAt(0) == '2';
        finish(code, invalid && invalidKeyPhrase);
        return invalid;
    }

    private static String describe(String code) {
        return switch (code) {
            case "10" -> "end of file";
            case "14" -> "relative record number too large";
            case "21" -> "key order not ascending";
            case "22" -> "record key already exists";
            case "23" -> "key not exists";
            case "24" -> "key out of bounds";
            case "35" -> "file does not exist";
            case "37" -> "permission denied";
            case "39" -> "mismatch of fixed file attributes";
            case "41" -> "file already open";
            case "42" -> "file not open";
            case "43" -> "READ must be executed first";
            case "46" -> "READ after unsuccessful READ/START";
            case "47" -> "READ/START not allowed, file not open for input";
            case "48" -> "WRITE not allowed, file not open for output";
            case "49" -> "DELETE/REWRITE not allowed, file not open for I-O";
            case "71" -> "bad character in record";
            default -> "I/O error";
        };
    }
}
