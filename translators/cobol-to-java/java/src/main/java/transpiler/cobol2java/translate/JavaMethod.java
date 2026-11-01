package transpiler.cobol2java.translate;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.util.List;
import transpiler.cobol2java.decision.Candidate;

/**
 * A static Java method a choice can call, read from its declaration: what {@link JavaTypes}
 * binds operands to and {@link MethodChoice} builds candidates from. Types are the declared
 * simple names ({@code String}, {@code int}, {@code BigDecimal}); nothing is resolved.
 *
 * @param scope the class the call is made on ({@code StringOps})
 */
public record JavaMethod(String scope, String name, List<Param> params, String returnType, Candidate.Doc doc) {
    public record Param(String name, String type) {}

    public JavaMethod {
        params = List.copyOf(params);
    }

    public static JavaMethod of(String scope, MethodDeclaration m) {
        List<Param> params = m.getParameters().stream().map(JavaMethod::param).toList();
        String returnType = simple(m.getType().asString());
        String signature = returnType + " " + scope + "." + m.getNameAsString() + "("
                + String.join(", ", params.stream().map(p -> p.type() + " " + p.name()).toList()) + ")";
        String summary = m.getJavadoc().map(j -> summary(j.getDescription().toText())).orElse("");
        return new JavaMethod(scope, m.getNameAsString(), params, returnType, new Candidate.Doc(signature, summary));
    }

    /** {@code Scope.name(Type, Type)}: unique among a class's overloads. */
    public String id() {
        return scope + "." + name + "(" + String.join(",", params.stream().map(Param::type).toList()) + ")";
    }

    /**
     * The first paragraph of a Javadoc description as plain text: up to the first blank line or
     * {@code <p>}, with inline tags reduced to their text and HTML removed.
     */
    public static String summary(String description) {
        String first = description.strip().split("\\n\\s*\\n|<p>|<P>", 2)[0];
        String text = first.replaceAll("\\{@(?:code|literal)\\s+([^}]*)}", "$1")
                .replaceAll("\\{@link(?:plain)?\\s+(?:[^}\\s]+\\s+)?([^}]*)}", "$1")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        return text.replaceAll("\\s+", " ").strip();
    }

    private static Param param(Parameter p) {
        return new Param(p.getNameAsString(), simple(p.getType().asString()) + (p.isVarArgs() ? "..." : ""));
    }

    private static String simple(String type) {
        return type.replaceAll("\\b[a-z][\\w]*\\.", "");
    }
}
