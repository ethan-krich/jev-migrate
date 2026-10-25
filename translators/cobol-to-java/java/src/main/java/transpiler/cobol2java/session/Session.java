package transpiler.cobol2java.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.ast.CompilationUnit;
import io.proleap.cobol.asg.metamodel.ProgramUnit;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import transpiler.cobol2java.cobol.CobolFrontend;
import transpiler.cobol2java.decision.Decision;
import transpiler.cobol2java.mapping.MappingRegistry;
import transpiler.cobol2java.protocol.ErrorCode;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.protocol.Protocol;
import transpiler.cobol2java.protocol.ProtocolException;
import transpiler.cobol2java.translate.Builtins;
import transpiler.cobol2java.translate.Names;
import transpiler.cobol2java.translate.ProgramTranslator;

/**
 * State of one translation project: parsed programs, partially built Java ASTs and decisions.
 *
 * <p>Lifecycle: {@code initialize} parses every program (so PROGRAM-IDs, class names and
 * cross-program references are known up front), {@code translate} builds ASTs per file,
 * {@code resolve} applies decisions, {@code finalize} writes files only if nothing is pending.
 */
public final class Session {
    public static final String TARGET_LANGUAGE = "java";

    enum State { ACTIVE, FINALIZED }

    /** One discovered COBOL program and everything known about it. */
    static final class ProgramState {
        final Path path;
        final String relativePath;
        final List<ObjectNode> diagnostics = new ArrayList<>();
        ProgramUnit unit;
        transpiler.cobol2java.cobol.SourceMap sourceMap;
        String programId;
        String packageName;
        String className;
        Path outputPath;
        CompilationUnit java;
        List<Decision> decisions = List.of();
        int statements;
        int translatedStatements;
        boolean failed;

        ProgramState(Path path, String relativePath) {
            this.path = path;
            this.relativePath = relativePath;
        }

        String fqcn() {
            return packageName + "." + className;
        }
    }

    private final String id;
    private final Path sourceRoot;
    private final Path outputRoot;
    private final Layout layout;
    private final MappingRegistry registry;
    private final Builtins builtins;
    private final Dialect dialect;
    private final Map<String, ProgramState> programs = new LinkedHashMap<>();
    private final List<String> copybooks = new ArrayList<>();
    private final Map<String, String> programDirectory = new HashMap<>();
    private final List<Decision> decisions = new ArrayList<>();
    private final Map<String, Decision> decisionsById = new HashMap<>();
    private State state = State.ACTIVE;

    private Session(String id, Path sourceRoot, Path outputRoot, Layout layout, MappingRegistry registry, Builtins builtins,
            Dialect dialect) {
        this.id = id;
        this.dialect = dialect;
        this.sourceRoot = sourceRoot;
        this.outputRoot = outputRoot;
        this.layout = layout;
        this.registry = registry;
        this.builtins = builtins;
    }

    // -------------------------------------------------------------- initialize

