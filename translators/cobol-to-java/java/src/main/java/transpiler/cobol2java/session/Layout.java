package transpiler.cobol2java.session;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.ProtocolException;
import transpiler.cobol2java.translate.Names;

/**
 * The folder convention declared by the Python {@code cobol_to_java.Layout}: each directory under
 * the source root becomes a package segment under {@code base_package}; the class comes from
 * PROGRAM-ID. Output goes to {@code <output>/<java_source_dir>/<package path>/<Class>.java}.
 */
public record Layout(
        String basePackage,
        String javaSourceDir,
        List<String> copybookDirs,
        List<String> copybookExtensions,
        String runtimePackage,
        Map<String, String> packageOverrides) {

    public static Layout parse(JsonNode n) throws ProtocolException {
        if (n == null || !n.isObject()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'layout' must be an object");
        }
        String base = n.path("base_package").asText("");
        if (!Names.isValidPackage(base)) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "layout.base_package '" + base + "' is not a valid Java package");
        }
        String runtime = n.hasNonNull("runtime_package") ? n.get("runtime_package").asText() : base + ".runtime";
        if (!Names.isValidPackage(runtime)) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "layout.runtime_package '" + runtime + "' is not a valid Java package");
        }
        String srcDir = n.path("java_source_dir").asText("src/main/java");
        if (Path.of(srcDir).isAbsolute() || Path.of(srcDir).normalize().startsWith("..")) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "layout.java_source_dir must be a relative path inside the output");
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        for (var it = n.path("package_overrides").properties().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!Names.isValidPackage(e.getValue().asText())) {
                throw new ProtocolException(ErrorCode.INVALID_PARAMS, "package override for '" + e.getKey() + "' is not a valid Java package");
            }
            overrides.put(normalizeDir(e.getKey()), e.getValue().asText());
        }
        // Defaults match cobol_to_java.Layout.
        List<String> dirs = n.has("copybook_dirs") ? strings(n.get("copybook_dirs")) : List.of("copy", "copybooks");
        List<String> exts = n.has("copybook_extensions") ? strings(n.get("copybook_extensions")) : List.of(".cpy", ".copy");
        return new Layout(base, srcDir, dirs, exts, runtime, overrides);
    }

    private static List<String> strings(JsonNode n) {
        List<String> out = new ArrayList<>();
        if (n != null) {
            n.forEach(v -> out.add(v.asText()));
        }
        return out;
    }

    private static String normalizeDir(String dir) {
        return dir.replace('\\', '/').replaceAll("^/+|/+$", "");
    }

    /** The package for a program at {@code relativePath} (relative to the source root). */
    public String packageFor(Path relativePath) {
        Path dir = relativePath.getParent();
        String relDir = dir == null ? "" : normalizeDir(dir.toString());
        if (packageOverrides.containsKey(relDir)) {
            return packageOverrides.get(relDir);
        }
        StringBuilder pkg = new StringBuilder(basePackage);
        if (dir != null) {
            for (Path part : dir) {
                pkg.append('.').append(Names.packageSegment(part.toString()));
            }
        }
        return pkg.toString();
    }

    /**
     * Copybook search path for a program: its own directory, then {@code copybook_dirs} entries in
     * each directory from the program's up to the source root, then {@code extra} paths.
     */
    public List<Path> copybookSearchPath(Path sourceRoot, Path programFile, List<Path> extra) {
        List<Path> dirs = new ArrayList<>();
        Path dir = programFile.getParent();
        dirs.add(dir);
        for (Path d = dir; d != null && d.startsWith(sourceRoot); d = d.getParent()) {
            for (String name : copybookDirs) {
                Path candidate = d.resolve(name);
                if (candidate.toFile().isDirectory() && !dirs.contains(candidate)) {
                    dirs.add(candidate);
                }
            }
        }
        for (Path p : extra) {
            if (p.toFile().isDirectory() && !dirs.contains(p)) {
                dirs.add(p);
            }
        }
        return dirs;
    }
}
