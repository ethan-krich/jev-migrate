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
import transpiler.cobol2java.context.ContextGraph;
import transpiler.cobol2java.decision.Decision;
import transpiler.cobol2java.decision.Importance;
import transpiler.cobol2java.library.Libraries;
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

    /**
     * A nested program: its container ({@code null} for the file's top-level program), its
     * PROGRAM-ID as written, its class (nested in the top-level class) and IS COMMON.
     */
    record Nested(ProgramUnit unit, ProgramUnit container, String programId, String entryName, String className, boolean common) {}

    /** One discovered COBOL program and everything known about it. */
    static final class ProgramState {
        final Path path;
        final String relativePath;
        final List<ObjectNode> diagnostics = new ArrayList<>();
        ProgramUnit unit;
        transpiler.cobol2java.cobol.SourceMap sourceMap;
        String programId;
        /** The PROGRAM-ID as written (a literal's contents): the name CALL and CANCEL match, case-sensitively. */
        String entryName;
        String packageName;
        String className;
        Path outputPath;
        CompilationUnit java;
        List<Decision> decisions = List.of();
        int statements;
        int translatedStatements;
        boolean failed;
        /** Programs nested in this file's program (directly or not), in source order; generated as static nested classes. */
        final List<Nested> nested = new ArrayList<>();

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
    private final Importance importance;
    private final ContextGraph.Budget contextBudget;
    /** initialize.libraries: COBOL parts mapped to installed Java libraries (none by default). */
    private Libraries libraries = Libraries.NONE;
    private final Map<String, ProgramState> programs = new LinkedHashMap<>();
    private final List<String> copybooks = new ArrayList<>();
    private final Map<String, String> programDirectory = new HashMap<>();
    private final List<Decision> decisions = new ArrayList<>();
    private final Map<String, Decision> decisionsById = new HashMap<>();
    /** One note per setting a provider chose (initialize.setting_provenance), written at the top of each class. */
    private final List<String> settingNotes = new ArrayList<>();
    private State state = State.ACTIVE;

    private Session(String id, Path sourceRoot, Path outputRoot, Layout layout, MappingRegistry registry, Builtins builtins,
            Dialect dialect, Importance importance, ContextGraph.Budget contextBudget) {
        this.id = id;
        this.dialect = dialect;
        this.importance = importance;
        this.contextBudget = contextBudget;
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
        JsonNode options = Dialect.withSettings(params.path("options"), params.get("settings"));
        Dialect dialect = Dialect.parse(options);
        Importance importance = Importance.parse(params.get("importance"));
        ContextGraph.Budget contextBudget = contextBudget(params.get("context"));
        // Indexes each library's Java source up front: an unavailable one fails initialize (library_unavailable).
        Libraries libraries = Libraries.parse(params.get("libraries"), sourceRoot);
        CobolFrontend frontend;
        try {
            frontend = new CobolFrontend(CobolFrontend.Options.of(dialect.sourceFormat(),
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

        Session session = new Session(id, sourceRoot, outputRoot, layout, registry, builtins, dialect, importance, contextBudget);
        session.libraries = libraries;
        session.settingNotes.addAll(settingNotes(params.get("settings"), params.get("setting_provenance")));
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
        session.checkLibraries();
        return session;
    }

    /** The COBOL side of the libraries, checked against the parsed programs (invalid_library lists every problem). */
    private void checkLibraries() throws ProtocolException {
        if (libraries.isEmpty()) {
            return;
        }
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, Set<String>> paragraphs = new HashMap<>();
        for (ProgramState ps : programs.values()) {
            if (ps.failed || ps.unit == null) {
                continue;
            }
            files.put(ps.relativePath, ps.entryName);
            List<ProgramUnit> units = new ArrayList<>(List.of(ps.unit));
            ps.nested.forEach(n -> units.add(n.unit()));
            for (int i = 0; i < units.size(); i++) {
                var pd = units.get(i).getProcedureDivision();
                String pid = i == 0 ? ps.programId : ps.nested.get(i - 1).programId();
                Set<String> names = paragraphs.computeIfAbsent(pid, k -> new HashSet<>());
                if (pd != null) {
                    pd.getParagraphs().forEach(p -> names.add(p.getName().toUpperCase(Locale.ROOT)));
                    for (var section : pd.getSections()) {
                        names.add(section.getName().toUpperCase(Locale.ROOT));
                        section.getParagraphs().forEach(p -> names.add(p.getName().toUpperCase(Locale.ROOT)));
                    }
                }
            }
        }
        Set<String> copybookNames = new HashSet<>();
        for (String c : copybooks) {
            String file = c.substring(c.lastIndexOf('/') + 1);
            copybookNames.add((file.contains(".") ? file.substring(0, file.lastIndexOf('.')) : file).toUpperCase(Locale.ROOT));
        }
        libraries.check(files, copybookNames, paragraphs);
    }

    /** {@code initialize.context}: {max_depth, max_chars}, each a non-negative integer (defaults 2 and 4000). */
    private static ContextGraph.Budget contextBudget(JsonNode node) throws ProtocolException {
        if (node == null || node.isNull()) {
            return ContextGraph.Budget.DEFAULT;
        }
        if (!node.isObject()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'context' must be an object {max_depth, max_chars}");
        }
        for (var it = node.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (!key.equals("max_depth") && !key.equals("max_chars")) {
                throw new ProtocolException(ErrorCode.INVALID_PARAMS, "unknown context key '" + key + "'");
            }
        }
        return new ContextGraph.Budget(nonNegative(node, "max_depth", ContextGraph.Budget.DEFAULT.maxDepth()),
                nonNegative(node, "max_chars", ContextGraph.Budget.DEFAULT.maxChars()));
    }

    private static int nonNegative(JsonNode node, String field, int fallback) throws ProtocolException {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return fallback;
        }
        if (!v.isInt() || v.asInt() < 0) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'" + field + "' must be a non-negative integer");
        }
        return v.asInt();
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
        // ProLeap lists nested programs beside their container; a unit's container is the closest unit around it.
        Map<ProgramUnit, ProgramUnit> containers = new java.util.IdentityHashMap<>();
        List<ProgramUnit> tops = new ArrayList<>();
        for (ProgramUnit u : units) {
            ProgramUnit container = null;
            for (ProgramUnit o : units) {
                if (o != u && contains(o, u) && (container == null || contains(container, o))) {
                    container = o;
                }
            }
            if (container == null) {
                tops.add(u);
            } else {
                containers.put(u, container);
            }
        }
        if (tops.size() != 1) {
            fail(ps, "unsupported_construct", "files with " + tops.size() + " separate programs (batched programs) are not supported");
            return;
        }
        ps.unit = tops.getFirst();
        var idDivision = ps.unit.getIdentificationDivision();
        if (idDivision == null || idDivision.getProgramIdParagraph() == null) {
            fail(ps, "syntax_error", "missing PROGRAM-ID");
            return;
        }
        var programId = idDivision.getProgramIdParagraph();
        // IS INITIAL is translated; the program's other attributes are decisions in its translation.
        ps.programId = programId.getName().toUpperCase(Locale.ROOT);
        var name = ((io.proleap.cobol.CobolParser.ProgramIdParagraphContext) programId.getCtx()).programName();
        ps.entryName = name.NONNUMERICLITERAL() != null ? name.getText().substring(1, name.getText().length() - 1) : name.getText();
        ps.packageName = layout.packageFor(Path.of(ps.relativePath));
        ps.className = Names.className(ps.programId);
        ps.outputPath = outputRoot.resolve(layout.javaSourceDir())
                .resolve(ps.packageName.replace('.', '/')).resolve(ps.className + ".java").normalize();
        Set<String> classNames = new HashSet<>(Set.of(ps.className));
        for (ProgramUnit u : units) {
            if (u == ps.unit) {
                continue;
            }
            var pid = u.getIdentificationDivision() == null ? null : u.getIdentificationDivision().getProgramIdParagraph();
            if (pid == null) {
                fail(ps, "syntax_error", "a nested program has no PROGRAM-ID");
                return;
            }
            var n = ((io.proleap.cobol.CobolParser.ProgramIdParagraphContext) pid.getCtx()).programName();
            String entry = n.NONNUMERICLITERAL() != null ? n.getText().substring(1, n.getText().length() - 1) : n.getText();
            String cls = Names.className(pid.getName().toUpperCase(Locale.ROOT));
            if (!classNames.add(cls) || RESERVED_CLASS_NAMES.contains(cls)) {
                fail(ps, "class_collision", "nested program " + entry + " would be class " + cls + ", which another name of the file already uses");
                return;
            }
            ps.nested.add(new Nested(u, containers.get(u) == ps.unit ? null : containers.get(u), pid.getName().toUpperCase(Locale.ROOT), entry, cls,
                    pid.getAttribute() == io.proleap.cobol.asg.metamodel.identification.ProgramIdParagraph.Attribute.COMMON));
        }
    }

    private static boolean contains(ProgramUnit outer, ProgramUnit inner) {
        var o = outer.getCtx();
        var i = inner.getCtx();
        return i.getStart().getTokenIndex() >= o.getStart().getTokenIndex() && i.getStop().getTokenIndex() <= o.getStop().getTokenIndex()
                && (i.getStart().getTokenIndex() != o.getStart().getTokenIndex() || i.getStop().getTokenIndex() != o.getStop().getTokenIndex());
    }

    /**
     * The programs a program of {@code ps} can CALL: the project's top-level programs, then (taking
     * precedence, as GnuCOBOL looks there first) the ones it contains directly and the COMMON
     * programs contained by its containers. Values: [class, nested record or null].
     */
    private Map<String, Object[]> visible(ProgramState ps, ProgramUnit unit) {
        Map<String, Object[]> out = new java.util.TreeMap<>();
        programDirectory.forEach((k, v) -> out.put(k, new Object[] {v, null}));
        // The chain of containers, innermost first (null stands for the top-level program).
        List<ProgramUnit> chain = new ArrayList<>();
        ProgramUnit at = unit == ps.unit ? null : unit;
        while (at != null) {
            ProgramUnit container = null;
            for (Nested n : ps.nested) {
                if (n.unit() == at) {
                    container = n.container();
                }
            }
            chain.add(container);
            at = container;
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (Nested n : ps.nested) {
                if (n.container() == chain.get(i) && n.common() && n.unit() != unit) {
                    out.put(n.entryName(), new Object[] {ps.fqcn() + "." + n.className(), n});
                }
            }
        }
        for (Nested n : ps.nested) {
            if (n.container() == (unit == ps.unit ? null : unit)) {
                out.put(n.entryName(), new Object[] {ps.fqcn() + "." + n.className(), n});
            }
        }
        return out;
    }

    /** Simple names that generated programs import (see ProgramTranslator) or use unqualified. */
    /** Every runtime class plus the java.lang / java.math types generated code names. */
    private static final java.util.Set<String> RESERVED_CLASS_NAMES = java.util.stream.Stream.concat(
            RuntimeSources.CLASSES.stream(),
            java.util.stream.Stream.of("BigDecimal", "String", "Object", "Exception", "Override", "Math", "System"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** USING item sizes per PROGRAM-ID, computed on first use from the parsed programs. */
    private final Map<String, Optional<java.util.List<Integer>>> parameterCache = new HashMap<>();

    /** ENTRY names (as written) to the program declaring them and the entry's number (1 for the first). */
    private final Map<String, String> entryPrograms = new HashMap<>();
    private final Map<String, Integer> entryIndexes = new HashMap<>();

    private Optional<java.util.List<Integer>> programParameters(String programId) {
        return parameterCache.computeIfAbsent(programId, pid -> {
            for (ProgramState ps : programs.values()) {
                if (!ps.failed && (pid.equals(ps.entryName) || ps.fqcn().equals(entryPrograms.get(pid)))) {
                    return ProgramTranslator.parameters(ps.unit, ps.sourceMap, ps.relativePath, ps.programId, dialect,
                            entryIndexes.getOrDefault(pid, 0));
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
                programDirectory.put(ps.entryName, ps.fqcn());
            }
        }
        for (ProgramState ps : programs.values()) {
            if (!ps.failed) {
                List<String> entries = ProgramTranslator.entryNames(ps.unit);
                for (int i = 0; i < entries.size(); i++) {
                    // An ENTRY name is called like a program; the first declaration of a name wins, as at link time.
                    if (programDirectory.putIfAbsent(entries.get(i), ps.fqcn()) == null) {
                        entryPrograms.put(entries.get(i), ps.fqcn());
                        entryIndexes.put(entries.get(i), i + 1);
                    }
                }
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
        if (!libraries.isEmpty()) {
            ArrayNode libs = r.putArray("libraries");
            for (Libraries.Library lib : libraries.all()) {
                ObjectNode l = libs.addObject();
                l.put("id", lib.id());
                l.put("java", lib.java());
                l.put("source", lib.target());
                l.put("hash", lib.index().hash());
                l.put("classes", lib.index().classes());
                l.put("members", lib.index().members().size());
                lib.index().skipped().forEach(l.putArray("skipped")::add);
            }
        }
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
                var out = translateUnit(ps, ps.unit, ps.programId, ps.className);
                ps.java = out.compilationUnit();
                // Settings a provider chose shape the whole program: say so once, at the top of its class.
                var type = ps.java.getType(0);
                if (!type.getMembers().isEmpty() && !settingNotes.isEmpty()) {
                    var first = type.getMember(0);
                    String existing = first.getComment().map(c -> " | " + c.getContent().strip()).orElse("");
                    first.setComment(new com.github.javaparser.ast.comments.LineComment(" " + String.join(" | ", settingNotes) + existing));
                }
                ps.statements = out.statements();
                ps.translatedStatements = out.translated();
                List<Decision> all = new ArrayList<>(out.decisions());
                var top = ps.java.getType(0);
                for (Nested n : ps.nested) {
                    var inner = translateUnit(ps, n.unit(), n.programId(), n.className());
                    var cls = inner.compilationUnit().getType(0);
                    cls.setStatic(true);
                    top.addMember(cls);
                    ps.statements += inner.statements();
                    ps.translatedStatements += inner.translated();
                    all.addAll(inner.decisions());
                }
                ps.decisions = all;
                for (Decision d : all) {
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

    /** Translates one program of a file (the top-level one or a nested one) with the names it can CALL. */
    private ProgramTranslator.Result translateUnit(ProgramState ps, ProgramUnit unit, String programId, String className) {
        Map<String, Object[]> scope = visible(ps, unit);
        Map<String, String> classes = new java.util.TreeMap<>();
        scope.forEach((k, v) -> classes.put(k, (String) v[0]));
        Map<String, Integer> entries = new HashMap<>(entryIndexes);
        scope.forEach((k, v) -> {
            if (v[1] != null) {
                entries.remove(k);
            }
        });
        java.util.function.Function<String, Optional<List<Integer>>> parameters = pid -> scope.get(pid) != null && scope.get(pid)[1] instanceof Nested n
                ? ProgramTranslator.parameters(n.unit(), ps.sourceMap, ps.relativePath, n.programId(), dialect, 0)
                : programParameters(pid);
        // The containing programs, innermost first, whose GLOBAL items this one sees.
        List<Map.Entry<ProgramUnit, String>> containers = new ArrayList<>();
        ProgramUnit at = unit;
        while (at != ps.unit) {
            ProgramUnit current = at;
            Nested n = ps.nested.stream().filter(x -> x.unit() == current).findFirst().orElseThrow();
            at = n.container() == null ? ps.unit : n.container();
            ProgramUnit owner = at;
            String ownerClass = owner == ps.unit ? ps.fqcn()
                    : ps.fqcn() + "." + ps.nested.stream().filter(x -> x.unit() == owner).findFirst().orElseThrow().className();
            containers.add(Map.entry(owner, ownerClass));
        }
        return new ProgramTranslator(new ProgramTranslator.Input(unit, ps.sourceMap, ps.relativePath, programId,
                ps.packageName, className, layout.runtimePackage(), registry, builtins,
                pid -> Optional.ofNullable(classes.get(pid)), dialect, parameters, classes, Map.copyOf(entries), containers)).translate();
    }

    private void register(Decision d) {
        d.register("decision-" + (decisions.size() + 1), TARGET_LANGUAGE, dialect.hash(), importance, contextBudget);
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
            } else if (d.setting() != null) {
                code = "setting";
                problem = "a setting decision is applied by translating again with initialize.settings, not by resolve";
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
            List<Decision> nested = d.resolve(selection.get("candidate_id").asText(), decidedBy(r));
            ProgramState owner = ownerOf(d);
            for (Decision n : nested) {
                register(n);
                owner.decisions.add(n);
                raised.add(n.toJson(TARGET_LANGUAGE));
            }
            if (d.statementTranslated()) {
                owner.translatedStatements++;
            }
            applied.add(decisionId);
        }
        ArrayNode pending = result.putArray("pending");
        decisions.stream().filter(x -> x.state() == Decision.State.PENDING).forEach(x -> pending.add(x.id()));
        return new Outcome(pending.isEmpty() ? Protocol.STATUS_OK : Protocol.STATUS_INCOMPLETE, result);
    }

    /**
     * The comment for a resolution, written into the generated code: who decided, with the
     * provider's confidence when it reports one ({@code Decided by jev (confidence 0.87): <candidate>}).
     */
    public static String decidedBy(JsonNode resolution) {
        String provider = resolution.path("provider").asText("");
        StringBuilder sb = new StringBuilder("Decided by ").append(provider.isEmpty() ? "a provider" : provider);
        if (resolution.path("confidence").isNumber()) {
            sb.append(String.format(java.util.Locale.ROOT, " (confidence %.2f)", resolution.get("confidence").asDouble()));
        }
        return sb.append(": ").append(resolution.path("selection").path("candidate_id").asText()).toString();
    }

    /**
     * The notes for settings a provider chose: {@code Dialect.move_overlap = "ibm" decided by jev
     * (confidence 0.91)}. Settings set by the project itself have no provenance and no note.
     */
    static List<String> settingNotes(JsonNode settings, JsonNode provenance) {
        List<String> notes = new ArrayList<>();
        if (provenance == null || !provenance.isObject() || settings == null || !settings.isObject()) {
            return notes;
        }
        for (var e : provenance.properties()) {
            JsonNode value = settings.get(e.getKey());
            if (value == null) {
                continue;
            }
            String provider = e.getValue().path("provider").asText("");
            StringBuilder sb = new StringBuilder("Dialect.").append(e.getKey()).append(" = ").append(value)
                    .append(" decided by ").append(provider.isEmpty() ? "a provider" : provider);
            if (e.getValue().path("confidence").isNumber()) {
                sb.append(String.format(java.util.Locale.ROOT, " (confidence %.2f)", e.getValue().get("confidence").asDouble()));
            }
            notes.add(sb.toString());
        }
        return notes;
    }

    private ProgramState ownerOf(Decision d) {
        for (ProgramState ps : programs.values()) {
            if (ps.decisions.contains(d)) {
                return ps;
            }
        }
        throw new IllegalStateException("decision " + d.id() + " has no owning program");
    }

    // ---------------------------------------------------------- expand_context

    /** The text of truncated context nodes of a decision, and their neighbours one level deeper. */
    Outcome expandContext(JsonNode params) throws ProtocolException {
        requireActive();
        String decisionId = Json.requireText(params, "decision_id");
        Decision d = decisionsById.get(decisionId);
        if (d == null) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "no decision '" + decisionId + "' in session " + id);
        }
        ArrayNode refs = Json.requireArray(params, "refs");
        if (refs.isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_PARAMS, "'refs' must name at least one ref");
        }
        int maxChars = nonNegative(params, "max_chars", contextBudget.maxChars());
        ObjectNode result = Json.object();
        result.put("decision_id", decisionId);
        ArrayNode nodes = result.putArray("refs");
        ArrayNode rejected = result.putArray("rejected");
        List<String> wanted = new ArrayList<>();
        for (JsonNode r : refs) {
            String ref = r.isTextual() ? r.asText() : r.toString();
            if (r.isTextual() && d.context() != null && d.context().knows(ref)) {
                if (!wanted.contains(ref)) {
                    wanted.add(ref);
                }
            } else {
                ObjectNode rej = rejected.addObject();
                rej.put("ref", ref);
                rej.put("code", "unknown_ref");
                rej.put("message", "decision " + decisionId + " has no context node '" + ref + "'");
            }
        }
        if (!wanted.isEmpty()) {
            d.context().expand(wanted, maxChars).forEach(nodes::add);
        }
        return new Outcome(Protocol.STATUS_OK, result);
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
