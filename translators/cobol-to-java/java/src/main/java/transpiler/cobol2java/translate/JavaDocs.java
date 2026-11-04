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
    private static JavaDocs jdk;

    /**
     * The JDK classes whose docs a {@code jdk} candidate (a JDK twin of a builtin handler) carries,
     * as paths in the JDK's {@code lib/src.zip}; a class key may take several sources (StringBuilder
     * declares most of its methods in its package-private superclass).
     */
    private static final Map<String, List<String>> JDK_CLASSES = jdkClasses();

    private static Map<String, List<String>> jdkClasses() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Math", List.of("java/lang/Math.java"));
        m.put("Double", List.of("java/lang/Double.java"));
        m.put("Character", List.of("java/lang/Character.java"));
        m.put("String", List.of("java/lang/String.java"));
        m.put("CharSequence", List.of("java/lang/CharSequence.java"));
        m.put("StringBuilder", List.of("java/lang/StringBuilder.java", "java/lang/AbstractStringBuilder.java"));
        m.put("BigDecimal", List.of("java/math/BigDecimal.java"));
        m.put("PrintStream", List.of("java/io/PrintStream.java"));
        m.put("Pattern", List.of("java/util/regex/Pattern.java"));
        m.put("Matcher", List.of("java/util/regex/Matcher.java"));
        m.put("IntStream", List.of("java/util/stream/IntStream.java"));
        m.put("DoubleStream", List.of("java/util/stream/DoubleStream.java"));
        return java.util.Collections.unmodifiableMap(m);
    }

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

    /**
     * The JDK classes JDK twins call, read with their Javadoc from the running JDK's
     * {@code lib/src.zip} (or the file {@code TRANSPILER_JDK_SRC} names); empty when the JDK was
     * installed without its sources (docs are advisory: candidates are the same without them).
     * Parsed once per worker.
     */
    public static synchronized JavaDocs jdk() {
        if (jdk == null) {
            jdk = of(jdkSources(jdkSourceZip()));
        }
        return jdk;
    }

    static java.nio.file.Path jdkSourceZip() {
        String override = System.getenv("TRANSPILER_JDK_SRC");
        return override != null && !override.isEmpty() ? java.nio.file.Path.of(override)
                : java.nio.file.Path.of(System.getProperty("java.home"), "lib", "src.zip");
    }

    /** The {@link #JDK_CLASSES} sources in {@code zip}, keyed by class (none when it is missing or unreadable). */
    static Map<String, CompilationUnit> jdkSources(java.nio.file.Path zip) {
        Map<String, CompilationUnit> out = new LinkedHashMap<>();
        if (!java.nio.file.Files.isRegularFile(zip)) {
            return out;
        }
        var parser = new com.github.javaparser.JavaParser(new com.github.javaparser.ParserConfiguration()
                .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_21));
        try (var file = new java.util.zip.ZipFile(zip.toFile())) {
            for (var e : JDK_CLASSES.entrySet()) {
                for (String path : e.getValue()) {
                    // JDK 9+ zips put each module first (java.base/java/lang/Math.java); JDK 8 ones do not.
                    var entry = file.getEntry("java.base/" + path);
                    if (entry == null) {
                        entry = file.getEntry(path);
                    }
                    if (entry == null) {
                        continue;
                    }
                    try (var in = file.getInputStream(entry)) {
                        var result = parser.parse(in, java.nio.charset.StandardCharsets.UTF_8);
                        result.getResult().ifPresent(cu -> {
                            // Registered under the class key: AbstractStringBuilder's methods are StringBuilder's.
                            cu.getTypes().forEach(t -> t.setName(e.getKey()));
                            out.put(e.getKey() + "#" + path, cu);
                        });
                    }
                }
            }
        } catch (java.io.IOException e) {
            return new LinkedHashMap<>();
        }
        return out;
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
        boolean isInterface = type instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration c && c.isInterface();
        for (MethodDeclaration m : type.getMethods()) {
            if (m.hasModifier(Modifier.Keyword.PUBLIC) || isInterface && !m.hasModifier(Modifier.Keyword.PRIVATE)) {
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

    /**
     * Like {@link #calledBy}, for JDK code: a call on a class of this set ({@code Math.sqrt(x)}),
     * on {@code System.out} / {@code System.err} (PrintStream), or on a value whose type is not
     * resolved ({@code s.compareTo(t)}), which gives the same-named methods of every class in the
     * set with Javadoc, once each.
     */
    public List<Candidate.Doc> calledByAnyScope(List<Statement> statements) {
        Set<Candidate.Doc> out = new LinkedHashSet<>();
        for (Statement s : statements) {
            for (MethodCallExpr call : s.findAll(MethodCallExpr.class, n -> true)) {
                String scope = call.getScope().map(Object::toString).orElse(null);
                List<String> classNames;
                if (scope != null && classes.containsKey(scope)) {
                    classNames = List.of(scope);
                } else if ("System.out".equals(scope) || "System.err".equals(scope)) {
                    classNames = List.of("PrintStream");
                } else {
                    classNames = List.copyOf(classes.keySet());
                }
                for (String cls : classNames) {
                    for (JavaMethod m : overloads(cls, call.getNameAsString(), call.getArguments().size())) {
                        if (!m.doc().summary().isEmpty()) {
                            out.add(m.doc());
                        }
                    }
                }
            }
        }
        return List.copyOf(out);
    }
}
