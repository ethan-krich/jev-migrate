package transpiler.cobol2java.translate;

import java.util.ArrayList;
import java.util.List;
import transpiler.cobol2java.cobol.Picture;

/** A WORKING-STORAGE entry and the Java field that represents it (if any). */
public final class DataItem {
    /** CONDITION is a level-88 condition-name on its (elementary) parent. */
    public enum Kind { ALPHANUMERIC, NUMERIC, GROUP, CONDITION, UNSUPPORTED }

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
            case UNSUPPORTED -> "unsupported";
        };
    }
}
