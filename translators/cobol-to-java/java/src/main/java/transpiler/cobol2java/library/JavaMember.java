package transpiler.cobol2java.library;

import java.util.List;
import transpiler.cobol2java.decision.Candidate;

/**
 * A public constructor, static method or instance method of a library, read from its source.
 * Types are canonical: the {@link transpiler.cobol2java.translate.JavaTypes} names ({@code String},
 * {@code int}, {@code long}, {@code BigDecimal}, {@code boolean}, {@code void}) for those types,
 * the fully qualified name for a type declared in the library, and {@code ?} followed by the
 * source text for anything else (a JDK type outside the table, a generic or array type), which
 * no binding takes and no chain follows.
 *
 * @param owner the fully qualified name of the declaring type ({@code com.acme.dates.Dates})
 * @param ownerName how candidates name the declaring type: its simple name ({@code Dates},
 *     {@code Outer.Inner}), or its qualified name when two types of the library share it
 * @param importName what a generated file imports for the owner ({@code com.acme.dates.Dates};
 *     for a nested type its top-level type)
 * @param qualified whether {@link #id} names the parameters' library types by their qualified names:
 *     set by the index for members whose ids would otherwise collide ({@code convert(a.Record)} and
 *     {@code convert(b.Record)} would both be {@code Ops.convert(Record)}); hints use the same ids
 */
public record JavaMember(String owner, String ownerName, String importName, Kind kind, String name, List<Param> params,
        String returnType, Candidate.Doc doc, boolean qualified) {
    /** {@code CONVERT}: not a member of the library but a record of a claimed data copybook taken as an object of the class. */
    public enum Kind { CONSTRUCTOR, STATIC, INSTANCE, CONVERT }

    public record Param(String name, String type) {}

    public JavaMember {
        params = List.copyOf(params);
    }

    public JavaMember(String owner, String ownerName, String importName, Kind kind, String name, List<Param> params,
            String returnType, Candidate.Doc doc) {
        this(owner, ownerName, importName, kind, name, params, returnType, doc, false);
    }

    /** This member with its id naming library parameter types by their qualified names. */
    JavaMember withQualifiedTypes() {
        return new JavaMember(owner, ownerName, importName, kind, name, params, returnType, doc, true);
    }

    /** {@code Dates.format(String,int)}, {@code Formatter.new(String)}: unique within a library. */
    public String id() {
        if (kind == Kind.CONVERT) {
            return ownerName + ".of(" + name + ")";
        }
        return ownerName + "." + (kind == Kind.CONSTRUCTOR ? "new" : name) + "("
                + String.join(",", params.stream().map(p -> qualified && !p.type().startsWith("?") ? p.type() : display(p.type())).toList())
                + ")";
    }

    /** Whether {@code hint} (a method id, with the owner simple or fully qualified) names this member. */
    public boolean matches(String hint) {
        String compact = hint.replace(" ", "");
        String qualified = owner + id().substring(ownerName.length());
        return compact.equals(id()) || compact.equals(qualified);
    }

    /** Whether every parameter and the result have a type a binding or chain can use. */
    public boolean usable() {
        return params.stream().noneMatch(p -> p.type().startsWith("?")) && !returnType.startsWith("?");
    }

    /** A type as written in ids and docs: simple names for library types. */
    static String display(String type) {
        if (type.startsWith("?")) {
            return type.substring(1);
        }
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }
}
