package transpiler.cobol.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntSupplier;

/**
 * A SEQUENTIAL or LINE SEQUENTIAL file, with GnuCOBOL 3.2's FILE STATUS values.
 *
 * <p>LINE SEQUENTIAL records are lines: READ fills the record area with the line's characters
 * and spaces (a line longer than the record is returned in pieces, status 06), WRITE writes the
 * record without trailing spaces plus a newline (a record holding a control character is not
 * written: status 71). SEQUENTIAL records are the record area's bytes. A READ after the end of
 * file gives status 46.
 *
 * <p>WRITE ... ADVANCING writes GnuCOBOL's line control bytes before or after the record (a
 * newline per line, a carriage return for 0 lines, a form feed for PAGE); a file last written
 * AFTER ADVANCING gets a final newline when it is closed. With LINAGE, a LINE SEQUENTIAL file
 * is paged as GnuCOBOL pages it: LINAGE-COUNTER, the footing (END-OF-PAGE), and the lines at
 * the top and bottom of each page.
 *
 * <p>Unsuccessful statuses are handled as {@link FileBase} describes.
 */
public final class CobolFile extends FileBase {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int IO = 2;
    public static final int EXTEND = 3;
    /** WRITE ... ADVANCING options, as GnuCOBOL encodes them: a line count (0 to 65535) plus these flags. */
    public static final int LINES = 0x10000;
    public static final int PAGE = 0x20000;
    public static final int AFTER = 0x100000;
    public static final int BEFORE = 0x200000;
    private static final int COUNT = 0xFFFF;

    private final boolean lineSequential;
    private final AlphanumericField record;
    private RandomAccessFile file;
    private final byte[] buffer = new byte[8192];
    private int bufferStart;
    private int bufferEnd;
    private long bufferPosition;
    private int mode = CLOSED;
    private boolean atEnd;
    private long lastRecord = -1;
    /** The last WRITE advanced after its record: CLOSE ends the line (GnuCOBOL's flag_needs_nl). */
    private boolean needsNewline;
    // LINAGE
    private IntSupplier linage;
    private IntSupplier footing;
    private IntSupplier top;
    private IntSupplier bottom;
    private NumericField counter;
    private int linageLines;
    private int linageFooting;
    private int linageTop;
    private int linageBottom;
    private boolean needsTop;
    private boolean endOfPage;

    public CobolFile(String name, String path, boolean lineSequential, AlphanumericField record, AlphanumericField status) {
        super(name, path, status);
        this.lineSequential = lineSequential;
        this.record = record;
    }

    /**
     * LINAGE IS {@code lines} [WITH FOOTING AT {@code footing}] [LINES AT TOP {@code top}] [LINES
     * AT BOTTOM {@code bottom}] (null when absent), read at OPEN and at each new page;
     * {@code counter} is the file's LINAGE-COUNTER.
     */
    public CobolFile linage(IntSupplier lines, IntSupplier footing, IntSupplier top, IntSupplier bottom, NumericField counter) {
        this.linage = lines;
        this.footing = footing;
        this.top = top;
        this.bottom = bottom;
        this.counter = counter;
        return this;
    }

    /** {@code ADVANCING n LINES} with n from an item: GnuCOBOL adds it to the option bits, so it must be 0 to 65535. */
    public static int lines(int n) {
        if (n < 0 || n > COUNT) {
            Bounds.fail("WRITE ... ADVANCING " + n + " LINES is out of range");
        }
        return n;
    }

    public void open(int openMode) {
        if (mode != CLOSED) {
            finish("41", false);
            return;
        }
        lastOpenMode = openMode;
        try {
            Path p = Path.of(path);
            if (openMode != OUTPUT && !Files.exists(p)) {
                finish("35", false);
                return;
            }
            file = new RandomAccessFile(p.toFile(), openMode == INPUT ? "r" : "rw");
            if (openMode == OUTPUT) {
                file.setLength(0);
            } else if (openMode == EXTEND) {
                file.seek(file.length());
            }
            bufferStart = bufferEnd = 0;
            bufferPosition = file.getFilePointer();
            if (linage != null) {
                if (!linageValid()) {
                    file.close();
                    finish("57", false);
                    return;
                }
                needsTop = true;
                counter.move(BigDecimal.ONE);
            }
            mode = openMode;
            atEnd = false;
            lastRecord = -1;
            opened(this);
            finish("00", false);
        } catch (IOException e) {
            finish("37", false);
        }
    }

    public void close() {
        if (mode == CLOSED) {
            finish("42", false);
            return;
        }
        try {
            endLine();
            file.close();
            mode = CLOSED;
            closed(this);
            finish("00", false);
        } catch (IOException e) {
            mode = CLOSED;
            finish("30", false);
        }
    }

