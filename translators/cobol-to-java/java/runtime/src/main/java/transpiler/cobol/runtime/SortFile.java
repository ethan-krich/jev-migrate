package transpiler.cobol.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * An SD sort file: SORT collects records (USING files or RELEASE in an INPUT PROCEDURE), orders
 * them by their keys and hands them out (GIVING files or RETURN in an OUTPUT PROCEDURE). The
 * sort is stable, so records with equal keys keep their release order, as in GnuCOBOL (with or
 * without WITH DUPLICATES IN ORDER). MERGE combines files already in key order, taking equal
 * keys in the order the files are named.
 */
public final class SortFile {
    private final String name;
    private final AlphanumericField record;
    private final List<Function<Storage, Comparable<?>>> keys = new ArrayList<>();
    private final List<Boolean> ascending = new ArrayList<>();
    private List<byte[]> records = new ArrayList<>();
    private int next;

    public SortFile(String name, AlphanumericField record) {
        this.name = name;
        this.record = record;
    }

    /** Starts a SORT or MERGE with no records and no keys. */
    public SortFile begin() {
        keys.clear();
        ascending.clear();
        records = new ArrayList<>();
        next = 0;
        return this;
    }

    /** A key: {@code key} reads it from a record's bytes (at offset 0 of the storage it is given). */
    public SortFile key(Function<Storage, Comparable<?>> key, boolean asc) {
        keys.add(key);
        ascending.add(asc);
        return this;
    }

    /** RELEASE: the record area as it is now. */
    public void release() {
        records.add(Arrays.copyOfRange(record.storage().bytes, record.offset(), record.offset() + record.length()));
    }

    /** USING: every record of {@code file}, which is opened INPUT for this and closed after. */
    public void using(CobolFile file) {
        file.open(CobolFile.INPUT);
        while (!file.read(true)) {
            record.set(file.data());
            release();
        }
        file.close();
    }

    /** Orders the records. */
    public void sort() {
        List<Comparable<?>[]> decorated = new ArrayList<>(records.size());
        for (byte[] r : records) {
            decorated.add(keysOf(r));
        }
        Integer[] order = new Integer[records.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> compare(decorated.get(a), decorated.get(b)));
        List<byte[]> sorted = new ArrayList<>(records.size());
        for (int i : order) {
            sorted.add(records.get(i));
        }
        records = sorted;
        next = 0;
    }

    /** MERGE ... USING: the files' records merged by key; equal keys come in the order of {@code files}. */
    public void merge(CobolFile... files) {
        List<List<byte[]>> inputs = new ArrayList<>();
        for (CobolFile f : files) {
            records = new ArrayList<>();
            using(f);
            inputs.add(records);
        }
        List<byte[]> merged = new ArrayList<>();
        int[] at = new int[inputs.size()];
        while (true) {
            int best = -1;
            Comparable<?>[] bestKeys = null;
            for (int i = 0; i < inputs.size(); i++) {
                if (at[i] < inputs.get(i).size()) {
                    Comparable<?>[] k = keysOf(inputs.get(i).get(at[i]));
                    if (best < 0 || compare(k, bestKeys) < 0) {
                        best = i;
                        bestKeys = k;
                    }
                }
            }
            if (best < 0) {
                break;
            }
            merged.add(inputs.get(best).get(at[best]++));
        }
        records = merged;
        next = 0;
    }

    /** RETURN: the next record into the record area; true at end (which ends the run unit without AT END). */
    public boolean returnRecord(boolean atEndPhrase) {
        if (next >= records.size()) {
            if (!atEndPhrase) {
                Bounds.fail("end of file (status = 10) for sort file " + name);
            }
            return true;
        }
        byte[] r = records.get(next++);
        System.arraycopy(r, 0, record.storage().bytes, record.offset(), r.length);
        return false;
    }

    /** GIVING: the remaining records into {@code file}, which is opened OUTPUT for this and closed after. */
    public void giving(CobolFile file) {
        file.open(CobolFile.OUTPUT);
        while (!returnRecord(true)) {
            file.writeRecord(record.get());
        }
        file.close();
    }

    private Comparable<?>[] keysOf(byte[] r) {
        Storage s = new Storage(Math.max(1, r.length));
        System.arraycopy(r, 0, s.bytes, 0, r.length);
        Comparable<?>[] k = new Comparable<?>[keys.size()];
        for (int i = 0; i < k.length; i++) {
            k[i] = keys.get(i).apply(s);
        }
        return k;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Comparable<?>[] a, Comparable<?>[] b) {
        for (int i = 0; i < a.length; i++) {
            int c = ((Comparable) a[i]).compareTo(b[i]);
            if (c != 0) {
                return ascending.get(i) ? c : -c;
            }
        }
        return 0;
    }
}
