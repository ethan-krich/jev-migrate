package transpiler.cobol2java.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.ProtocolException;

/**
 * The libraries of a session ({@code initialize.libraries}): which COBOL parts each one claims,
 * and the index of its Java source. Loading never touches the network: a Maven coordinate is
 * looked up in the local caches ({@link Caches}).
 */
public final class Libraries {
    public static final Libraries NONE = new Libraries(List.of(), null);

    /** The verbs a library can claim: each statement's operands and receiver map to a call's arguments and result. */
    public static final Set<String> VERBS = Set.of("MOVE", "DISPLAY", "ACCEPT");

    /** The operations a library can claim (verbs, intrinsic functions, system routines), as {@code Cobol.operation} names them. */
    public static final java.util.function.Predicate<String> OPERATION_NAME =
            n -> n.matches("FUNCTION [A-Z0-9-]+|CALL '[^']+'") || VERBS.contains(n);

    /** A COBOL part a library claims. {@code names} are as written (PROGRAM-IDs) or upper case (the others). */
    public record Selector(String kind, List<String> names, String pattern, String program) {}

    /** One library: its COBOL selectors, where its Java source came from, and its index. */
    public record Library(String id, List<Selector> selectors, String target, String coordinates, JavaIndex index,
            Map<String, List<String>> hints, int maxChain) {
        /** What the report and the Java comments call the Java side: the coordinates, or the source path. */
        public String java() {
            return coordinates != null ? coordinates : target;
        }

        /** The hinted method ids for a claimed part (matched without regard to case), or empty. */
        public List<String> hintsFor(String part) {
            for (var e : hints.entrySet()) {
                if (e.getKey().equalsIgnoreCase(part)) {
                    return e.getValue();
                }
            }
            return List.of();
        }
    }

    private final List<Library> libraries;
    private final Path targetProject;
    private Path sourceRoot;

    private Libraries(List<Library> libraries, Path targetProject) {
        this.libraries = List.copyOf(libraries);
        this.targetProject = targetProject;
    }

    public List<Library> all() {
        return libraries;
    }

    public boolean isEmpty() {
        return libraries.isEmpty();
    }

    /** The project the output goes into, whose build files are checked for each library used; null when not given. */
    public Path targetProject() {
        return targetProject;
    }

