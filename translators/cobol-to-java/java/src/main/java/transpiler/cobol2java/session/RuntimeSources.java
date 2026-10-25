package transpiler.cobol2java.session;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The runtime library sources bundled in the worker jar, re-packaged for a translation. */
public final class RuntimeSources {
    public static final String SOURCE_PACKAGE = "transpiler.cobol.runtime";

    /** Kept in sync with runtime/src/main/java by RuntimeSourcesTest. */
    public static final List<String> CLASSES = List.of(
            "Accept", "AlphanumericField", "Arithmetic", "Bounds", "Clock", "CobolProgram", "Compare", "Display", "EditedField", "Goback",
            "NumericField", "ProgramSupport", "StopRun", "Storage", "StringOps");

    private RuntimeSources() {}

    /** Class simple name to Java source, with the package declaration set to {@code targetPackage}. */
    public static Map<String, String> forPackage(String targetPackage) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String cls : CLASSES) {
            String resource = "/runtime-src/" + SOURCE_PACKAGE.replace('.', '/') + "/" + cls + ".java";
            try (InputStream in = RuntimeSources.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("runtime source missing from worker: " + resource);
                }
                CompilationUnit cu = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21))
                        .parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getResult().orElseThrow();
                cu.setPackageDeclaration(targetPackage);
                out.put(cls, cu.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }
}
