package transpiler.cobol.runtime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A RELATIVE file in GnuCOBOL 3.2's format and with its behaviour: record number {@code n} is the
 * slot at {@code (n - 1) * (8 + record length)}, holding the record's size as a 64-bit
 * little-endian number (0 for no record) and then the record. Like GnuCOBOL, READ NEXT / PREVIOUS,
 * a sequential WRITE and REWRITE work from the file position the previous statement left, so
 * the results match GnuCOBOL's in every case, including its particular ones (DELETE of a deleted
 * record succeeds; READ PREVIOUS right after OPEN or START reads forward).
 */
public final class RelativeFile extends FileBase {
    private final AlphanumericField record;
    private final int access;
    private final NumericField key;
    private final long slotSize;

    private int mode = CLOSED;
    private RandomAccessFile file;
    private long position;
    private boolean readDone;
    private int firstRead;
    private boolean endOfFile;
    private boolean beginOfFile;

    /** @param key the RELATIVE KEY item, or null (ACCESS SEQUENTIAL without one) */
    public RelativeFile(String name, String path, AlphanumericField record, AlphanumericField status, int access, NumericField key) {
        super(name, path, status);
        this.record = record;
        this.access = access;
        this.key = key;
        this.slotSize = 8L + record.length();
    }

    public void open(int openMode) {
        if (lockedOut()) {
            return;
        }
        if (mode != CLOSED) {
            finish("41", false);
            return;
        }
        lastOpenMode = openMode;
        readDone = false;
        endOfFile = false;
        beginOfFile = false;
        firstRead = 2;
        Path p = Path.of(path);
        if (openMode != CobolFile.OUTPUT && !Files.exists(p)) {
            finish("35", false);
            return;
        }
        if (Files.isDirectory(p)) {
            finish("37", false);
            return;
        }
        try {
            file = new RandomAccessFile(p.toFile(), openMode == CobolFile.INPUT ? "r" : "rw");
            if (openMode == CobolFile.OUTPUT) {
                file.setLength(0);
            }
            position = openMode == CobolFile.EXTEND ? file.length() : 0;
        } catch (IOException e) {
            finish(!Files.isReadable(p) || openMode != CobolFile.INPUT && !Files.isWritable(p) ? "37" : "30", false);
            return;
        }
        mode = openMode;
        finish("00", false);
    }

    public void close() {
        if (closeLocked()) {
            return;
        }
        readDone = false;
        if (mode == CLOSED) {
            finish("42", false);
            return;
        }
        try {
            file.close();
        } catch (IOException e) {
            // closed either way
        }
        file = null;
        mode = CLOSED;
        finish("00", false);
    }

    /** READ [NEXT] / PREVIOUS: true for an AT END condition (status 1x). */
    public boolean readNext(boolean previous, boolean atEndPhrase) {
        readDone = false;
        if (mode != CobolFile.INPUT && mode != CobolFile.IO) {
            return atEnd("47", atEndPhrase);
        }
        if (endOfFile && !previous || beginOfFile && previous) {
            return atEnd("46", atEndPhrase);
        }
        String code = guarded(() -> readNextRecord(previous));
        if (code.equals("00")) {
            firstRead = 0;
            readDone = true;
            endOfFile = false;
            beginOfFile = false;
        } else if (code.equals("10")) {
            if (previous) {
                beginOfFile = true;
            } else {
                endOfFile = true;
            }
        }
        return atEnd(code, atEndPhrase);
    }

    /** READ of the record the RELATIVE KEY names: true for INVALID KEY. */
    public boolean read(boolean invalidKeyPhrase) {
        readDone = false;
        if (mode != CobolFile.INPUT && mode != CobolFile.IO) {
            return invalidKey("47", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            long number = key.intValue() - 1L;
            if (number < 0) {
                return "23";
            }
            long offset = number * slotSize;
            position = offset;
            Long size = readSize();
            if (size == null) {
                return "23";
            }
            if (size == 0) {
                position = offset;
                return "23";
            }
            return readData() ? "00" : "30";
        });
        if (code.equals("00")) {
            firstRead = 0;
            readDone = true;
            endOfFile = false;
            beginOfFile = false;
        }
        return invalidKey(code, invalidKeyPhrase);
    }

