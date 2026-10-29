package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** STRING, UNSTRING and INSPECT. */
public final class StringOps {
    private StringOps() {}

    /**
     * STRING ... INTO target [WITH POINTER p]: characters are written from the pointer on; the
     * rest of the target is left as it was. Overflow when the pointer starts outside the target
     * (nothing is written) or a sending item does not fit (the rest is dropped).
     */
    public static final class Concat {
        private final AlphanumericField target;
        private final NumericField pointer;
        private int position;
        private boolean overflow;

        public Concat(AlphanumericField target, NumericField pointer) {
            this.target = target;
            this.pointer = pointer;
            this.position = pointer == null ? 1 : pointer.intValue();
            this.overflow = position < 1 || position > target.length();
        }

        /** Appends {@code data} up to the first {@code delimiter} (all of it when null: DELIMITED BY SIZE). */
        public Concat add(String data, String delimiter) {
            if (overflow) {
                return this;
            }
            String part = data;
            if (delimiter != null) {
                int at = data.indexOf(delimiter);
                if (at >= 0) {
                    part = data.substring(0, at);
                }
            }
            for (int i = 0; i < part.length(); i++) {
                if (position > target.length()) {
                    overflow = true;
                    return this;
                }
                target.storage().bytes[target.offset() + position - 1] = (byte) part.charAt(i);
                position++;
            }
            return this;
        }

        /** Stores the pointer; returns whether ON OVERFLOW applies. */
        public boolean end() {
            if (pointer != null) {
                pointer.move(BigDecimal.valueOf(position));
            }
            return overflow;
        }
    }

    /**
     * UNSTRING source [DELIMITED BY [ALL] d [OR [ALL] d]...] INTO ... [WITH POINTER p]: each
     * {@link #next} takes the characters up to the earliest delimiter (delimiters tried in order
     * at each position; ALL treats consecutive occurrences as one), or the rest of the source.
     */
    public static final class Unstring {
        private final String source;
        private final NumericField pointer;
        private final String[] delimiters;
        private final boolean[] all;
        private int position;
        private final boolean badPointer;
        private int fields;
        private String data = "";
        private String delimiter = "";

        public Unstring(String source, NumericField pointer, String[] delimiters, boolean[] all) {
            this.source = source;
            this.pointer = pointer;
            this.delimiters = delimiters;
            this.all = all;
            this.position = pointer == null ? 1 : pointer.intValue();
            this.badPointer = position < 1 || position > source.length();
        }

        /** The next field for a receiver; false when the source is used up (the receiver is left alone). */
        public boolean next() {
            if (badPointer || position > source.length()) {
                return false;
            }
            int from = position - 1;
            for (int p = from; p < source.length(); p++) {
                for (int d = 0; d < delimiters.length; d++) {
                    if (source.startsWith(delimiters[d], p)) {
                        data = source.substring(from, p);
                        delimiter = delimiters[d];
                        int next = p + delimiters[d].length();
                        while (all[d] && source.startsWith(delimiters[d], next)) {
                            next += delimiters[d].length();
                        }
                        position = next + 1;
                        fields++;
                        return true;
                    }
                }
            }
            data = source.substring(from);
            delimiter = "";
            position = source.length() + 1;
            fields++;
            return true;
        }

        /** The next field without delimiters: as many characters as the receiver holds. */
        public boolean next(int length) {
            if (badPointer || position > source.length()) {
                return false;
            }
            int from = position - 1;
            int to = Math.min(source.length(), from + length);
            data = source.substring(from, to);
            delimiter = "";
            position = to + 1;
            fields++;
            return true;
        }

        public String data() {
            return data;
        }

        public String delimiter() {
            return delimiter;
        }

        public int count() {
            return data.length();
        }

        /** Receivers acted upon (for TALLYING IN). */
        public int fields() {
            return fields;
        }

        /** Stores the pointer; returns whether ON OVERFLOW applies (bad pointer, or source left over). */
        public boolean end() {
            if (pointer != null && !badPointer) {
                pointer.move(BigDecimal.valueOf(position));
            }
            return badPointer || position <= source.length();
        }
    }

    /**
     * INSPECT: comparands are tried in order at each position of the item, left to right; a
     * match is counted (or replaced) and skipped, otherwise the scan moves one character on.
     * Each comparand applies only within its BEFORE / AFTER INITIAL region.
     */
    public static final class Inspect {
        private enum Kind { CHARACTERS, ALL, LEADING, FIRST }

