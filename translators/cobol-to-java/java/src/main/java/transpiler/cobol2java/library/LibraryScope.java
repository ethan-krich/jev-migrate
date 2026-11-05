package transpiler.cobol2java.library;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import transpiler.cobol2java.protocol.Json;

/**
 * What a program's translation needs to know about the session's libraries: which library claims
 * a CALLed program, the claimed part's source for a provider to read, and the record of every
 * library use site (for the report, {@code review.md} and the generated code).
 */
public final class LibraryScope {
    public static final LibraryScope NONE = new LibraryScope(Libraries.NONE, name -> Optional.empty(), file -> Optional.empty());

    /** Characters of a claimed part's source sent with a decision. */
    public static final int MAX_PART_CHARS = 3000;

    /**
     * One use of a claimed COBOL part: where it is, and what was chosen. {@code outcome} is
     * {@code pending}, {@code library} (a library call replaced it), {@code translated} (the exact
     * translation was chosen, or no library member binds: {@code reason} says which).
     */
    public static final class Use {
        public final String library;
        public final String program;
        public final String file;
        public final int line;
        public final String selector;
        public final String part;
        public String outcome = "pending";
        public final List<String> chain = new ArrayList<>();
        /** The library types the generated call names (top-level types, to import). */
        public final List<String> types = new ArrayList<>();
        public String reason;
        public String decidedBy;
        public String statement;

        Use(String library, String program, String file, int line, String selector, String part) {
            this.library = library;
            this.program = program;
            this.file = file;
            this.line = line;
            this.selector = selector;
            this.part = part;
        }

        ObjectNode toJson() {
            ObjectNode n = Json.object();
            n.put("program", program);
            n.put("file", file);
            n.put("line", line);
            n.put("selector", selector);
            n.put("part", part);
            n.put("outcome", outcome);
            chain.forEach(n.putArray("chain")::add);
            if (reason != null) {
                n.put("reason", reason);
            }
            if (decidedBy != null) {
                n.put("decided_by", decidedBy);
            }
            if (statement != null) {
                n.put("java", statement);
            }
            return n;
        }
    }

    private final Libraries libraries;
    private final Function<String, Optional<String>> programFiles;
    private final Function<String, Optional<String>> sources;
    private final List<Use> uses = new ArrayList<>();

    /**
     * @param programFiles a CALL name to the relative path of the project program it reaches (empty outside the project)
     * @param sources a relative path to that file's source text
     */
    public LibraryScope(Libraries libraries, Function<String, Optional<String>> programFiles, Function<String, Optional<String>> sources) {
        this.libraries = libraries;
        this.programFiles = programFiles;
        this.sources = sources;
    }

    public Libraries libraries() {
        return libraries;
    }

    /**
     * The library claiming what a CALL names: a program (by PROGRAM-ID or by its file's path) or a
     * system routine ({@code Cobol.operation("CALL 'CBL_TOUPPER'")}).
     */
    public Optional<Libraries.Library> program(String name) {
        if (libraries.isEmpty() || name == null) {
            return Optional.empty();
        }
        return libraries.program(name, programFiles.apply(name).orElse(null))
                .or(() -> libraries.operation("CALL '" + name + "'"));
    }

    /** {@code program}, or {@code operation} when a CALL's target is claimed as a system routine. */
    public String programSelector(String name) {
        return libraries.program(name, programFiles.apply(name).orElse(null)).isPresent() ? "program" : "operation";
    }

    /** The project file of a CALLed program, or empty. */
    public Optional<String> programFile(String name) {
        return programFiles.apply(name);
    }

    /** A source file's whole text. */
    public Optional<String> text(String file) {
        return sources.apply(file);
    }

    /** A source file's text, capped at {@link #MAX_PART_CHARS}. */
    public Optional<String> source(String file) {
        return sources.apply(file).map(t -> t.length() > MAX_PART_CHARS ? t.substring(0, MAX_PART_CHARS) + "\n..." : t);
    }

    public Use record(String library, String program, String file, int line, String selector, String part) {
        Use u = new Use(library, program, file, line, selector, part);
        uses.add(u);
        return u;
    }

    public List<Use> uses() {
        return uses;
    }

    /** A position in the use records, for {@link #takeSince}. */
    public int mark() {
        return uses.size();
    }

    /**
     * Removes and returns the uses recorded since {@code mark}. A candidate's preview is generated
     * before it is chosen; its uses are staged with it and {@link #restore}d only when it is selected,
     * so a discarded alternative leaves no use behind.
     */
    public List<Use> takeSince(int mark) {
        List<Use> tail = uses.subList(mark, uses.size());
        List<Use> taken = new ArrayList<>(tail);
        tail.clear();
        return taken;
    }

    /** Records the uses of a candidate that was selected. */
    public void restore(List<Use> staged) {
        uses.addAll(staged);
    }

    /**
     * {@code libraries_used}: each library with at least one use site, with its uses. For a library
     * the output calls, {@code declared_in_target} says whether the target project declares it
     * (true, false, or null when it cannot be confirmed) and {@code target_note} why; both are
     * absent when no target project was given.
     */
    public ArrayNode report(Function<Libraries.Library, TargetProject.Check> target) {
        Map<String, List<Use>> byLibrary = new LinkedHashMap<>();
        for (Use u : uses) {
            byLibrary.computeIfAbsent(u.library, k -> new ArrayList<>()).add(u);
        }
        ArrayNode out = Json.MAPPER.createArrayNode();
        for (Libraries.Library lib : libraries.all()) {
            List<Use> list = byLibrary.get(lib.id());
            if (list == null) {
                continue;
            }
            ObjectNode n = out.addObject();
            n.put("id", lib.id());
            n.put("java", lib.java());
            TargetProject.Check check = list.stream().anyMatch(u -> u.outcome.equals("library")) ? target.apply(lib) : null;
            if (check != null) {
                if (check.declared() == null) {
                    n.putNull("declared_in_target");
                } else {
                    n.put("declared_in_target", check.declared());
                }
                n.put("target_note", check.note());
            }
            ArrayNode us = n.putArray("uses");
            list.forEach(u -> us.add(u.toJson()));
        }
        return out;
    }

    /** {@code partial} when any use site calls a library instead of the translation, else {@code exact}. */
    public String equivalence() {
        return uses.stream().anyMatch(u -> u.outcome.equals("library")) ? "partial" : "exact";
    }
}