    /** Parses {@code initialize.libraries} and indexes each library's Java source. */
    public static Libraries parse(JsonNode node, Path sourceRoot) throws ProtocolException {
        if (node == null || node.isNull()) {
            return NONE;
        }
        List<String> problems = new ArrayList<>();
        if (!node.isObject() || node.path("version").asInt() != 1 || !node.path("libraries").isArray()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'libraries' must be {version: 1, libraries: [...]}");
        }
        JsonNode caches = node.path("caches");
        Caches lookup = new Caches(caches.hasNonNull("maven") ? Path.of(caches.get("maven").asText()) : null,
                caches.hasNonNull("gradle") ? Path.of(caches.get("gradle").asText()) : null);
        List<Library> out = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (JsonNode lib : node.get("libraries")) {
            String id = lib.path("id").asText("");
            String where = "library '" + id + "'";
            if (id.isEmpty()) {
                problems.add("a library has no id");
                continue;
            }
            List<Selector> selectors = new ArrayList<>();
            for (JsonNode s : lib.path("source")) {
                List<String> names = new ArrayList<>();
                s.path("names").forEach(n -> names.add(n.asText()));
                selectors.add(new Selector(s.path("kind").asText(), names, s.hasNonNull("pattern") ? s.get("pattern").asText() : null,
                        s.hasNonNull("program") ? s.get("program").asText() : null));
            }
            if (selectors.isEmpty()) {
                problems.add(where + ": 'source' must list at least one selector");
            }
            JsonNode target = lib.path("target");
            String kind = target.path("kind").asText();
            String coordinates = null;
            Path path;
            try {
                switch (kind) {
                    case "maven" -> {
                        coordinates = target.path("coordinates").asText();
                        path = lookup.sourcesJar(coordinates);
                    }
                    case "path", "jar" -> {
                        path = Path.of(target.path("path").asText());
                        if (!path.isAbsolute() || !Files.exists(path)) {
                            throw new Caches.Unavailable(kind + " " + path + " does not exist");
                        }
                    }
                    default -> {
                        problems.add(where + ": unknown target kind '" + kind + "'");
                        continue;
                    }
                }
            } catch (Caches.Unavailable e) {
                unavailable.add(where + ": " + e.getMessage());
                continue;
            }
            List<String> expose = new ArrayList<>();
            lib.path("expose").forEach(e -> expose.add(e.asText()));
            JavaIndex index;
            try {
                index = JavaIndex.of(JavaIndex.read(path), expose);
            } catch (IOException | RuntimeException e) {
                unavailable.add(where + ": cannot read " + path + ": " + e.getMessage());
                continue;
            }
            if (index.members().isEmpty()) {
                problems.add(where + ": " + path + " declares no public constructor or method"
                        + (expose.isEmpty() ? "" : " in the exposed classes " + expose));
            }
            Map<String, List<String>> hints = new LinkedHashMap<>();
            lib.path("hints").properties().forEach(e -> {
                List<String> ids = new ArrayList<>();
                e.getValue().forEach(v -> ids.add(v.asText()));
                hints.put(e.getKey(), ids);
            });
            for (var e : hints.entrySet()) {
                for (String hint : e.getValue()) {
                    if (index.members().stream().noneMatch(m -> m.matches(hint))) {
                        problems.add(where + ": hint " + hint + " for " + e.getKey() + " names no public member of the library");
                    }
                }
            }
            int maxChain = lib.path("max_chain").asInt(4);
            if (maxChain < 1 || maxChain > 8) {
                problems.add(where + ": max_chain must be from 1 to 8");
            }
            out.add(new Library(id, selectors, path.toString(), coordinates, index, hints, maxChain));
        }
        if (!unavailable.isEmpty()) {
            ObjectNode details = Json.object();
            unavailable.forEach(details.putArray("problems")::add);
            throw new ProtocolException(ErrorCode.LIBRARY_UNAVAILABLE, unavailable.size() + " library(ies) unavailable; first: "
                    + unavailable.getFirst(), details);
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        Libraries libs = new Libraries(out, node.hasNonNull("target_project") ? Path.of(node.get("target_project").asText()) : null);
        libs.sourceRoot = sourceRoot;
        return libs;
    }

    /**
     * Checks the COBOL side against the parsed project: a path selector matches a program, a
     * paragraph or copybook exists, an operation is one a library can claim, and every hint names
     * a claimed part. Throws {@code invalid_library} listing every problem.
     *
     * @param programs relative path of each parsed program to its PROGRAM-ID as written
     * @param copybooks the project's copybook names (upper case, no extension)
     * @param paragraphs PROGRAM-ID (upper case) to its paragraph and section names (upper case)
     */
    public void check(Map<String, String> programs, Set<String> copybooks, Map<String, Set<String>> paragraphs) throws ProtocolException {
        List<String> problems = new ArrayList<>();
        for (Library lib : libraries) {
            String where = "library '" + lib.id() + "'";
            List<String> parts = new ArrayList<>();
            for (Selector s : lib.selectors()) {
                switch (s.kind()) {
                    case "program" -> parts.addAll(s.names());
                    case "path" -> {
                        List<String> matched = programs.keySet().stream().filter(f -> pathMatches(s.pattern(), f)).toList();
                        if (matched.isEmpty()) {
                            problems.add(where + ": Cobol.path('" + s.pattern() + "') matches no program of the project");
                        }
                        matched.forEach(f -> parts.add(programs.get(f)));
                    }
                    case "operation" -> {
                        for (String n : s.names()) {
                            if (!OPERATION_NAME.test(n)) {
                                problems.add(where + ": '" + n + "' is not an operation a library can claim: FUNCTION name, CALL 'routine' or one of "
                                        + new java.util.TreeSet<>(VERBS));
                            }
                            parts.add(n);
                        }
                    }
                    case "copybook" -> {
                        for (String n : s.names()) {
                            if (!copybooks.contains(n.toUpperCase(Locale.ROOT))) {
                                problems.add(where + ": copybook " + n + " is not part of the project");
                            }
                            parts.add(n);
                        }
                    }
                    case "paragraph" -> {
                        for (String n : s.names()) {
                            boolean found = s.program() != null
                                    ? paragraphs.getOrDefault(s.program().toUpperCase(Locale.ROOT), Set.of()).contains(n)
                                    : paragraphs.values().stream().anyMatch(p -> p.contains(n));
                            if (!found) {
                                problems.add(where + ": paragraph " + n + (s.program() != null ? " of program " + s.program() : "")
                                        + " does not exist");
                            }
                            parts.add(n);
                        }
                    }
                    default -> problems.add(where + ": unknown selector kind '" + s.kind() + "'");
                }
            }
            for (String key : lib.hints().keySet()) {
                if (parts.stream().noneMatch(p -> p.equalsIgnoreCase(key))) {
                    problems.add(where + ": hints name " + key + ", which the library does not claim");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
    }

    /** The library claiming the program CALLed as {@code name}, whose file (if it is in the project) is {@code file}. */
    public Optional<Library> program(String name, String file) {
        for (Library lib : libraries) {
            for (Selector s : lib.selectors()) {
                if (s.kind().equals("program") && s.names().contains(name)
                        || s.kind().equals("path") && file != null && pathMatches(s.pattern(), file)) {
                    return Optional.of(lib);
                }
            }
        }
        return Optional.empty();
    }

    /** The library claiming an operation ({@code FUNCTION INTEGER-OF-DATE}, {@code CALL 'CBL_TOUPPER'}, {@code INSPECT}). */
    public Optional<Library> operation(String name) {
        return find("operation", name, null);
    }

    /** The library claiming a paragraph or section of a program. */
    public Optional<Library> paragraph(String name, String programId) {
        return find("paragraph", name, programId);
    }

    /** The library claiming a copybook. */
    public Optional<Library> copybook(String name) {
        return find("copybook", name, null);
    }

    private Optional<Library> find(String kind, String name, String programId) {
        String upper = kind.equals("operation") && name.startsWith("CALL '") ? name : name.toUpperCase(Locale.ROOT);
        for (Library lib : libraries) {
            for (Selector s : lib.selectors()) {
                if (s.kind().equals(kind) && s.names().contains(upper)
                        && (s.program() == null || programId == null || s.program().equalsIgnoreCase(programId))) {
                    return Optional.of(lib);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a source-relative program path is selected by a {@code Cobol.path} pattern: a
     * directory (everything below it), a file, or a glob.
     */
    boolean pathMatches(String pattern, String file) {
        String p = pattern.replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (p.matches(".*[*?\\[{].*")) {
            PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + p);
            return m.matches(Path.of(file));
        }
        String dir = p.endsWith("/") ? p : sourceRoot != null && Files.isDirectory(sourceRoot.resolve(p)) ? p + "/" : null;
        return dir != null ? file.startsWith(dir) : file.equals(p);
    }

    private static ProtocolException invalid(List<String> problems) {
        ObjectNode details = Json.object();
        problems.forEach(details.putArray("problems")::add);
        return new ProtocolException(ErrorCode.INVALID_LIBRARY,
                "invalid libraries: " + problems.size() + " problem(s); first: " + problems.getFirst(), details);
    }
}
