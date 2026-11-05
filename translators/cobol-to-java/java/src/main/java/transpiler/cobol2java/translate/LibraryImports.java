package transpiler.cobol2java.translate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Imports the library types a generated file uses. Library calls are generated with fully
 * qualified names; when the file is written, each top-level library type whose simple name is
 * free in the file is imported once and its uses shortened. A name that clashes (another library
 * type, a runtime class, a {@code java.lang} or {@code java.math} type the code names, or a class
 * of the file) stays fully qualified and is not imported. Nothing is renamed, and the result
 * depends only on the file and the types, so it is deterministic.
 */
public final class LibraryImports {
    /** Simple names generated code may use without an import: java.lang types it names, besides the imports. */
    private static final Set<String> TAKEN = Set.of("String", "Object", "Math", "System", "Integer", "Long", "Boolean",
            "Override", "Exception", "RuntimeException", "Character", "StringBuilder");

    private LibraryImports() {}

    /**
     * Shortens and imports {@code types} (the {@code importName} of each library member used, a
     * top-level type) in {@code cu}, and adds {@code note} to the Javadoc of its top-level class.
     */
    public static void apply(CompilationUnit cu, List<String> types, String note) {
        Set<String> taken = new HashSet<>(TAKEN);
        cu.getImports().forEach(i -> taken.add(i.getName().getIdentifier()));
        cu.findAll(TypeDeclaration.class).forEach(t -> taken.add(((TypeDeclaration<?>) t).getNameAsString()));
        Map<String, Integer> simpleCount = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(new java.util.TreeSet<>(types));
        sorted.forEach(t -> simpleCount.merge(simple(t), 1, Integer::sum));
        for (String type : sorted) {
            String simple = simple(type);
            if (taken.contains(simple) || simpleCount.get(simple) > 1) {
                continue;
            }
            taken.add(simple);
            cu.addImport(type);
            shorten(cu, type, simple);
        }
        if (note != null && !cu.getTypes().isEmpty()) {
            TypeDeclaration<?> top = cu.getType(0);
            String doc = top.getJavadoc().map(j -> j.toText().strip()).orElse("");
            top.setJavadocComment((doc.isEmpty() ? "" : doc + "\n\n<p>") + note);
        }
    }

    private static void shorten(CompilationUnit cu, String type, String simple) {
        List<Node> replace = new ArrayList<>();
        // Static call scopes: com.acme.dates.Dates.addDays(..) has the scope com.acme.dates.Dates (a FieldAccessExpr).
        for (FieldAccessExpr f : cu.findAll(FieldAccessExpr.class)) {
            if (f.toString().equals(type) && !(f.getParentNode().orElse(null) instanceof FieldAccessExpr p && p.getScope() == f
                    && p.toString().equals(type))) {
                replace.add(f);
            }
        }
        for (Node n : replace) {
            n.replace(new NameExpr(simple));
        }
        List<ClassOrInterfaceType> types = new ArrayList<>();
        for (ClassOrInterfaceType t : cu.findAll(ClassOrInterfaceType.class)) {
            String name = t.getNameWithScope();
            if ((name.equals(type) || name.startsWith(type + ".")) && !(t.getParentNode().orElse(null) instanceof ClassOrInterfaceType)) {
                types.add(t);
            }
        }
        for (ClassOrInterfaceType t : types) {
            String rest = t.getNameWithScope().substring(type.length());
            t.replace(StaticJavaParser.parseClassOrInterfaceType(simple + rest));
        }
    }

    private static String simple(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }
}
