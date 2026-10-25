package transpiler.cobol2java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.proleap.cobol.asg.metamodel.ProgramUnit;
import io.proleap.cobol.preprocessor.CobolPreprocessor.CobolSourceFormatEnum;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import transpiler.cobol2java.cobol.CobolFrontend;
import transpiler.cobol2java.cobol.SourceMap;
import transpiler.cobol2java.mapping.MappingRegistry;
import transpiler.cobol2java.protocol.Json;
import transpiler.cobol2java.session.Dialect;
import transpiler.cobol2java.translate.Builtins;
import transpiler.cobol2java.translate.ProgramTranslator;

/** Builds COBOL programs from snippets and translates them with the default rules. */
public final class TestSupport {
    public static final Builtins BUILTINS = new Builtins();

    private TestSupport() {}

    /** Fixed-format source: each line is placed in Area A/B (column 8 onward). */
    public static String cobol(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            sb.append("       ").append(l).append('\n');
        }
        return sb.toString();
    }

    public static String program(String data, String... procedure) {
        List<String> lines = new ArrayList<>(List.of("IDENTIFICATION DIVISION.", "PROGRAM-ID. T.", "DATA DIVISION.",
                "WORKING-STORAGE SECTION."));
        if (!data.isEmpty()) {
            lines.addAll(List.of(data.split("\n")));
        }
        lines.add("PROCEDURE DIVISION.");
        lines.addAll(List.of(procedure));
        return cobol(lines.toArray(String[]::new));
    }

    public static ProgramUnit parse(Path dir, String source) throws Exception {
        Path file = dir.resolve("T.cbl");
        Files.writeString(file, source, StandardCharsets.ISO_8859_1);
        var frontend = new CobolFrontend(new CobolFrontend.Options(CobolSourceFormatEnum.FIXED, StandardCharsets.ISO_8859_1, false));
        return frontend.parse(file, List.of(dir), List.of(".cpy")).program().getCompilationUnits().getFirst().getProgramUnit();
    }

    public static ObjectNode rule(String id, String source, String handler, Map<String, Object> when, int priority) {
        ObjectNode r = Json.object();
        r.put("id", id);
        r.put("source", source);
        r.put("strategy", handler == null ? "unresolved" : "builtin");
        if (handler != null) {
            r.put("handler", handler);
        }
        r.set("when", Json.MAPPER.valueToTree(when));
        r.put("priority", priority);
        return r;
    }

    /** The same defaults the Python package ships (cobol_to_java.mappings.DEFAULT_RULES). */
    public static ArrayNode defaultRules() {
        ArrayNode rules = Json.array();
        rules.add(rule("cobol.move.to_alphanumeric", "MOVE", "move_to_alphanumeric", Map.of("target_category", "alphanumeric"), 100));
        rules.add(rule("cobol.move.to_numeric", "MOVE", "move_to_numeric", Map.of("target_category", "numeric"), 100));
        String[][] simple = {{"ADD", "add"}, {"SUBTRACT", "subtract"}, {"MULTIPLY", "multiply"}, {"DIVIDE", "divide"},
            {"COMPUTE", "compute"}, {"DISPLAY", "display"}, {"IF", "if"}, {"PERFORM", "perform"}, {"GOBACK", "goback"},
            {"EXIT", "exit"}, {"CONTINUE", "continue"}, {"CALL", "call_program"}};
        for (String[] s : simple) {
            rules.add(rule("cobol." + s[1], s[0], s[1], Map.of(), 100));
        }
        rules.add(rule("cobol.stop_run", "STOP", "stop_run", Map.of(), 100));
        rules.add(rule("cobol.set.condition", "SET", "set_condition", Map.of("form", List.of("true", "false")), 100));
        rules.add(rule("cobol.evaluate", "EVALUATE", "evaluate", Map.of(), 100));
        rules.add(rule("cobol.accept.stdin", "ACCEPT", "accept_stdin", Map.of("from", List.of("stdin", "device")), 100));
        rules.add(rule("cobol.accept.datetime", "ACCEPT", "accept_datetime",
                Map.of("from", List.of("date", "date_yyyymmdd", "day", "day_yyyyddd", "day_of_week", "time")), 100));
        rules.add(rule("cobol.exhibit", "EXHIBIT", "exhibit", Map.of("changed", false), 100));
        rules.add(rule("cobol.move.to_group", "MOVE", "move_group", Map.of("target_category", "group"), 100));
        rules.add(rule("cobol.move.to_edited", "MOVE", "move_to_edited",
                Map.of("target_category", List.of("numeric_edited", "alphanumeric_edited")), 100));
        rules.add(rule("cobol.move.from_edited", "MOVE", "move_from_edited",
                Map.of("source_category", "numeric_edited", "target_category", "numeric"), 100));
        rules.add(rule("cobol.initialize", "INITIALIZE", "initialize", Map.of(), 100));
        rules.add(rule("cobol.string", "STRING", "string", Map.of(), 100));
        rules.add(rule("cobol.go_to", "GO_TO", "go_to", Map.of(), 100));
        rules.add(rule("cobol.alter", "ALTER", "alter", Map.of(), 100));
        rules.add(rule("cobol.unstring", "UNSTRING", "unstring", Map.of(), 100));
        rules.add(rule("cobol.inspect", "INSPECT", "inspect", Map.of(), 100));
        rules.add(rule("cobol.set.index", "SET", "set_index", Map.of("form", List.of("to", "up_by", "down_by")), 100));
        rules.add(rule("cobol.search", "SEARCH", "search", Map.of("all", false), 100));
        rules.add(rule("cobol.search_all", "SEARCH", "search_all", Map.of("all", true), 100));
        return rules;
    }

    public static JsonNode mappings(ArrayNode rules) {
        ObjectNode m = Json.object();
        m.put("version", 1);
        m.set("rules", rules);
        return m;
    }

    public static MappingRegistry registry(ArrayNode rules) throws Exception {
        return MappingRegistry.parse(mappings(rules), BUILTINS);
    }

    public static ProgramTranslator.Result translate(Path dir, String source, ArrayNode rules) throws Exception {
        return translate(dir, source, rules, Map.of());
    }

    public static ProgramTranslator.Result translate(Path dir, String source, ArrayNode rules, Map<String, String> programs)
            throws Exception {
        return translate(dir, source, rules, programs, Dialect.defaults());
    }

    public static ProgramTranslator.Result translate(Path dir, String source, ArrayNode rules, Map<String, String> programs,
            Dialect dialect) throws Exception {
        return new ProgramTranslator(new ProgramTranslator.Input(parse(dir, source), SourceMap.identity(), "T.cbl", "T", "test.gen", "T",
                "transpiler.cobol.runtime", registry(rules), BUILTINS, id -> Optional.ofNullable(programs.get(id)), dialect)).translate();
    }

    /** A dialect from JSON options, e.g. {@code dialect("{\"encoding\": \"ascii\"}")}. */
    public static Dialect dialect(String json) throws Exception {
        return Dialect.parse(Json.MAPPER.readTree(json));
    }

    /** Compiles generated Java against the runtime on the test classpath; returns diagnostics. */
    public static String compile(String fqcn, String source) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject file = new SimpleJavaFileObject(
                java.net.URI.create("string:///" + fqcn.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        Path out;
        try {
            out = Files.createTempDirectory("gen-classes");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        boolean ok = compiler.getTask(null, null, diagnostics,
                List.of("-classpath", System.getProperty("java.class.path"), "-d", out.toString(), "-Xlint:all", "-Werror"),
                null, List.of(file)).call();
        return ok ? "" : diagnostics.getDiagnostics().toString();
    }
}