    /**
     * READ [NEXT]: true at end of file (status 10). {@code atEndPhrase}: the statement has AT END,
     * which handles that condition.
     */
    public boolean read(boolean atEndPhrase) {
        if (mode != INPUT && mode != IO) {
            finish("47", false);
            return false;
        }
        try {
            if (atEnd) {
                // A READ after the end of file is not an AT END condition.
                finish("46", false);
                return false;
            }
            long start = position();
            String s = lineSequential ? readLine() : readRecord();
            if (s == null) {
                atEnd = true;
                return atEnd("10", atEndPhrase);
            }
            lastRecord = start;
            record.storage().put(record.offset(), record.length(), s);
            finish(lineSequential && truncated ? "06" : "00", false);
            return false;
        } catch (IOException e) {
            finish("30", false);
            return false;
        }
    }

    /** The record area, for READ ... INTO. */
    public String data() {
        return record.get();
    }

    /** WRITE of {@code data} (a record of this file) without ADVANCING: a LINE SEQUENTIAL record is a line. */
    public void write(AlphanumericField data) {
        write(data, lineSequential ? BEFORE | LINES | 1 : 0, false);
    }

    /**
     * WRITE of {@code data} with ADVANCING {@code options} ({@link #AFTER} / {@link #BEFORE} with
     * {@link #PAGE} or {@link #LINES} plus a count): true at END-OF-PAGE, which is only reported
     * when the statement has the phrase ({@code endOfPagePhrase}).
     */
    public boolean write(AlphanumericField data, int options, boolean endOfPagePhrase) {
        if (mode != OUTPUT && mode != EXTEND) {
            finish("48", false);
            return false;
        }
        endOfPage = false;
        try {
            flushRead();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String code = lineSequential ? lineRecord(data, options, endOfPagePhrase, out) : sequentialRecord(data, options, out);
            file.write(out.toByteArray());
            finish(code, false);
            return code.equals("00") && endOfPage;
        } catch (IOException e) {
            finish("30", false);
            return false;
        }
    }