    /** START {@code condition} (an {@link IndexedFile} condition) on the RELATIVE KEY: true for INVALID KEY. */
    public boolean start(int condition, boolean invalidKeyPhrase) {
        readDone = false;
        firstRead = 0;
        if (mode != CobolFile.IO && mode != CobolFile.INPUT || access == IndexedFile.RANDOM) {
            return invalidKey("47", invalidKeyPhrase);
        }
        String code = guarded(() -> startAt(condition));
        endOfFile = !code.equals("00");
        beginOfFile = false;
        firstRead = 1;
        return invalidKey(code, invalidKeyPhrase);
    }

    /** WRITE of {@code data} (a record of this file): true for INVALID KEY. */
    public boolean write(AlphanumericField data, boolean invalidKeyPhrase) {
        readDone = false;
        boolean allowed = access == IndexedFile.SEQUENTIAL ? mode == CobolFile.OUTPUT || mode == CobolFile.EXTEND
                : mode == CobolFile.OUTPUT || mode == CobolFile.IO;
        if (!allowed) {
            return invalidKey("48", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            long offset;
            if (access != IndexedFile.SEQUENTIAL) {
                long number = key.intValue() - 1L;
                if (number < 0) {
                    return "24";
                }
                offset = number * slotSize;
                position = offset;
                Long size = readSize();
                if (size != null && size > 0) {
                    return "22";
                }
            } else {
                offset = mode == CobolFile.EXTEND ? file.length() : position;
            }
            position = offset;
            writeSize(record.length());
            writeData();
            if (access == IndexedFile.SEQUENTIAL && key != null) {
                key.move(BigDecimal.valueOf((offset + slotSize) / slotSize));
            }
            return "00";
        });
        return invalidKey(code, invalidKeyPhrase);
    }

    /** REWRITE of {@code data}: true for INVALID KEY. */
    public boolean rewrite(AlphanumericField data, boolean invalidKeyPhrase) {
        boolean read = readDone;
        readDone = false;
        if (mode != CobolFile.IO) {
            return invalidKey("49", invalidKeyPhrase);
        }
        if (access == IndexedFile.SEQUENTIAL && !read) {
            return invalidKey("43", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            if (access == IndexedFile.SEQUENTIAL) {
                seek(position - record.length());
            } else {
                long number = key.intValue() - 1L;
                if (number < 0) {
                    return "24";
                }
                position = number * slotSize;
                if (readSize() == null) {
                    return "23";
                }
            }
            writeData();
            return "00";
        });
        return invalidKey(code, invalidKeyPhrase);
    }

    /** DELETE of the record the RELATIVE KEY names: true for INVALID KEY. */
    public boolean delete(boolean invalidKeyPhrase) {
        boolean read = readDone;
        readDone = false;
        if (mode != CobolFile.IO) {
            return invalidKey("49", invalidKeyPhrase);
        }
        if (access == IndexedFile.SEQUENTIAL && !read) {
            return invalidKey("43", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            long number = key.intValue() - 1L;
            if (number < 0) {
                return "24";
            }
            long offset = number * slotSize;
            position = offset;
            if (readSize() == null) {
                return "23";
            }
            position = offset;
            writeSize(0);
            position += record.length();
            return "00";
        });
        return invalidKey(code, invalidKeyPhrase);
    }

    /** The record area, for READ ... INTO. */
    public String data() {
        return record.get();
    }

    // ------------------------------------------------------------- GnuCOBOL's relative handler

    private interface Io {
        String run() throws IOException;
    }

    private static String guarded(Io io) {
        try {
            return io.run();
        } catch (IOException e) {
            return "30";
        }
    }