        private record Comparand(Kind kind, String pattern, String replacement, int counter, String before, String after) {}

        private final char[] value;
        private final List<Comparand> comparands = new ArrayList<>();

        public Inspect(String value) {
            this.value = value.toCharArray();
        }

        public Inspect tallyCharacters(int counter, String before, String after) {
            comparands.add(new Comparand(Kind.CHARACTERS, null, null, counter, before, after));
            return this;
        }

        public Inspect tallyAll(int counter, String pattern, String before, String after) {
            comparands.add(new Comparand(Kind.ALL, pattern, null, counter, before, after));
            return this;
        }

        public Inspect tallyLeading(int counter, String pattern, String before, String after) {
            comparands.add(new Comparand(Kind.LEADING, pattern, null, counter, before, after));
            return this;
        }

        /** Runs the TALLYING comparands added so far; returns the count for each counter index. */
        public int[] tally(int counters) {
            int[] counts = new int[counters];
            scan((c, at) -> counts[c.counter()]++);
            comparands.clear();
            return counts;
        }

        public Inspect replaceCharacters(String by, String before, String after) {
            comparands.add(new Comparand(Kind.CHARACTERS, null, by, -1, before, after));
            return this;
        }

        public Inspect replaceAll(String pattern, String by, String before, String after) {
            comparands.add(new Comparand(Kind.ALL, pattern, by, -1, before, after));
            return this;
        }

        public Inspect replaceLeading(String pattern, String by, String before, String after) {
            comparands.add(new Comparand(Kind.LEADING, pattern, by, -1, before, after));
            return this;
        }

        public Inspect replaceFirst(String pattern, String by, String before, String after) {
            comparands.add(new Comparand(Kind.FIRST, pattern, by, -1, before, after));
            return this;
        }

        /** Runs the REPLACING comparands; returns the new value. */
        public String replace() {
            scan((c, at) -> {
                for (int k = 0; k < c.replacement().length(); k++) {
                    value[at + k] = c.replacement().charAt(k);
                }
            });
            comparands.clear();
            return new String(value);
        }

        /** CONVERTING from TO to: each character of {@code from} (its first occurrence) becomes the one at the same place in {@code to}. */
        public String convert(String from, String to, String before, String after) {
            int[] region = region(new String(value), before, after);
            for (int i = region[0]; i < region[1]; i++) {
                int k = from.indexOf(value[i]);
                if (k >= 0) {
                    value[i] = to.charAt(k);
                }
            }
            return new String(value);
        }

        private interface Action {
            void apply(Comparand c, int at);
        }

        private void scan(Action action) {
            String original = new String(value);
            int n = comparands.size();
            int[][] regions = new int[n][];
            // LEADING: where the next contiguous occurrence must start (-1 once the run is broken).
            int[] expect = new int[n];
            boolean[] done = new boolean[n];
            for (int i = 0; i < n; i++) {
                regions[i] = region(original, comparands.get(i).before(), comparands.get(i).after());
                expect[i] = regions[i][0];
            }
            int at = 0;
            while (at < value.length) {
                int step = 1;
                for (int i = 0; i < n; i++) {
                    Comparand c = comparands.get(i);
                    int[] r = regions[i];
                    if (done[i] || at < r[0] || at >= r[1]) {
                        continue;
                    }
                    int length = c.kind() == Kind.CHARACTERS ? 1 : c.pattern().length();
                    boolean hit = at + length <= r[1] && (c.kind() == Kind.CHARACTERS || original.startsWith(c.pattern(), at));
                    if (c.kind() == Kind.LEADING && expect[i] != at) {
                        hit = false;
                    }
                    if (hit) {
                        action.apply(c, at);
                        if (c.kind() == Kind.FIRST) {
                            done[i] = true;
                        }
                        if (c.kind() == Kind.LEADING) {
                            expect[i] = at + length;
                        }
                        step = length;
                        break;
                    }
                }
                at += step;
            }
        }

        /** [start, end) of the region after AFTER INITIAL and before BEFORE INITIAL. */
        private static int[] region(String value, String before, String after) {
            int start = 0;
            if (after != null) {
                int k = value.indexOf(after);
                start = k < 0 ? value.length() : k + after.length();
            }
            int end = value.length();
            if (before != null) {
                int k = value.indexOf(before, start);
                if (k >= 0) {
                    end = k;
                }
            }
            return new int[] {start, end};
        }
    }
}