    /** GnuCOBOL's lineseq_write: the bytes of one LINE SEQUENTIAL WRITE, and its status. */
    private String lineRecord(AlphanumericField data, int options, boolean checkEndOfPage, ByteArrayOutputStream out) {
        if (linage != null && needsTop) {
            needsTop = false;
            newlines(out, linageTop);
        }
        if ((options & AFTER) != 0) {
            String code = advance(options, checkEndOfPage, out);
            if (code != null) {
                return code;
            }
            needsNewline = true;
        }
        String s = data.get();
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        for (int i = 0; i < end; i++) {
            if (s.charAt(i) < ' ') {
                // GnuCOBOL 3.2 validates LINE SEQUENTIAL data: a control character is not written.
                return "71";
            }
        }
        out.writeBytes(s.substring(0, end).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        if (linage != null) {
            out.write('\n');
        } else if (options == 0) {
            out.write('\n');
            needsNewline = false;
        }
        if ((options & BEFORE) != 0) {
            String code = advance(options, checkEndOfPage, out);
            if (code != null) {
                return code;
            }
            needsNewline = false;
        }
        return "00";
    }

    /** GnuCOBOL's sequential_write: the record's bytes with the ADVANCING control bytes before or after them. */
    private String sequentialRecord(AlphanumericField data, int options, ByteArrayOutputStream out) {
        if ((options & AFTER) != 0) {
            controlBytes(options, out);
            needsNewline = true;
        }
        out.write(data.storage().bytes, data.offset(), data.length());
        if ((options & BEFORE) != 0) {
            controlBytes(options, out);
            needsNewline = false;
        }
        return "00";
    }

    /** The ADVANCING of a LINE SEQUENTIAL WRITE: LINAGE paging, or plain control bytes; a status when it fails. */
    private String advance(int options, boolean checkEndOfPage, ByteArrayOutputStream out) {
        if (linage == null) {
            controlBytes(options, out);
            return null;
        }
        if ((options & PAGE) != 0) {
            int line = counter.intValue();
            if (line == 0) {
                return "57";
            }
            newlines(out, linageLines - line);
            newlines(out, linageBottom);
            if (!linageValid()) {
                return "57";
            }
            newlines(out, linageTop);
            counter.move(BigDecimal.ONE);
        } else if ((options & LINES) != 0) {
            int from = counter.intValue();
            if (from == 0) {
                return "57";
            }
            counter.move(counter.get().add(BigDecimal.valueOf(options & COUNT)));
            int line = counter.intValue();
            if (checkEndOfPage && linageFooting != 0 && line >= linageFooting) {
                endOfPage = true;
            }
            if (line > linageLines) {
                if (checkEndOfPage) {
                    endOfPage = true;
                }
                newlines(out, linageLines - from);
                newlines(out, linageBottom);
                if (!linageValid()) {
                    return "57";
                }
                counter.move(BigDecimal.ONE);
                newlines(out, linageTop);
            } else {
                newlines(out, (options & COUNT) - 1);
            }
        }
        return null;
    }

    /** GnuCOBOL's file_linage_check: reads the LINAGE values; invalid ones set LINAGE-COUNTER to 0. */
    private boolean linageValid() {
        linageLines = linage.getAsInt();
        linageFooting = footing == null ? 0 : footing.getAsInt();
        linageTop = top == null ? 0 : top.getAsInt();
        linageBottom = bottom == null ? 0 : bottom.getAsInt();
        if (linageLines < 1 || footing != null && (linageFooting < 1 || linageFooting > linageLines) || linageTop < 0 || linageBottom < 0) {
            counter.move(BigDecimal.ZERO);
            return false;
        }
        return true;
    }

    /** A carriage return for 0 lines, else a newline per line; a form feed for PAGE. */
    private static void controlBytes(int options, ByteArrayOutputStream out) {
        if ((options & LINES) != 0) {
            if ((options & COUNT) == 0) {
                out.write('\r');
            } else {
                newlines(out, options & COUNT);
            }
        } else if ((options & PAGE) != 0) {
            out.write('\f');
        }
    }

    private static void newlines(ByteArrayOutputStream out, int n) {
        for (int i = 0; i < n; i++) {
            out.write('\n');
        }
    }

    /** At CLOSE (or the implicit close at the end of the run unit): the newline a WRITE ... AFTER ADVANCING left pending. */
    private void endLine() throws IOException {
        if (needsNewline) {
            needsNewline = false;
            if (!lineSequential || linage == null) {
                flushRead();
                file.write('\n');
            }
        }
    }

    @Override
    void closeAtEnd() {
        try {
            endLine();
            file.close();
        } catch (IOException e) {
            // the run unit is ending
        }
        mode = CLOSED;
    }

    /** WRITE of {@code data} moved (as alphanumeric data) into the record area: SORT ... GIVING. */
    public void writeRecord(String data) {
        record.set(data);
        write(record);
    }

    @Override
    boolean sortRead() {
        return read(true);
    }

    @Override
    void sortWrite(String data) {
        writeRecord(data);
    }

    /** REWRITE of the record last read (SEQUENTIAL files opened I-O). */
    public void rewrite(AlphanumericField data) {
        if (mode != IO) {
            finish("49", false);
            return;
        }
        if (lastRecord < 0) {
            finish("43", false);
            return;
        }
        try {
            long back = position();
            file.seek(lastRecord);
            file.write(data.storage().bytes, data.offset(), data.length());
            file.seek(back);
            bufferStart = bufferEnd = 0;
            bufferPosition = back;
            lastRecord = -1;
            finish("00", false);
        } catch (IOException e) {
            finish("30", false);
        }
    }

    private boolean truncated;

    // ------------------------------------------------------------- buffered reading

    private long position() {
        return bufferPosition + bufferStart;
    }

    private int next() throws IOException {
        if (bufferStart == bufferEnd) {
            bufferPosition = file.getFilePointer();
            bufferEnd = Math.max(0, file.read(buffer));
            bufferStart = 0;
            if (bufferEnd == 0) {
                return -1;
            }
        }
        return buffer[bufferStart++] & 0xFF;
    }

    private int peek() throws IOException {
        int c = next();
        if (c >= 0) {
            bufferStart--;
        }
        return c;
    }

    private void flushRead() throws IOException {
        if (bufferStart != bufferEnd) {
            file.seek(position());
        }
        bufferStart = bufferEnd = 0;
        bufferPosition = file.getFilePointer();
    }

    /** The next line, at most the record's length: the rest of a longer line is the next record. */
    private String readLine() throws IOException {
        truncated = false;
        StringBuilder sb = new StringBuilder();
        int c = next();
        if (c < 0) {
            return null;
        }
        while (c >= 0 && c != '\n') {
            sb.append((char) c);
            if (sb.length() == record.length()) {
                int after = peek();
                if (after == '\n') {
                    next();
                } else if (after >= 0) {
                    truncated = true;
                }
                return sb.toString();
            }
            c = next();
        }
        return sb.toString();
    }

    private String readRecord() throws IOException {
        StringBuilder sb = new StringBuilder(record.length());
        for (int i = 0; i < record.length(); i++) {
            int c = next();
            if (c < 0) {
                return sb.isEmpty() ? null : sb.toString();
            }
            sb.append((char) c);
        }
        return sb.toString();
    }
}
