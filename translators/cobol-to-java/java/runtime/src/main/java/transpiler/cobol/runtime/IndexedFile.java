package transpiler.cobol.runtime;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.TreeSet;

/**
 * An INDEXED file with GnuCOBOL 3.2's behaviour (its Berkeley DB handler): the record key and
 * ALTERNATE RECORD KEYs [WITH DUPLICATES] are compared as bytes, duplicates come back in the
 * order they were written, and every status follows GnuCOBOL, including its particular cases
 * (no status 02; REWRITE of a missing record is 21; in ACCESS SEQUENTIAL, REWRITE and DELETE act
 * on the record last read, whatever the record key in the record area now holds).
 *
 * <p>The position for READ NEXT / PREVIOUS is the key last read (with its duplicate number), as
 * in GnuCOBOL, so records written or deleted in between are seen or skipped accordingly.
 *
 * <p>The file is this runtime's own format, not GnuCOBOL's: a header describing the record and
 * keys, then one slot per record ({@code 1} live / {@code 0} free, the duplicate numbers of the
 * alternate keys, the record's bytes). Every change is written through; the indexes are rebuilt
 * when the file is opened.
 */
public final class IndexedFile extends FileBase {
    public static final int SEQUENTIAL = 0;
    public static final int RANDOM = 1;
    public static final int DYNAMIC = 2;
    /** START conditions. */
    public static final int EQ = 0;
    public static final int LT = 1;
    public static final int LE = 2;
    public static final int GT = 3;
    public static final int GE = 4;

    private static final byte[] MAGIC = "CBLIDX01".getBytes(StandardCharsets.ISO_8859_1);

    /** An index entry: a key value, its duplicate number (0 for unique keys) and the record it belongs to. */
    private record Entry(byte[] key, int dupno, byte[] primary, long slot) {}

    private static int compareKeys(byte[] a, byte[] b) {
        return Arrays.compareUnsigned(a, b);
    }

    private static final java.util.Comparator<Entry> ORDER = (a, b) -> {
        int c = compareKeys(a.key, b.key);
        return c != 0 ? c : Integer.compare(a.dupno, b.dupno);
    };

    private final AlphanumericField record;
    private final int access;
    private final int[] offsets;
    private final int[] lengths;
    private final boolean[] duplicates;
    private final int nkeys;

    private int mode = CLOSED;
    private RandomAccessFile file;
    private int headerSize;
    private int slotSize;
    private List<TreeSet<Entry>> indexes;
    private final Deque<Long> freeSlots = new ArrayDeque<>();
    private long slotCount;
    // GnuCOBOL's per-file flags
    private boolean readDone;
    private int firstRead;
    private boolean endOfFile;
    private boolean beginOfFile;
    // GnuCOBOL's BDB handler state
    private int keyIndex;
    private byte[][] lastReadKey;
    private byte[][] lastReadPrimary;
    private int[] lastDupno;
    private boolean dataLoaded;
    private byte[] lastKey;
    private boolean[] rewriteSecondary;
    /** The record key of the record last read or positioned on (what DELETE / REWRITE use in ACCESS SEQUENTIAL). */
    private byte[] current;

    /**
     * @param keys for the record key then each alternate key: offset in the record, length, and 1
     *     when the key allows duplicates (0 otherwise)
     */
    public IndexedFile(String name, String path, AlphanumericField record, AlphanumericField status, int access, int... keys) {
        super(name, path, status);
        this.record = record;
        this.access = access;
        this.nkeys = keys.length / 3;
        this.offsets = new int[nkeys];
        this.lengths = new int[nkeys];
        this.duplicates = new boolean[nkeys];
        for (int i = 0; i < nkeys; i++) {
            offsets[i] = keys[3 * i];
            lengths[i] = keys[3 * i + 1];
            duplicates[i] = keys[3 * i + 2] != 0;
        }
    }

