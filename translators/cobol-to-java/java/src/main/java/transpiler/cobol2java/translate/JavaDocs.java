package transpiler.cobol2java.translate;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import transpiler.cobol2java.decision.Candidate;
import transpiler.cobol2java.session.RuntimeSources;

/**
 * The public methods of a set of Java classes, read from their source with their Javadoc: the
 * documentation a candidate carries for the calls it makes, and the methods {@link MethodChoice}
 * can build candidates from. Advisory only: docs never affect what is generated or the fingerprint.
 */
public final class JavaDocs {
    private static JavaDocs runtime;

    /** Class simple name to its public methods, in source order. */
    private final Map<String, List<Declared>> classes;

    private record Declared(JavaMethod method, boolean isStatic) {}

    private JavaDocs(Map<String, List<Declared>> classes) {
        this.classes = classes;
    }

    /** The bundled runtime library (parsed once per worker). */
    public static synchronized JavaDocs runtime() {
        if (runtime == null) {
            runtime = of(RuntimeSources.parsed());
        }
        return runtime;
    }

    /** The public methods of each class's top-level type and its nested types ({@code Outer.Inner}). */
    public static JavaDocs of(Map<String, CompilationUnit> sources) {
        Map<String, List<Declared>> classes = new LinkedHashMap<>();
        for (CompilationUnit cu : sources.values()) {
            for (TypeDeclaration<?> t : cu.getTypes()) {
                collect(t.getNameAsString(), t, classes);
            }
        }
        return new JavaDocs(classes);
    }

    private static void collect(String name, TypeDeclaration<?> type, Map<String, List<Declared>> classes) {
        List<Declared> methods = classes.computeIfAbsent(name, k -> new ArrayList<>());
        for (MethodDeclaration m : type.getMethods()) {
            if (m.hasModifier(Modifier.Keyword.PUBLIC)) {
                methods.add(new Declared(JavaMethod.of(name, m), m.isStatic()));
            }
        }
        for (var member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested && nested.hasModifier(Modifier.Keyword.PUBLIC)) {
                collect(name + "." + nested.getNameAsString(), nested, classes);
            }
        }
    }

    /** The public static methods of a class (empty for a class not in the set). */
    public List<JavaMethod> staticMethods(String cls) {
        return classes.getOrDefault(cls, List.of()).stream().filter(Declared::isStatic).map(Declared::method).toList();
    }

    /** The overloads of {@code cls.name} with {@code arity} parameters (a varargs one takes any). */
    public List<JavaMethod> overloads(String cls, String name, int arity) {
        return classes.getOrDefault(cls, List.of()).stream().map(Declared::method)
                .filter(m -> m.name().equals(name) && (m.params().size() == arity
                        || !m.params().isEmpty() && m.params().getLast().type().endsWith("...") && arity >= m.params().size() - 1))
                .toList();
    }

    /**
     * The docs of the calls {@code statements} make on a class of this set ({@code Compare.x(...)}),
     * once each, in the order they first appear. Without type resolution an overloaded name
     * gives every overload of the call's arity; methods without Javadoc are left out.
     */
    public List<Candidate.Doc> calledBy(List<Statement> statements) {
        Set<Candidate.Doc> out = new LinkedHashSet<>();
        for (Statement s : statements) {
            for (MethodCallExpr call : s.findAll(MethodCallExpr.class, n -> true)) {
                String scope = call.getScope().map(Object::toString).orElse(null);
                if (scope == null || !classes.containsKey(scope)) {
                    continue;
                }
                for (JavaMethod m : overloads(scope, call.getNameAsString(), call.getArguments().size())) {
                    if (!m.doc().summary().isEmpty()) {
                        out.add(m.doc());
                    }
                }
            }
        }
        return List.copyOf(out);
    }
}
