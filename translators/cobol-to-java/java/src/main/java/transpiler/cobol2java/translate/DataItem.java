package transpiler.cobol2java.translate;

import java.util.ArrayList;
import java.util.List;
import transpiler.cobol2java.cobol.Picture;

/** A WORKING-STORAGE entry and the Java field that represents it (if any). */
public final class DataItem {
    /** CONDITION is a level-88 condition-name on its (elementary) parent; CONSTANT a level-78 name. */
    public enum Kind { ALPHANUMERIC, NUMERIC, GROUP, CONDITION, CONSTANT, UNSUPPORTED }

    /** An INDEXED BY name of a table: an occurrence number held in a Java {@code int}. */
    public record IndexName(String cobolName, String javaName, DataItem table) {}

    /** An ASCENDING / DESCENDING KEY of a table. */
    public record Key(DataItem item, boolean ascending) {}

    /** One VALUE of a condition-name: a single value, or {@code from THRU thru}. */
    public record ConditionValue(Operand from, Operand thru) {}

    private final String cobolName;
    private final Kind kind;
    private final Picture picture;
    private final String unsupportedReason;
    private final DataItem parent;
    private final List<DataItem> children = new ArrayList<>();
    private final int line;
    private String javaName;
    private String initialValue;
    private List<ConditionValue> conditionValues = List.of();
    private Operand falseValue;
    private int offset;
    private int size;
    private boolean signLeading;
    private boolean signSeparate;
    private boolean justified;
    private DataItem redefines;
    private Operand constant;
    private int occurs;
    private int occursMin;
    private DataItem dependingOn;
    private List<IndexName> indexes = List.of();
    private List<Key> keys = List.of();