    private String readNextRecord(boolean previous) throws IOException {
        long length = file.length();
        if (length == 0) {
            return "10";
        }
        if (length < slotSize) {
            return "30";
        }
        long offset = position;
        boolean back = false;
        if (previous && firstRead == 0) {
            if (offset <= slotSize) {
                return "10";
            }
            offset = seek(offset - 2 * slotSize);
            back = true;
        }
        while (true) {
            Long size = readSize();
            if (size == null) {
                if (partial) {
                    return "30";
                }
                break;
            }
            if (size > 0) {
                if (!readData()) {
                    return "30";
                }
                if (key != null) {
                    key.move(BigDecimal.ZERO);
                    if (key.store(BigDecimal.valueOf(offset / slotSize + 1), false, true)) {
                        position = offset;
                        return "14";
                    }
                }
                if (back) {
                    seek(offset - slotSize);
                }
                return "00";
            }
            if (back) {
                if (offset <= slotSize) {
                    break;
                }
                offset = seek(offset - 2 * slotSize);
            } else {
                position += record.length();
                offset = position;
            }
        }
        return "10";
    }

    private String startAt(int condition) throws IOException {
        long length = file.length();
        if (length == 0) {
            return "23";
        }
        long index = key.intValue() - 1L;
        int cond = condition;
        if (cond == IndexedFile.LT || cond == IndexedFile.LE) {
            long lastIndex = length / slotSize - 1;
            if (index > lastIndex) {
                index = lastIndex;
                if (cond == IndexedFile.LT) {
                    index++;
                }
            }
        }
        if (index < 0) {
            if (cond == IndexedFile.GE) {
                index = 0;
            } else if (cond == IndexedFile.GT) {
                index = -1;
            } else {
                return "23";
            }
        }
        if (cond == IndexedFile.LT) {
            index--;
            if (index < 0) {
                return "23";
            }
        } else if (cond == IndexedFile.GT) {
            index++;
        }
        while (index >= 0) {
            long offset = index * slotSize;
            if (offset >= length) {
                if (cond == IndexedFile.LT || cond == IndexedFile.LE) {
                    index--;
                    continue;
                }
                break;
            }
            position = offset;
            Long size = readSize();
            if (size != null && size > 0) {
                position = offset;
                return "00";
            }
            switch (cond) {
                case IndexedFile.EQ -> {
                    return "23";
                }
                case IndexedFile.LT, IndexedFile.LE -> index--;
                default -> index++;
            }
        }
        return "23";
    }

    /** lseek: a negative offset fails (-1) and leaves the position. */
    private long seek(long offset) {
        if (offset < 0) {
            return -1;
        }
        position = offset;
        return offset;
    }

    /** Whether the last {@link #readSize} found only part of a size field before the end of the file. */
    private boolean partial;

    /** The size field at the position (advancing past what was read), or null when the file ends before it. */
    private Long readSize() throws IOException {
        byte[] b = new byte[8];
        file.seek(position);
        int n = Math.max(0, file.read(b));
        position += n;
        partial = n > 0 && n < 8;
        if (n < 8) {
            return null;
        }
        long size = 0;
        for (int i = 7; i >= 0; i--) {
            size = size << 8 | (b[i] & 0xFF);
        }
        return size;
    }

    /** Reads the record at the position into the record area (as far as the file goes); false when it is cut short. */
    private boolean readData() throws IOException {
        file.seek(position);
        byte[] b = new byte[record.length()];
        int n = Math.max(0, file.read(b));
        position += n;
        System.arraycopy(b, 0, record.storage().bytes, record.offset(), n);
        return n == b.length;
    }

    private void writeSize(long size) throws IOException {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (size >>> 8 * i);
        }
        put(b);
    }

    private void writeData() throws IOException {
        put(java.util.Arrays.copyOfRange(record.storage().bytes, record.offset(), record.offset() + record.length()));
    }

    /** Writes at the position; a gap before it reads back as empty slots (zeros), as on POSIX. */
    private void put(byte[] b) throws IOException {
        long length = file.length();
        if (position > length) {
            file.seek(length);
            file.write(new byte[(int) (position - length)]);
        }
        file.seek(position);
        file.write(b);
        position += b.length;
    }

    @Override
    boolean sortRead() {
        return readNext(false, true) || !succeeded();
    }

    @Override
    void sortWrite(String data) {
        record.set(data);
        write(record, true);
    }
}