    // ------------------------------------------------------------- statements

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
        if (Files.isDirectory(p)) {
            finish("30", false);
            return;
        }
        if (openMode != CobolFile.OUTPUT && !Files.exists(p)) {
            finish("35", false);
            return;
        }
        try {
            file = new RandomAccessFile(p.toFile(), openMode == CobolFile.INPUT ? "r" : "rw");
            String problem = openMode == CobolFile.OUTPUT ? create() : load();
            if (problem != null) {
                file.close();
                file = null;
                finish(problem, false);
                return;
            }
        } catch (IOException e) {
            finish(!Files.isReadable(p) || openMode != CobolFile.INPUT && !Files.isWritable(p) ? "37" : "30", false);
            return;
        }
        keyIndex = 0;
        lastKey = null;
        lastReadKey = new byte[nkeys][];
        lastReadPrimary = new byte[nkeys][];
        lastDupno = new int[nkeys];
        rewriteSecondary = new boolean[nkeys];
        for (int i = 0; i < nkeys; i++) {
            lastReadKey[i] = new byte[lengths[i]];
            lastReadPrimary[i] = new byte[lengths[0]];
        }
        current = null;
        TreeSet<Entry> primary = indexes.getFirst();
        dataLoaded = !primary.isEmpty();
        if (dataLoaded) {
            lastReadKey[0] = primary.first().key;
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
            // GnuCOBOL's handler reports success once the file is closed.
        }
        file = null;
        indexes = null;
        mode = CLOSED;
        finish("00", false);
    }

    /** READ [NEXT] / PREVIOUS: true for an AT END condition. */
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

    /** READ by key ({@code key}: 0 for the record key, else the alternate key's number): true for INVALID KEY. */
    public boolean read(int key, boolean invalidKeyPhrase) {
        readDone = false;
        if (mode != CobolFile.INPUT && mode != CobolFile.IO) {
            return invalidKey("47", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            Entry found = start(EQ, key, lengths[key]);
            if (found == null) {
                return "23";
            }
            load(found);
            return "00";
        });
        if (code.equals("00")) {
            firstRead = 0;
            readDone = true;
            endOfFile = false;
            beginOfFile = false;
        }
        return invalidKey(code, invalidKeyPhrase);
    }

    /** START: {@code length} is the length of the key item named (a leading part of the key). True for INVALID KEY. */
    public boolean start(int condition, int key, int length, boolean invalidKeyPhrase) {
        readDone = false;
        firstRead = 0;
        if (mode != CobolFile.IO && mode != CobolFile.INPUT || access == RANDOM) {
            return invalidKey("47", invalidKeyPhrase);
        }
        String code = guarded(() -> start(condition, key, length) == null ? "23" : "00");
        endOfFile = !code.equals("00");
        beginOfFile = false;
        firstRead = 1;
        return invalidKey(code, invalidKeyPhrase);
    }

    /** WRITE of {@code data} (a record of this file): true for INVALID KEY. */
    public boolean write(AlphanumericField data, boolean invalidKeyPhrase) {
        readDone = false;
        boolean allowed = access == SEQUENTIAL ? mode == CobolFile.OUTPUT || mode == CobolFile.EXTEND
                : mode == CobolFile.OUTPUT || mode == CobolFile.IO;
        if (!allowed) {
            return invalidKey("48", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            byte[] key = keyOf(record, 0);
            if (lastKey != null && access == SEQUENTIAL && compareKeys(lastKey, key) > 0) {
                return "21";
            }
            lastKey = key;
            String status = writeRecord(false);
            return access == SEQUENTIAL && mode == CobolFile.OUTPUT && status.equals("22") ? "21" : status;
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
        if (access == SEQUENTIAL && !read) {
            return invalidKey("43", invalidKeyPhrase);
        }
        String code = guarded(() -> {
            if (alternateKeyTaken(true)) {
                return "22";
            }
            String status = deleteRecord(true);
            if (!status.equals("00")) {
                return status.equals("23") ? "21" : status;
            }
            return writeRecord(true);
        });
        return invalidKey(code, invalidKeyPhrase);
    }

    /** DELETE: true for INVALID KEY. */
    public boolean delete(boolean invalidKeyPhrase) {
        boolean read = readDone;
        readDone = false;
        if (mode != CobolFile.IO) {
            return invalidKey("49", invalidKeyPhrase);
        }
        if (access == SEQUENTIAL && !read) {
            return invalidKey("43", invalidKeyPhrase);
        }
        return invalidKey(guarded(() -> deleteRecord(false)), invalidKeyPhrase);
    }

    /** The record area, for READ ... INTO. */
    public String data() {
        return record.get();
    }

    // ------------------------------------------------------------- GnuCOBOL's handler

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

    /** The positioning of a START (and of a READ by key): the entry found, or null (status 23). */
    private Entry start(int condition, int key, int length) throws IOException {
        keyIndex = key;
        TreeSet<Entry> index = indexes.get(key);
        byte[] probe = Arrays.copyOf(keyOf(record, key), length);
        Entry at = range(index, probe);
        if (at != null) {
            dataLoaded = true;
        }
        switch (condition) {
            case EQ -> {
                if (at != null && comparePart(at.key, probe) != 0) {
                    at = null;
                }
            }
            case LT -> at = at == null ? last(index) : index.lower(at);
            case LE -> {
                if (at == null) {
                    at = last(index);
                } else if (comparePart(at.key, probe) != 0) {
                    at = index.lower(at);
                } else if (duplicates[key]) {
                    Entry next = index.higher(new Entry(at.key, Integer.MAX_VALUE, null, -1));
                    at = next == null ? last(index) : index.lower(next);
                }
            }
            case GT -> {
                while (at != null && comparePart(at.key, probe) == 0) {
                    at = index.higher(at);
                }
            }
            default -> {
                // GE: the entry found.
            }
        }
        if (at != null) {
            dataLoaded = true;
        }
        Entry found = at;
        if (at != null && key > 0) {
            found = exact(indexes.getFirst(), at.primary);
        }
        if (found == null) {
            return null;
        }
        remember(key, at);
        current = found.key;
        return found;
    }

    /** GnuCOBOL's READ NEXT / PREVIOUS through the current key of reference. */
    private String readNextRecord(boolean previous) throws IOException {
        int step = previous ? endOfFile ? LAST : PREV : beginOfFile ? FIRST : NEXT;
        TreeSet<Entry> index = indexes.get(keyIndex);
        byte[] last = lastReadKey[keyIndex];
        Entry result = null;
        boolean changed = false;
        if (firstRead != 0) {
            // The record positioned on by OPEN or START, if it still exists.
            if (!dataLoaded || firstRead == 2 && step == PREV) {
                return "10";
            }
            Entry at = exact(index, last);
            if (at != null && keyIndex > 0) {
                if (duplicates[keyIndex]) {
                    while (at != null && Arrays.equals(at.key, last) && at.dupno < lastDupno[keyIndex]) {
                        at = index.higher(at);
                    }
                    if (at == null || !Arrays.equals(at.key, last) || at.dupno != lastDupno[keyIndex]
                            || !Arrays.equals(at.primary, lastReadPrimary[keyIndex])) {
                        at = null;
                    }
                } else if (!Arrays.equals(at.primary, lastReadPrimary[keyIndex])) {
                    at = null;
                }
                at = at == null ? null : exact(indexes.getFirst(), lastReadPrimary[keyIndex]);
            }
            changed = at == null;
            result = at;
        }
        if (firstRead == 0 || changed) {
            Entry at = null;
            boolean move;
            if (step == FIRST || step == LAST) {
                move = true;
            } else {
                at = range(index, last);
                if (at == null) {
                    if (step != PREV) {
                        return "10";
                    }
                    step = LAST;
                    move = true;
                } else if (Arrays.equals(at.key, last)) {
                    if (keyIndex > 0 && duplicates[keyIndex]) {
                        while (at != null && Arrays.equals(at.key, last) && at.dupno < lastDupno[keyIndex]) {
                            at = index.higher(at);
                        }
                        if (at == null) {
                            if (step != PREV) {
                                return "10";
                            }
                            step = LAST;
                            move = true;
                        } else {
                            move = Arrays.equals(at.key, last) && at.dupno == lastDupno[keyIndex] || step == PREV;
                        }
                    } else {
                        move = true;
                    }
                } else {
                    move = step == PREV;
                }
            }
            if (move) {
                at = switch (step) {
                    case FIRST -> index.isEmpty() ? null : index.first();
                    case LAST -> last(index);
                    case NEXT -> index.higher(at);
                    default -> index.lower(at);
                };
                if (at == null) {
                    return "10";
                }
            }
            result = at;
            if (keyIndex > 0) {
                result = exact(indexes.getFirst(), at.primary);
                if (result == null) {
                    return "23";
                }
            }
            remember(keyIndex, at);
        }
        current = result.key;
        load(result);
        return "00";
    }

    private static final int NEXT = 0;
    private static final int PREV = 1;
    private static final int FIRST = 2;
    private static final int LAST = 3;

    /** The record key, alternate key and duplicate number GnuCOBOL keeps as the position. */
    private void remember(int key, Entry at) {
        lastReadKey[key] = at.key;
        if (key > 0) {
            lastReadPrimary[key] = at.primary;
            if (duplicates[key]) {
                lastDupno[key] = at.dupno;
            }
        }
    }

    /** Whether a unique alternate key of the record area belongs to another record already. */
    private boolean alternateKeyTaken(boolean rewrite) throws IOException {
        for (int i = 1; i < nkeys; i++) {
            if (!duplicates[i]) {
                Entry e = exact(indexes.get(i), keyOf(record, i));
                if (e != null) {
                    dataLoaded = true;
                    if (!rewrite || !Arrays.equals(e.primary, keyOf(record, 0))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String writeRecord(boolean rewrite) throws IOException {
        dataLoaded = true;
        if (!rewrite && nkeys > 1 && alternateKeyTaken(false)) {
            return "22";
        }
        byte[] primaryKey = keyOf(record, 0);
        if (exact(indexes.getFirst(), primaryKey) != null) {
            return "22";
        }
        int[] dupnos = new int[nkeys];
        for (int i = 1; i < nkeys; i++) {
            if (rewrite && !rewriteSecondary[i]) {
                dupnos[i] = -1;
            } else if (duplicates[i]) {
                byte[] alt = keyOf(record, i);
                Entry lastDup = indexes.get(i).lower(new Entry(alt, Integer.MAX_VALUE, null, -1));
                dupnos[i] = lastDup != null && Arrays.equals(lastDup.key, alt) ? lastDup.dupno + 1 : 1;
            }
        }
        long slot = freeSlots.isEmpty() ? slotCount++ : freeSlots.pop();
        byte[] bytes = Arrays.copyOfRange(record.storage().bytes, record.offset(), record.offset() + record.length());
        indexes.getFirst().add(new Entry(primaryKey, 0, primaryKey, slot));
        for (int i = 1; i < nkeys; i++) {
            if (dupnos[i] >= 0) {
                indexes.get(i).add(new Entry(keyOf(bytes, i), dupnos[i], primaryKey, slot));
            }
        }
        // Alternate entries kept by a REWRITE keep their duplicate numbers.
        if (rewrite) {
            for (int i = 1; i < nkeys; i++) {
                if (dupnos[i] < 0) {
                    dupnos[i] = keptDupno(i, primaryKey);
                }
            }
        }
        writeSlot(slot, true, dupnos, bytes);
        return "00";
    }

    /** The duplicate number of the entry a REWRITE kept (its key is unchanged). */
    private int keptDupno(int key, byte[] primaryKey) {
        TreeSet<Entry> index = indexes.get(key);
        byte[] alt = keyOf(record, key);
        for (Entry e = range(index, alt); e != null && Arrays.equals(e.key, alt); e = index.higher(e)) {
            if (Arrays.equals(e.primary, primaryKey)) {
                return e.dupno;
            }
        }
        return 0;
    }

    private String deleteRecord(boolean rewrite) throws IOException {
        byte[] primaryKey = access != SEQUENTIAL || current == null ? keyOf(record, 0) : current;
        Entry e = exact(indexes.getFirst(), primaryKey);
        if (e == null) {
            return "23";
        }
        dataLoaded = true;
        byte[] old = readSlot(e.slot);
        for (int i = 1; i < nkeys; i++) {
            byte[] oldKey = keyOf(old, i);
            if (rewrite) {
                rewriteSecondary[i] = !Arrays.equals(oldKey, keyOf(record, i));
                if (!rewriteSecondary[i]) {
                    continue;
                }
            }
            TreeSet<Entry> index = indexes.get(i);
            List<Entry> gone = new ArrayList<>();
            for (Entry a = range(index, oldKey); a != null && Arrays.equals(a.key, oldKey); a = index.higher(a)) {
                if (!duplicates[i] || Arrays.equals(a.primary, primaryKey)) {
                    gone.add(a);
                }
            }
            gone.forEach(index::remove);
        }
        indexes.getFirst().remove(e);
        writeSlot(e.slot, false, null, null);
        freeSlots.push(e.slot);
        return "00";
    }

    // ------------------------------------------------------------- indexes

    private static Entry range(TreeSet<Entry> index, byte[] key) {
        return index.ceiling(new Entry(key, -1, null, -1));
    }

    private static Entry exact(TreeSet<Entry> index, byte[] key) {
        Entry e = range(index, key);
        return e != null && Arrays.equals(e.key, key) ? e : null;
    }

    private static Entry last(TreeSet<Entry> index) {
        return index.isEmpty() ? null : index.last();
    }

    /** Compares the leading {@code probe.length} bytes of {@code key} with {@code probe}. */
    private static int comparePart(byte[] key, byte[] probe) {
        return Arrays.compareUnsigned(key, 0, Math.min(key.length, probe.length), probe, 0, Math.min(key.length, probe.length));
    }

    private byte[] keyOf(AlphanumericField area, int key) {
        int start = area.offset() + offsets[key];
        return Arrays.copyOfRange(area.storage().bytes, start, start + lengths[key]);
    }

    private byte[] keyOf(byte[] bytes, int key) {
        return Arrays.copyOfRange(bytes, offsets[key], offsets[key] + lengths[key]);
    }

    private void load(Entry e) throws IOException {
        byte[] bytes = readSlot(e.slot);
        System.arraycopy(bytes, 0, record.storage().bytes, record.offset(), bytes.length);
    }

    // ------------------------------------------------------------- the file

    private byte[] header() {
        ByteBuffer b = ByteBuffer.allocate(MAGIC.length + 8 + 9 * nkeys);
        b.put(MAGIC).putInt(record.length()).putInt(nkeys);
        for (int i = 0; i < nkeys; i++) {
            b.putInt(offsets[i]).putInt(lengths[i]).put((byte) (duplicates[i] ? 1 : 0));
        }
        return b.array();
    }

    private void layout() {
        headerSize = header().length;
        slotSize = 1 + 4 * (nkeys - 1) + record.length();
        indexes = new ArrayList<>();
        for (int i = 0; i < nkeys; i++) {
            indexes.add(new TreeSet<>(ORDER));
        }
        freeSlots.clear();
        slotCount = 0;
    }

    private String create() throws IOException {
        layout();
        file.setLength(0);
        file.write(header());
        return null;
    }

    /** Reads the header and rebuilds the indexes; a status when the file is not one of these. */
    private String load() throws IOException {
        layout();
        byte[] expected = header();
        byte[] actual = new byte[expected.length];
        if (file.length() < expected.length) {
            return "39";
        }
        file.seek(0);
        file.readFully(actual);
        if (!Arrays.equals(expected, actual) || (file.length() - headerSize) % slotSize != 0) {
            return "39";
        }
        slotCount = (file.length() - headerSize) / slotSize;
        byte[] slot = new byte[slotSize];
        for (long s = 0; s < slotCount; s++) {
            file.readFully(slot);
            if (slot[0] == 0) {
                freeSlots.add(s);
                continue;
            }
            ByteBuffer b = ByteBuffer.wrap(slot, 1, 4 * (nkeys - 1));
            byte[] bytes = Arrays.copyOfRange(slot, 1 + 4 * (nkeys - 1), slotSize);
            byte[] primaryKey = keyOf(bytes, 0);
            indexes.getFirst().add(new Entry(primaryKey, 0, primaryKey, s));
            for (int i = 1; i < nkeys; i++) {
                indexes.get(i).add(new Entry(keyOf(bytes, i), b.getInt(), primaryKey, s));
            }
        }
        return null;
    }

    private byte[] readSlot(long slot) throws IOException {
        byte[] bytes = new byte[record.length()];
        file.seek(headerSize + slot * slotSize + 1 + 4L * (nkeys - 1));
        file.readFully(bytes);
        return bytes;
    }

    private void writeSlot(long slot, boolean live, int[] dupnos, byte[] bytes) throws IOException {
        file.seek(headerSize + slot * slotSize);
        if (!live) {
            file.write(0);
            return;
        }
        ByteBuffer b = ByteBuffer.allocate(slotSize);
        b.put((byte) 1);
        for (int i = 1; i < nkeys; i++) {
            b.putInt(dupnos[i]);
        }
        b.put(bytes);
        file.write(b.array());
    }

    @Override
    boolean sortRead() {
        return sortQuietly(() -> readNext(false, true)) || !succeeded();
    }

    @Override
    void sortWrite(String data) {
        record.set(data);
        sortQuietly(() -> write(record, true));
    }
}