    static Session initialize(String id, JsonNode params, Builtins builtins, Consumer<ObjectNode> progress)
            throws ProtocolException {
        String target = Json.requireText(params, "target_language");
        if (!TARGET_LANGUAGE.equals(target)) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "this worker translates to java, not " + target);
        }
        Path sourceRoot = absolute(Json.requireText(params, "source_root"), "source_root");
        Path outputRoot = absolute(Json.requireText(params, "output_root"), "output_root");
        Layout layout = Layout.parse(params.get("layout"));
        MappingRegistry registry = MappingRegistry.parse(params.get("mappings"), builtins);
        JsonNode options = params.path("options");
        Dialect dialect = Dialect.parse(options);
        CobolFrontend frontend;
        try {
            frontend = new CobolFrontend(new CobolFrontend.Options(
                    CobolFrontend.Options.parseFormat(dialect.sourceFormat()),
                    Charset.forName(options.path("source_encoding").asText("ISO-8859-1")),
                    dialect.debuggingLines()));
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "invalid options: " + e.getMessage());
        }
        if (!frontend.charset().equals(StandardCharsets.ISO_8859_1)) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "unsupported options.source_encoding "
                    + frontend.charset().name() + ": the runtime is byte-oriented and only ISO-8859-1 is preserved exactly");
        }
        List<Path> extraCopybookPaths = new ArrayList<>();
        for (JsonNode p : params.path("copybook_paths")) {
            extraCopybookPaths.add(absolute(p.asText(), "copybook_paths[]"));
        }

        Session session = new Session(id, sourceRoot, outputRoot, layout, registry, builtins, dialect);
        ArrayNode files = Json.requireArray(params, "files");
        List<ProgramState> toParse = new ArrayList<>();
        for (JsonNode f : files) {
            Path path = absolute(Json.requireText(f, "path"), "files[].path");
            if (!path.startsWith(sourceRoot)) {
                throw new ProtocolException(ErrorCode.INVALID_PARAMS, "file " + path + " is outside source_root " + sourceRoot);
            }
            String rel = sourceRoot.relativize(path).toString().replace('\\', '/');
            String role = Json.requireText(f, "role");
            switch (role) {
                case "program" -> {
                    if (session.programs.containsKey(rel)) {
                        throw new ProtocolException(ErrorCode.INVALID_PARAMS, "duplicate file " + rel);
                    }
                    ProgramState ps = new ProgramState(path, rel);
                    session.programs.put(rel, ps);
                    toParse.add(ps);
                }
                case "copybook" -> session.copybooks.add(rel);
                default -> throw new ProtocolException(ErrorCode.INVALID_PARAMS, "unknown file role '" + role + "'");
            }
        }

        int index = 0;
        for (ProgramState ps : toParse) {
            session.parse(ps, frontend, extraCopybookPaths);
            ObjectNode event = Json.object();
            event.put("phase", "parse");
            event.put("file", ps.relativePath);
            event.put("index", ++index);
            event.put("total", toParse.size());
            progress.accept(event);
        }
        session.checkNameCollisions();
        return session;
    }

    private static Path absolute(String value, String field) throws ProtocolException {
        Path p = Path.of(value);
        if (!p.isAbsolute()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'" + field + "' must be an absolute path: " + value);
        }
        return p.normalize();
    }

    private void parse(ProgramState ps, CobolFrontend frontend, List<Path> extraCopybookPaths) {
        CobolFrontend.Parsed parsed;
        try {
            parsed = frontend.parse(ps.path, layout.copybookSearchPath(sourceRoot, ps.path, extraCopybookPaths),
                    layout.copybookExtensions());
        } catch (CobolFrontend.ParseFailure e) {
            fail(ps, e.kind(), e.getMessage());
            return;
        }
        ps.sourceMap = parsed.sourceMap();
        if (!ps.sourceMap.exact()) {
            ps.diagnostics.add(diagnostic("warning", "approximate_line_numbers",
                    "line numbers refer to the preprocessed source (COPY/REPLACE expansion could not be mapped back)",
                    ps.relativePath));
        }
        List<ProgramUnit> units = new ArrayList<>();
        parsed.program().getCompilationUnits().forEach(cu -> units.addAll(cu.getProgramUnits()));
        if (units.size() != 1) {
            fail(ps, "unsupported_construct", "files with " + units.size() + " program units (nested or batched programs) are not supported");
            return;
        }
        ps.unit = units.getFirst();
        var idDivision = ps.unit.getIdentificationDivision();
        if (idDivision == null || idDivision.getProgramIdParagraph() == null) {
            fail(ps, "syntax_error", "missing PROGRAM-ID");
            return;
        }
        var programId = idDivision.getProgramIdParagraph();
        // IS INITIAL is translated; the program's other attributes are decisions in its translation.
        ps.programId = programId.getName().toUpperCase(Locale.ROOT);
        ps.packageName = layout.packageFor(Path.of(ps.relativePath));
        ps.className = Names.className(ps.programId);
        ps.outputPath = outputRoot.resolve(layout.javaSourceDir())
                .resolve(ps.packageName.replace('.', '/')).resolve(ps.className + ".java").normalize();
    }

    /** Simple names that generated programs import (see ProgramTranslator) or use unqualified. */
    /** Every runtime class plus the java.lang / java.math types generated code names. */
    private static final java.util.Set<String> RESERVED_CLASS_NAMES = java.util.stream.Stream.concat(
            RuntimeSources.CLASSES.stream(),
            java.util.stream.Stream.of("BigDecimal", "String", "Object", "Exception", "Override", "Math", "System"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** USING item sizes per PROGRAM-ID, computed on first use from the parsed programs. */
    private final Map<String, Optional<java.util.List<Integer>>> parameterCache = new HashMap<>();

    private Optional<java.util.List<Integer>> programParameters(String programId) {
        return parameterCache.computeIfAbsent(programId, pid -> {
            for (ProgramState ps : programs.values()) {
                if (!ps.failed && ps.programId != null && ps.programId.equalsIgnoreCase(pid)) {
                    return ProgramTranslator.parameters(ps.unit, ps.sourceMap, ps.relativePath, ps.programId, dialect);
                }
            }
            return Optional.empty();
        });
    }

    private void checkNameCollisions() {
        Map<String, List<ProgramState>> byClass = new LinkedHashMap<>();
        Map<String, List<ProgramState>> byProgramId = new LinkedHashMap<>();
        for (ProgramState ps : programs.values()) {
            if (ps.failed) {
                continue;
            }
            byClass.computeIfAbsent(ps.fqcn().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(ps);
            byProgramId.computeIfAbsent(ps.programId, k -> new ArrayList<>()).add(ps);
            if (RESERVED_CLASS_NAMES.contains(ps.className)) {
                fail(ps, "class_collision", "class name " + ps.className
                        + " clashes with a runtime type imported by generated programs");
            }
            if (ps.packageName.equals(layout.runtimePackage())) {
                fail(ps, "class_collision", "package " + ps.packageName + " is reserved for the runtime library");
            }
        }
        byClass.values().stream().filter(l -> l.size() > 1).forEach(l -> l.forEach(ps -> fail(ps, "class_collision",
                "class " + ps.fqcn() + " would be generated by " + l.stream().map(p -> p.relativePath).toList()
                        + " (class names are case-insensitive on some file systems)")));
        byProgramId.values().stream().filter(l -> l.size() > 1).forEach(l -> l.forEach(ps -> fail(ps, "duplicate_program_id",
                "PROGRAM-ID " + ps.programId + " is declared by " + l.stream().map(p -> p.relativePath).toList())));
        for (ProgramState ps : programs.values()) {
            if (!ps.failed) {
                programDirectory.put(ps.programId, ps.fqcn());
            }
        }
    }

    private static void fail(ProgramState ps, String code, String message) {
        ps.failed = true;
        ps.diagnostics.add(diagnostic("error", code, message, ps.relativePath));
    }

    private static ObjectNode diagnostic(String severity, String code, String message, String file) {
        ObjectNode d = Json.object();
        d.put("severity", severity);
        d.put("code", code);
        d.put("message", message);
        d.putObject("source").put("file", file);
        return d;
    }

    public String id() {
        return id;
    }

    ObjectNode initializeResult() {
        ObjectNode r = Json.object();
        r.put("session", id);
        ArrayNode list = r.putArray("programs");
        for (ProgramState ps : programs.values()) {
            list.add(programJson(ps));
        }
        ArrayNode cbs = r.putArray("copybooks");
        copybooks.forEach(cbs::add);
        r.put("mapping_rules", registry.rules().size());
        r.set("dialect", dialect.toJson());
        return r;
    }

    private ObjectNode programJson(ProgramState ps) {
        ObjectNode n = Json.object();
        n.put("file", ps.relativePath);
        n.put("status", status(ps));
        if (ps.programId != null) {
            n.put("program_id", ps.programId);
            n.put("package", ps.packageName);
            n.put("class", ps.className);
            n.put("output_path", outputRoot.relativize(ps.outputPath).toString().replace('\\', '/'));
        }
        n.put("statements", ps.statements);
        n.put("translated_statements", ps.translatedStatements);
        ArrayNode ds = n.putArray("decisions");
        ps.decisions.stream().filter(d -> d.state() == Decision.State.PENDING).forEach(d -> ds.add(d.id()));
        n.set("diagnostics", Json.MAPPER.valueToTree(ps.diagnostics));
        return n;
    }

    private static String status(ProgramState ps) {
        if (ps.failed) {
            return "failed";
        }
        if (ps.java == null) {
            return "parsed";
        }
        return ps.decisions.stream().anyMatch(d -> d.state() == Decision.State.PENDING) ? "incomplete" : "translated";
    }

    // --------------------------------------------------------------- translate

    record Outcome(String status, ObjectNode result) {}

    Outcome translate(JsonNode params, Consumer<ObjectNode> progress) throws ProtocolException {
        requireActive();
        List<ProgramState> targets = new ArrayList<>();
        JsonNode files = params.get("files");
        if (files == null || files.isNull()) {
            targets.addAll(programs.values());
        } else {
            if (!files.isArray()) {
                throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'files' must be an array of relative paths");
            }
            for (JsonNode f : files) {
                ProgramState ps = programs.get(f.asText());
                if (ps == null) {
                    throw new ProtocolException(ErrorCode.INVALID_PARAMS, "unknown file '" + f.asText() + "' (not passed to initialize)");
                }
                targets.add(ps);
            }
        }
        ObjectNode result = Json.object();
        ArrayNode fileResults = result.putArray("files");
        ArrayNode raised = result.putArray("decisions");
        boolean complete = true;
        int index = 0;
        for (ProgramState ps : targets) {
            if (!ps.failed && ps.java != null) {
                throw new ProtocolException(ErrorCode.INVALID_STATE, ps.relativePath + " has already been translated in this session");
            }
            if (!ps.failed) {
                var out = new ProgramTranslator(new ProgramTranslator.Input(ps.unit, ps.sourceMap, ps.relativePath, ps.programId,
                        ps.packageName, ps.className, layout.runtimePackage(), registry, builtins,
                        pid -> Optional.ofNullable(programDirectory.get(pid)), dialect, this::programParameters)).translate();
                ps.java = out.compilationUnit();
                ps.statements = out.statements();
                ps.translatedStatements = out.translated();
                ps.decisions = new ArrayList<>(out.decisions());
                for (Decision d : out.decisions()) {
                    register(d);
                    raised.add(d.toJson(TARGET_LANGUAGE));
                }
            }
            complete &= "translated".equals(status(ps));
            fileResults.add(programJson(ps));
            ObjectNode event = Json.object();
            event.put("phase", "translate");
            event.put("file", ps.relativePath);
            event.put("index", ++index);
            event.put("total", targets.size());
            progress.accept(event);
        }
        return new Outcome(complete ? Protocol.STATUS_OK : Protocol.STATUS_INCOMPLETE, result);
    }

    private void register(Decision d) {
        d.register("decision-" + (decisions.size() + 1), TARGET_LANGUAGE, dialect.hash());
        decisions.add(d);
        decisionsById.put(d.id(), d);
    }

    // ----------------------------------------------------------------- resolve

    Outcome resolve(JsonNode params) throws ProtocolException {
        requireActive();
        ArrayNode resolutions = Json.requireArray(params, "resolutions");
        ObjectNode result = Json.object();
        ArrayNode applied = result.putArray("applied");
        ArrayNode rejected = result.putArray("rejected");
        ArrayNode raised = result.putArray("new_decisions");
        Set<String> seen = new HashSet<>();
        for (JsonNode r : resolutions) {
            String decisionId = r.path("decision_id").asText("");
            String problem = null;
            String code = null;
            Decision d = decisionsById.get(decisionId);
            JsonNode selection = r.get("selection");
            if (!seen.add(decisionId)) {
                code = "duplicate_resolution";
                problem = "decision resolved more than once in this request";
            } else if (d == null) {
                code = "unknown_decision";
                problem = "no decision '" + decisionId + "' in session " + id;
            } else if (d.state() != Decision.State.PENDING) {
                code = "already_resolved";
                problem = "decision was already resolved with " + d.selectedCandidate();
            } else if (r.hasNonNull("fingerprint") && !r.get("fingerprint").asText().equals(d.fingerprint())) {
                code = "fingerprint_mismatch";
                problem = "fingerprint does not match the decision (stale or cached for different source)";
            } else if (selection == null || !selection.isObject() || !"candidate".equals(selection.path("kind").asText())) {
                code = "unsupported_selection";
                problem = "selection.kind must be 'candidate' in protocol version " + Protocol.VERSION;
            } else if (d.candidates().isEmpty()) {
                code = "no_candidates";
                problem = "decision has no candidates; it cannot be resolved by selection";
            } else if (!d.hasCandidate(selection.path("candidate_id").asText())) {
                code = "unknown_candidate";
                problem = "candidate '" + selection.path("candidate_id").asText() + "' is not offered by this decision";
            }
            if (problem != null) {
                ObjectNode rej = rejected.addObject();
                rej.put("decision_id", decisionId);
                rej.put("code", code);
                rej.put("message", problem);
                continue;
            }
            List<Decision> nested = d.resolve(selection.get("candidate_id").asText());
            ProgramState owner = ownerOf(d);
            for (Decision n : nested) {
                register(n);
                owner.decisions.add(n);
                raised.add(n.toJson(TARGET_LANGUAGE));
            }
            owner.translatedStatements++;
            applied.add(decisionId);
        }
        ArrayNode pending = result.putArray("pending");
        decisions.stream().filter(x -> x.state() == Decision.State.PENDING).forEach(x -> pending.add(x.id()));
        return new Outcome(pending.isEmpty() ? Protocol.STATUS_OK : Protocol.STATUS_INCOMPLETE, result);
    }

    private ProgramState ownerOf(Decision d) {
        for (ProgramState ps : programs.values()) {
            if (ps.decisions.contains(d)) {
                return ps;
            }
        }
        throw new IllegalStateException("decision " + d.id() + " has no owning program");
    }

    // ---------------------------------------------------------------- finalize

    Outcome finish(JsonNode params) throws ProtocolException {
        requireActive();
        ObjectNode result = Json.object();
        ArrayNode problems = result.putArray("blocking");
        for (ProgramState ps : programs.values()) {
            String s = status(ps);
            if (!"translated".equals(s)) {
                ObjectNode p = problems.addObject();
                p.put("file", ps.relativePath);
                p.put("status", s);
                p.put("message", switch (s) {
                    case "failed" -> "file could not be parsed or named; see diagnostics";
                    case "parsed" -> "file was never translated";
                    default -> "file has unresolved decisions";
                });
            }
        }
        ArrayNode pending = result.putArray("pending");
        decisions.stream().filter(d -> d.state() == Decision.State.PENDING).forEach(d -> pending.add(d.toJson(TARGET_LANGUAGE)));
        ArrayNode written = result.putArray("files_written");
        ArrayNode programList = result.putArray("programs");
        programs.values().forEach(ps -> programList.add(programJson(ps)));
        if (!problems.isEmpty()) {
            result.put("message", "no files were written: " + problems.size() + " file(s) are not fully translated");
            return new Outcome(Protocol.STATUS_INCOMPLETE, result);
        }
        try {
            for (ProgramState ps : programs.values()) {
                ProgramTranslator.finish(ps.java);
                write(ps.outputPath, ps.java.toString());
                ObjectNode w = written.addObject();
                w.put("path", outputRoot.relativize(ps.outputPath).toString().replace('\\', '/'));
                w.put("kind", "program");
                w.put("source", ps.relativePath);
                w.put("class", ps.fqcn());
            }
            Path runtimeDir = outputRoot.resolve(layout.javaSourceDir()).resolve(layout.runtimePackage().replace('.', '/'));
            for (var e : RuntimeSources.forPackage(layout.runtimePackage()).entrySet()) {
                Path p = runtimeDir.resolve(e.getKey() + ".java");
                write(p, e.getValue());
                ObjectNode w = written.addObject();
                w.put("path", outputRoot.relativize(p).toString().replace('\\', '/'));
                w.put("kind", "runtime");
                w.put("class", layout.runtimePackage() + "." + e.getKey());
            }
        } catch (IOException e) {
            throw new ProtocolException(ErrorCode.INTERNAL_ERROR, "failed to write output: " + e.getMessage());
        }
        state = State.FINALIZED;
        return new Outcome(Protocol.STATUS_OK, result);
    }

    private void write(Path path, String content) throws IOException {
        if (!path.normalize().startsWith(outputRoot)) {
            throw new IOException("refusing to write outside output_root: " + path);
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void requireActive() throws ProtocolException {
        if (state != State.ACTIVE) {
            throw new ProtocolException(ErrorCode.INVALID_STATE, "session " + id + " is finalized");
        }
    }
}
