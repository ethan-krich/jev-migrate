package transpiler.cobol2java.translate;

import java.util.ArrayList;
import java.util.List;
import transpiler.cobol2java.cobol.Picture;

/** A WORKING-STORAGE entry and the Java field that represents it (if any). */
public final class DataItem {
    /** CONDITION is a level-88 condition-name on its (elementary) parent; CONSTANT a level-78 name. */
    public enum Kind { ALPHANUMERIC, NUMERIC, EDITED, GROUP, CONDITION, CONSTANT, UNSUPPORTED }

    /** How a numeric item is stored: zoned, packed, big-endian binary, native binary, or an index data item. */
    public enum Usage { DISPLAY, PACKED, BINARY, NATIVE, INDEX }

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
    private Usage usage = Usage.DISPLAY;
    private boolean blankWhenZero;
    private boolean initialIsNumber;
    private boolean linkage;
    private boolean fileRecord;
    private int occurs;
    private int occursMin;
    private DataItem dependingOn;
    private List<IndexName> indexes = List.of();
    private List<Key> keys = List.of();
    /** For a level-66 RENAMES item: the record whose bytes it renames. */
    private DataItem renamedRecord;
    /** For an EXTERNAL record: the name its storage is shared under in the run unit. */
    private String externalName;
    /** A LOCAL-STORAGE record: initialised at each invocation. */
    private boolean local;
    /** Declared GLOBAL (a record of this program that its nested programs share). */
    private boolean global;
    /** For a GLOBAL record of a containing program: that program's class, and how many levels out it is. */
    private String globalOwner;
    private int globalDepth;

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

    public boolean isEdited() {
        return kind == Kind.EDITED;
    }

    public Picture.Edited edited() {
        return (Picture.Edited) picture;
    }

    /** Whether BLANK WHEN ZERO applies (only to numeric-edited items). */
    public boolean blankWhenZero() {
        return blankWhenZero;
    }

    void blankWhenZero(boolean b) {
        this.blankWhenZero = b;
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
        return isNumeric() && numeric().scale() <= 0;
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
        if (renamedRecord != null) {
            return renamedRecord;
        }
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

    public Usage usage() {
        return usage;
    }

    void usage(Usage usage) {
        this.usage = usage;
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
        if (renamedRecord != null) {
            // A RENAMES item is another view of bytes that are initialised through their own items.
            return true;
        }
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

    /** Whether a reference to this item names all of it (it is not an element of a table). */
    public boolean subscriptsOrNone() {
        return dimensions().isEmpty();
    }

    /** Whether this item is in the LINKAGE SECTION (its storage is the caller's). */
    public boolean linkage() {
        return record().linkage;
    }

    /** Whether this item is in a FILE SECTION record (its bytes are the file's record area, not initialised). */
    public boolean fileRecord() {
        return record().fileRecord;
    }

    /** Makes this a level-66 RENAMES item: the bytes at {@code offset} ({@code size} long) of {@code record}. */
    void renames(DataItem record, int offset, int size) {
        this.renamedRecord = record;
        layout(offset, size);
    }

    void markGlobal() {
        this.global = true;
    }

    /** Whether this record (or the record of this item) is declared GLOBAL in this program. */
    public boolean global() {
        return record().global;
    }

    void importGlobal(String owner, int depth) {
        this.globalOwner = owner;
        this.globalDepth = depth;
    }

    /** The class of the containing program owning this GLOBAL item, or null for this program's own items. */
    public String globalOwner() {
        return record().globalOwner;
    }

    /** 0 for this program's own items, else how many containers out the GLOBAL item is declared. */
    public int globalDepth() {
        return record().globalDepth;
    }

    void markLocal() {
        this.local = true;
    }

    /** Whether this item is in the LOCAL-STORAGE SECTION. */
    public boolean local() {
        return record().local;
    }

    void external(String name) {
        this.externalName = name;
    }

    /** The run-unit name of this item's EXTERNAL storage (its record's), or null when it is not EXTERNAL. */
    public String externalName() {
        DataItem r = record();
        while (r.externalName == null && r.redefines != null && r.parent == null) {
            r = r.redefines;
        }
        return r.externalName;
    }

    /** Whether this is a level-66 RENAMES item (a group renaming has no subordinate items of its own). */
    public boolean isRenames() {
        return renamedRecord != null;
    }

    void markFileRecord() {
        this.fileRecord = true;
    }

    void markLinkage() {
        this.linkage = true;
    }

    /** Whether references go through a generated accessor method (tables, variable-length groups, LINKAGE items). */
    public boolean hasAccessor() {
        return !dimensions().isEmpty() || (isGroup() && variableTable() != null) || linkage();
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
        } else if (item.kind == Kind.ALPHANUMERIC || item.kind == Kind.NUMERIC || item.kind == Kind.EDITED || item.kind == Kind.UNSUPPORTED) {
            out.add(item);
        }
    }

    /**
     * Items whose bytes differ between GnuCOBOL on ASCII / little-endian hardware and an EBCDIC
     * mainframe: zoned items with an embedded sign, and native-order binary (COMP-5, INDEX).
     */
    public boolean encodingSensitive() {
        return isNumeric() && ((usage == Usage.DISPLAY && numeric().signed() && !signSeparate)
                || usage == Usage.NATIVE || usage == Usage.INDEX);
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

    /** For an edited item: whether {@link #initialValue()} is a numeric literal (edited) rather than stored as is. */
    public boolean initialIsNumber() {
        return initialIsNumber;
    }

    void initialNumber(String value) {
        this.initialValue = value;
        this.initialIsNumber = true;
    }

    /** "alphanumeric" / "numeric" / "group" / "condition" - the category exposed to mapping conditions. */
    public String category() {
        return switch (kind) {
            case ALPHANUMERIC -> "alphanumeric";
            case NUMERIC -> "numeric";
            case EDITED -> edited().numeric() ? "numeric_edited" : "alphanumeric_edited";
            case GROUP -> "group";
            case CONDITION -> "condition";
            case CONSTANT -> "constant";
            case UNSUPPORTED -> "unsupported";
        };
    }
}
