package transpiler.cobol2java.library;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import transpiler.cobol2java.decision.Candidate;
import transpiler.cobol2java.translate.JavaMethod;

/**
 * The public API of a Java library, read from its source with JavaParser: every public
 * constructor, static method and instance method of its public types (records with their
 * canonical constructor and accessors), with parameter names, types resolved within the
 * library, and the first paragraph of their Javadoc.
 *
 * <p>Types are resolved by the index itself, following Java's scoping (nested types, single-type
 * imports, the type's package, on-demand imports, {@code java.lang}), against the types the
 * library declares plus {@code String} and {@code BigDecimal}. Inherited members are not listed:
 * a chain only calls methods a type declares.
 */
public final class JavaIndex {
    private static final Set<String> TABLE_PRIMITIVES = Set.of("int", "long", "boolean", "void");
    private static final Map<String, String> JDK = Map.of("java.lang.String", "String", "java.math.BigDecimal", "BigDecimal");

    private final List<JavaMember> members;
    private final Map<String, List<JavaMember>> byOwner;
    private final String hash;
    private final List<String> skipped;
    private final int classes;

    private JavaIndex(List<JavaMember> members, String hash, List<String> skipped, int classes) {
        this.members = disambiguated(members);
        this.byOwner = this.members.stream().collect(Collectors.groupingBy(JavaMember::owner, LinkedHashMap::new, Collectors.toList()));
        this.hash = hash;
        this.skipped = List.copyOf(skipped);
        this.classes = classes;
    }

    /** Members whose ids collide (same owner and name, parameter types equal but for their packages) get qualified ids. */
    private static List<JavaMember> disambiguated(List<JavaMember> all) {
        Map<String, Long> counts = all.stream().collect(Collectors.groupingBy(JavaMember::id, Collectors.counting()));
        return all.stream().map(m -> counts.get(m.id()) > 1 ? m.withQualifiedTypes() : m).toList();
    }

    /** Every member, in source order (files sorted by path). */
    public List<JavaMember> members() {
        return members;
    }

    /** The members a type declares (constructors, static and instance methods). */
    public List<JavaMember> of(String owner) {
        return byOwner.getOrDefault(owner, List.of());
    }

    /** SHA-256 over every source file read (path and content): changes with the library's version. */
    public String hash() {
        return hash;
    }

    /** Files that could not be parsed (left out of the index), with the parser's first problem. */
    public List<String> skipped() {
        return skipped;
    }

    /** The number of public types offered. */
    public int classes() {
        return classes;
    }

