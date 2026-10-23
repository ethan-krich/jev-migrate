package transpiler.cobol2java.translate;

import java.util.ArrayList;
import java.util.List;
import transpiler.cobol2java.cobol.Picture;

/** A WORKING-STORAGE entry and the Java field that represents it (if any). */
public final class DataItem {
    public enum Kind { ALPHANUMERIC, NUMERIC, GROUP, UNSUPPORTED }

    private final String cobolName;
    private final Kind kind;
    private final Picture picture;
    private final String unsupportedReason;
    private final DataItem parent;
    private final List<DataItem> children = new ArrayList<>();
    private final int line;
    private String javaName;
    private String initialValue;

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

    /** "alphanumeric" / "numeric" / "group" - the category exposed to mapping conditions. */
    public String category() {
        return switch (kind) {
            case ALPHANUMERIC -> "alphanumeric";
            case NUMERIC -> "numeric";
            case GROUP -> "group";
            case UNSUPPORTED -> "unsupported";
        };
    }
}
