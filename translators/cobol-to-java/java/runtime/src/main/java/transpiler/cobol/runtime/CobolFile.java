package transpiler.cobol.runtime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A SEQUENTIAL or LINE SEQUENTIAL file, with GnuCOBOL 3.2's FILE STATUS values.
 *
 * <p>LINE SEQUENTIAL records are lines: READ fills the record area with the line's characters
 * and spaces (a line longer than the record is returned in pieces, status 06), WRITE writes the
 * record without trailing spaces plus a newline (a record holding a control character is not
 * written: status 71). SEQUENTIAL records are the record area's bytes. A READ after the end of
 * file gives status 46.
 *
 * <p>Unsuccessful statuses are handled as {@link FileBase} describes.
 */
public final class CobolFile extends FileBase {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int IO = 2;
    public static final int EXTEND = 3;

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

    public CobolFile(String name, String path, boolean lineSequential, AlphanumericField record, AlphanumericField status) {
        super(name, path, status);
        this.lineSequential = lineSequential;
        this.record = record;
    }

    public void open(int openMode) {
        if (mode != CLOSED) {
            finish("41", false);
            return;
        }
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
            mode = openMode;
            atEnd = false;
            lastRecord = -1;
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
            file.close();
            mode = CLOSED;
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

    /** WRITE of {@code data} (a record of this file). */
    public void write(AlphanumericField data) {
        if (mode != OUTPUT && mode != EXTEND) {
            finish("48", false);
            return;
        }
        try {
            flushRead();
            if (lineSequential) {
                String s = data.get();
                if (s.chars().anyMatch(ch -> ch < ' ')) {
                    // GnuCOBOL 3.2 validates LINE SEQUENTIAL data: a control character is not written.
                    finish("71", false);
                    return;
                }
                int end = s.length();
                while (end > 0 && s.charAt(end - 1) == ' ') {
                    end--;
                }
                file.write((s.substring(0, end) + "\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            } else {
                file.write(data.storage().bytes, data.offset(), data.length());
            }
            finish("00", false);
        } catch (IOException e) {
            finish("30", false);
        }
    }

    /** WRITE of {@code data} moved (as alphanumeric data) into the record area: SORT ... GIVING. */
    public void writeRecord(String data) {
        record.set(data);
        write(record);
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