    /** The sources of a directory (every {@code .java} below it), a single file, or a sources jar. */
    public static Map<String, String> read(Path path) throws IOException {
        Map<String, String> sources = new TreeMap<>();
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path p : walk.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p)).sorted().toList()) {
                    sources.put(path.relativize(p).toString().replace('\\', '/'), Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        } else if (path.toString().endsWith(".java")) {
            sources.put(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
        } else {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                for (ZipEntry e : zip.stream().filter(e -> !e.isDirectory() && e.getName().endsWith(".java")).toList()) {
                    try (var in = zip.getInputStream(e)) {
                        sources.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return sources;
    }

    /**
     * Indexes {@code sources} (path to content). {@code expose} limits the types offered
     * ({@code a.b.C} with its nested types, or {@code a.b.*} for a package); empty offers all.
     */
    public static JavaIndex of(Map<String, String> sources, List<String> expose) {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
        List<String> skipped = new ArrayList<>();
        List<CompilationUnit> units = new ArrayList<>();
        for (var e : new TreeMap<>(sources).entrySet()) {
            String file = e.getKey().substring(e.getKey().lastIndexOf('/') + 1);
            if (file.equals("module-info.java") || file.equals("package-info.java")) {
                continue;
            }
            ParseResult<CompilationUnit> r = parser.parse(e.getValue());
            if (r.getResult().isEmpty() || !r.isSuccessful()) {
                skipped.add(e.getKey() + ": " + r.getProblems().stream().findFirst().map(p -> p.getMessage()).orElse("not parsed"));
                continue;
            }
            units.add(r.getResult().get());
        }
        // Every type the library declares, public or not (resolution needs them all).
        Map<String, TypeDeclaration<?>> declared = new LinkedHashMap<>();
        Map<TypeDeclaration<?>, CompilationUnit> unitOf = new java.util.IdentityHashMap<>();
        for (CompilationUnit cu : units) {
            String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            for (TypeDeclaration<?> t : cu.getTypes()) {
                collectTypes(pkg.isEmpty() ? t.getNameAsString() : pkg + "." + t.getNameAsString(), t, cu, declared, unitOf);
            }
        }
        // How candidates name a type: its name within its top-level type, unless another library type shares it.
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Long> counts = declared.keySet().stream().collect(Collectors.groupingBy(f -> local(f, declared, unitOf), Collectors.counting()));
        declared.keySet().forEach(f -> names.put(f, counts.get(local(f, declared, unitOf)) > 1 ? f : local(f, declared, unitOf)));

        List<JavaMember> members = new ArrayList<>();
        int offered = 0;
        for (var e : declared.entrySet()) {
            String fqcn = e.getKey();
            TypeDeclaration<?> type = e.getValue();
            if (!publicType(type) || !exposed(fqcn, expose)) {
                continue;
            }
            offered++;
            CompilationUnit cu = unitOf.get(type);
            Resolver resolver = new Resolver(cu, fqcn, declared.keySet());
            String importName = topLevel(fqcn, type);
            String ownerName = names.get(fqcn);
            String typeDoc = summary(type);
            boolean iface = type instanceof ClassOrInterfaceDeclaration c && c.isInterface();
            boolean concrete = !iface && !(type instanceof ClassOrInterfaceDeclaration c && c.isAbstract())
                    && (type instanceof ClassOrInterfaceDeclaration || type instanceof RecordDeclaration);
            if (concrete) {
                List<ConstructorDeclaration> ctors = type.getConstructors();
                boolean canonicalDeclared = false;
                for (ConstructorDeclaration c : ctors) {
                    if (c.hasModifier(Modifier.Keyword.PUBLIC)) {
                        members.add(member(fqcn, ownerName, importName, JavaMember.Kind.CONSTRUCTOR, c, fqcn, resolver, typeDoc));
                    }
                }
                if (type instanceof RecordDeclaration rec) {
                    List<String> componentTypes = rec.getParameters().stream().map(p -> p.getType().asString()).toList();
                    canonicalDeclared = ctors.stream().anyMatch(c -> c.getParameters().stream().map(p -> p.getType().asString()).toList()
                            .equals(componentTypes));
                    if (!canonicalDeclared) {
                        // The implicit canonical constructor (a compact one declares no parameters).
                        List<JavaMember.Param> params = rec.getParameters().stream()
                                .map(p -> new JavaMember.Param(p.getNameAsString(), resolver.resolve(p.getType()))).toList();
                        members.add(new JavaMember(fqcn, ownerName, importName, JavaMember.Kind.CONSTRUCTOR, "new", params, fqcn,
                                doc(ownerName, "new", params, fqcn, typeDoc)));
                    }
                    for (Parameter component : rec.getParameters()) {
                        String name = component.getNameAsString();
                        boolean explicit = rec.getMethodsBySignature(name).stream().anyMatch(m -> true);
                        if (!explicit) {
                            String t = resolver.resolve(component.getType());
                            members.add(new JavaMember(fqcn, ownerName, importName, JavaMember.Kind.INSTANCE, name, List.of(), t,
                                    doc(ownerName, name, List.of(), t, "The " + name + " component of " + ownerName + ".")));
                        }
                    }
                } else if (ctors.isEmpty() && type instanceof ClassOrInterfaceDeclaration) {
                    // The implicit default constructor of a public class.
                    members.add(new JavaMember(fqcn, ownerName, importName, JavaMember.Kind.CONSTRUCTOR, "new", List.of(), fqcn,
                            doc(ownerName, "new", List.of(), fqcn, typeDoc)));
                }
            }
            for (MethodDeclaration m : type.getMethods()) {
                boolean visible = m.hasModifier(Modifier.Keyword.PUBLIC) || iface && !m.hasModifier(Modifier.Keyword.PRIVATE);
                if (!visible || !m.getTypeParameters().isEmpty()) {
                    continue;
                }
                JavaMember.Kind kind = m.isStatic() ? JavaMember.Kind.STATIC : JavaMember.Kind.INSTANCE;
                members.add(member(fqcn, ownerName, importName, kind, m, resolver.resolve(m.getType()), resolver, ""));
            }
        }
        return new JavaIndex(members, hash(sources), skipped, offered);
    }

    private static void collectTypes(String fqcn, TypeDeclaration<?> type, CompilationUnit cu, Map<String, TypeDeclaration<?>> out,
            Map<TypeDeclaration<?>, CompilationUnit> unitOf) {
        out.put(fqcn, type);
        unitOf.put(type, cu);
        for (var member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                collectTypes(fqcn + "." + nested.getNameAsString(), nested, cu, out, unitOf);
            }
        }
    }

    /** A type's name within its top-level type ({@code Outer.Inner}). */
    private static String local(String fqcn, Map<String, TypeDeclaration<?>> declared, Map<TypeDeclaration<?>, CompilationUnit> unitOf) {
        String pkg = unitOf.get(declared.get(fqcn)).getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        return pkg.isEmpty() ? fqcn : fqcn.substring(pkg.length() + 1);
    }

    private static String topLevel(String fqcn, TypeDeclaration<?> type) {
        String top = fqcn;
        var at = type;
        while (at.getParentNode().orElse(null) instanceof TypeDeclaration<?> parent) {
            top = top.substring(0, top.lastIndexOf('.'));
            at = parent;
        }
        return top;
    }

    /** Public itself and in every enclosing type (an interface's members are public). */
    private static boolean publicType(TypeDeclaration<?> type) {
        var at = type;
        while (true) {
            var parent = at.getParentNode().orElse(null);
            boolean inInterface = parent instanceof ClassOrInterfaceDeclaration c && c.isInterface();
            if (!at.hasModifier(Modifier.Keyword.PUBLIC) && !inInterface) {
                return false;
            }
            if (!(parent instanceof TypeDeclaration<?> p)) {
                return true;
            }
            at = p;
        }
    }

    static boolean exposed(String fqcn, List<String> expose) {
        if (expose.isEmpty()) {
            return true;
        }
        for (String pattern : expose) {
            if (pattern.endsWith(".*")) {
                String pkg = pattern.substring(0, pattern.length() - 2);
                // The package's types and their nested types (a nested type's prefix is still a type of the package).
                if (fqcn.startsWith(pkg + ".") && Character.isUpperCase(fqcn.charAt(pkg.length() + 1))) {
                    return true;
                }
            } else if (fqcn.equals(pattern) || fqcn.startsWith(pattern + ".")) {
                return true;
            }
        }
        return false;
    }

    private static JavaMember member(String fqcn, String ownerName, String importName, JavaMember.Kind kind, CallableDeclaration<?> c,
            String returnType, Resolver resolver, String fallbackDoc) {
        List<JavaMember.Param> params = new ArrayList<>();
        for (Parameter p : c.getParameters()) {
            String t = p.isVarArgs() ? "?" + p.getType().asString() + "..." : resolver.resolve(p.getType());
            params.add(new JavaMember.Param(p.getNameAsString(), t));
        }
        String name = kind == JavaMember.Kind.CONSTRUCTOR ? "new" : c.getNameAsString();
        String own = summary(c);
        return new JavaMember(fqcn, ownerName, importName, kind, name, params, returnType,
                doc(ownerName, name, params, returnType, own.isEmpty() ? fallbackDoc : own));
    }

    private static Candidate.Doc doc(String ownerName, String name, List<JavaMember.Param> params, String returnType, String summary) {
        String args = String.join(", ", params.stream().map(p -> JavaMember.display(p.type()) + " " + p.name()).toList());
        String signature = name.equals("new") ? "new " + ownerName + "(" + args + ")"
                : JavaMember.display(returnType) + " " + ownerName + "." + name + "(" + args + ")";
        return new Candidate.Doc(signature, summary);
    }

    private static String summary(NodeWithJavadoc<?> node) {
        return node.getJavadoc().map(j -> JavaMethod.summary(j.getDescription().toText())).orElse("");
    }

    private static String hash(Map<String, String> sources) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (var e : new TreeMap<>(sources).entrySet()) {
                sha.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                sha.update(e.getValue().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return "sha256:" + HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Resolves type names as written in one type of one compilation unit. */
    private static final class Resolver {
        private final String pkg;
        private final String fqcn;
        private final Set<String> declared;
        private final Map<String, String> singles = new LinkedHashMap<>();
        private final List<String> onDemand = new ArrayList<>();

        Resolver(CompilationUnit cu, String fqcn, Set<String> declared) {
            this.pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            this.fqcn = fqcn;
            this.declared = declared;
            for (ImportDeclaration i : cu.getImports()) {
                if (i.isStatic()) {
                    continue;
                }
                String name = i.getNameAsString();
                if (i.isAsterisk()) {
                    onDemand.add(name);
                } else {
                    singles.put(name.substring(name.lastIndexOf('.') + 1), name);
                }
            }
        }

        String resolve(Type type) {
            String text = type.asString();
            if (type.isPrimitiveType() || type.isVoidType()) {
                return TABLE_PRIMITIVES.contains(text) ? text : "?" + text;
            }
            if (!type.isClassOrInterfaceType() || type.asClassOrInterfaceType().getTypeArguments().isPresent()
                    || text.contains("<")) {
                return "?" + text;
            }
            String name = text;
            String first = name.split("\\.", 2)[0];
            String rest = name.contains(".") ? name.substring(first.length()) : "";
            if (!first.isEmpty() && Character.isLowerCase(first.charAt(0))) {
                return known(name);
            }
            // A nested type of this type or of an enclosing one, or the type itself.
            for (String scope = fqcn; !scope.isEmpty(); scope = scope.contains(".") ? scope.substring(0, scope.lastIndexOf('.')) : "") {
                if (declared.contains(scope + "." + name)) {
                    return scope + "." + name;
                }
                if (scope.endsWith("." + first) && declared.contains(scope)) {
                    String r = scope + rest;
                    if (declared.contains(r)) {
                        return r;
                    }
                }
            }
            if (singles.containsKey(first)) {
                return known(singles.get(first) + rest);
            }
            String samePackage = pkg.isEmpty() ? name : pkg + "." + name;
            if (declared.contains(samePackage)) {
                return samePackage;
            }
            for (String p : onDemand) {
                String r = known(p + "." + name);
                if (!r.startsWith("?")) {
                    return r;
                }
            }
            return known("java.lang." + name);
        }

        private String known(String qualified) {
            if (declared.contains(qualified)) {
                return qualified;
            }
            return JDK.getOrDefault(qualified, "?" + qualified);
        }
    }
}