    DataItem(String cobolName, Kind kind, Picture picture, String unsupportedReason, DataItem parent, int line) {
        this.cobolName = cobolName;
        this.kind = kind;
        this.picture = picture;
        this.unsupportedReason = unsupportedReason;
        this.parent = parent;
        this.line = line;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    public String cobolName() {
        return cobolName;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isAlphanumeric() {
        return kind == Kind.ALPHANUMERIC;
    }

    public boolean isNumeric() {
        return kind == Kind.NUMERIC;
    }

    public boolean isCondition() {
        return kind == Kind.CONDITION;
    }

    public boolean isGroup() {
        return kind == Kind.GROUP;
    }

    public boolean isFiller() {
        return cobolName.equalsIgnoreCase("FILLER");
    }

    /** The values of a condition-name, in source order. */
    public List<ConditionValue> conditionValues() {
        return conditionValues;
    }

    /** The {@code WHEN SET TO FALSE} value of a condition-name, or null. */
    public Operand falseValue() {
        return falseValue;
    }

    void condition(List<ConditionValue> values, Operand whenFalse) {
        this.conditionValues = List.copyOf(values);
        this.falseValue = whenFalse;
    }

    public Picture.Numeric numeric() {
        return (Picture.Numeric) picture;
    }

    public Picture.Alphanumeric alphanumeric() {
        return (Picture.Alphanumeric) picture;
    }

    public boolean isInteger() {
        return isNumeric() && numeric().scale() == 0;
    }

    public String unsupportedReason() {
        return unsupportedReason;
    }

    public DataItem parent() {
        return parent;
    }

    public List<DataItem> children() {
        return children;
    }

    public int line() {
        return line;
    }

    /** The level-01 / level-77 item whose storage this item is part of. */
    public DataItem record() {
        DataItem r = this;
        while (r.parent != null) {
            r = r.parent;
        }
        return r;
    }

    /** Byte offset within the record. */
    public int offset() {
        return offset;
    }

    /** Size in bytes (0 for condition-names and unsupported items). */
    public int size() {
        return size;
    }

    void layout(int offset, int size) {
        this.offset = offset;
        this.size = size;
    }

    public boolean signLeading() {
        return signLeading;
    }

    public boolean signSeparate() {
        return signSeparate;
    }

    void sign(boolean leading, boolean separate) {
        this.signLeading = leading;
        this.signSeparate = separate;
    }

    public boolean justified() {
        return justified;
    }

    void justified(boolean justified) {
        this.justified = justified;
    }

    /** The item this one REDEFINES, or null. */
    public DataItem redefines() {
        return redefines;
    }

    void redefines(DataItem target) {
        this.redefines = target;
    }

    /** Whether this item or an ancestor has REDEFINES (such items are not initialised). */
    public boolean inRedefines() {
        for (DataItem i = this; i != null; i = i.parent) {
            if (i.redefines != null) {
                return true;
            }
        }
        return false;
    }

    /** OCCURS maximum, or 0 for an item that is not a table. */
    public int occurs() {
        return occurs;
    }

    /** OCCURS minimum ({@code OCCURS min TO max}); equals {@link #occurs()} without DEPENDING ON. */
    public int occursMin() {
        return occursMin;
    }

    public DataItem dependingOn() {
        return dependingOn;
    }

    public List<IndexName> indexes() {
        return indexes;
    }

    public List<Key> keys() {
        return keys;
    }

    void occurs(int min, int max, DataItem depending) {
        this.occursMin = min;
        this.occurs = max;
        this.dependingOn = depending;
    }

    void tableNames(List<IndexName> indexes, List<Key> keys) {
        this.indexes = List.copyOf(indexes);
        this.keys = List.copyOf(keys);
    }

    /** The tables this item is in (itself included), outermost first: one subscript each. */
    public List<DataItem> dimensions() {
        List<DataItem> out = new ArrayList<>();
        for (DataItem i = this; i != null; i = i.parent) {
            if (i.occurs > 0) {
                out.addFirst(i);
            }
        }
        return out;
    }

    /** The OCCURS DEPENDING ON table inside this group (itself included), or null. */
    public DataItem variableTable() {
        if (dependingOn != null) {
            return this;
        }
        for (DataItem c : children) {
            DataItem t = c.variableTable();
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** Size in bytes of all occurrences (the item itself when it is not a table). */
    public int totalSize() {
        return occurs > 0 ? size * occurs : size;
    }

    /** Whether references go through a generated accessor method (tables, variable-length groups). */
    public boolean hasAccessor() {
        return !dimensions().isEmpty() || (isGroup() && variableTable() != null);
    }

    /** The VALUE of a level-78 constant. */
    public Operand constant() {
        return constant;
    }

    void constant(Operand value) {
        this.constant = value;
    }

    /** Elementary items (not condition-names) in this item, itself included when elementary, in order. */
    public List<DataItem> elementaryItems() {
        List<DataItem> out = new ArrayList<>();
        collectElementary(this, out);
        return out;
    }

    private static void collectElementary(DataItem item, List<DataItem> out) {
        if (item.isGroup()) {
            item.children.forEach(c -> collectElementary(c, out));
        } else if (item.kind == Kind.ALPHANUMERIC || item.kind == Kind.NUMERIC || item.kind == Kind.UNSUPPORTED) {
            out.add(item);
        }
    }

    /** A signed zoned item with an embedded sign: its bytes differ between ASCII and EBCDIC sign conventions. */
    public boolean encodingSensitive() {
        return isNumeric() && numeric().signed() && !signSeparate;
    }

    public String javaName() {
        return javaName;
    }

    void javaName(String name) {
        this.javaName = name;
    }

    /** The VALUE clause text in a form accepted by the runtime constructor, or null for the default. */
    public String initialValue() {
        return initialValue;
    }

    void initialValue(String value) {
        this.initialValue = value;
    }

    /** "alphanumeric" / "numeric" / "group" / "condition" - the category exposed to mapping conditions. */
    public String category() {
        return switch (kind) {
            case ALPHANUMERIC -> "alphanumeric";
            case NUMERIC -> "numeric";
            case GROUP -> "group";
            case CONDITION -> "condition";
            case CONSTANT -> "constant";
            case UNSUPPORTED -> "unsupported";
        };
    }
}
