package transpiler.cobol2java.translate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import transpiler.cobol2java.context.ContextGraph;
import transpiler.cobol2java.decision.Candidate;
import transpiler.cobol2java.decision.Decision;
import transpiler.cobol2java.decision.Reason;
import transpiler.cobol2java.library.JavaMember;
import transpiler.cobol2java.library.LibraryScope;
import transpiler.cobol2java.protocol.Json;

/**
 * One use of a claimed COBOL part, decided as a chain of flat {@code library_call} decisions:
 * {@code start} (a static method or constructor), {@code args} (how the inputs bind, when
 * several bindings fit), {@code next} (an instance method, or {@code end}), {@code result} (the
 * output, when several fit) and {@code lifetime} (a constructor without arguments: a new object
 * per call or one per program). Every decision also offers {@code cobol:translate}: the exact
 * translation, which is already generated and stays in place until the chain is complete.
 *
 * <p>A step is asked only when the library side has more than one option there; a forced step
 * is taken without a decision. Each decision's facts hold the library's hash and the chain so
 * far, so its fingerprint changes with the library's version and with the steps before it.
 */
final class LibrarySite {
    static final String TRANSLATE = "cobol:translate";
    static final String END = "end";
    static final String PER_CALL = "lifetime:per_call";
    static final String PER_PROGRAM = "lifetime:per_program";

    /** The exact translation, generated in place after the anchor. */
    record Alternative(EmptyStmt anchor, List<Statement> statements, List<Decision> nested, boolean translated, String preview) {}

    /** What a complete chain generates: its statements and the decisions they raise. */
    record Generated(List<Statement> statements, List<Decision> nested) {}

    /**
     * Generates the code for a complete chain: {@code steps} storing into {@code out};
     * {@code instance} is the per-program object a chain starts with (null: created per use).
     * {@code keep}: the code is the one kept (not a preview), so it may change the class.
     */
    interface Finisher {
        Generated generate(List<LibraryChain.Step> steps, LibraryChain.Out out, Expression instance, boolean keep) throws HandlerException;
    }

    private final ProgramTranslator t;
    private final OpUnit unit;
    private final BlockStmt block;
    private final Map<String, Object> facts;
    private final String ruleId;
    private final LibraryChain chain;
    private final LibraryScope.Use use;
    private final ObjectNode part;
    private final String partLabel;
    private final Alternative alternative;
    private final Finisher finisher;
    private final ContextGraph graph;

    private final List<LibraryChain.Step> steps = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private JavaMember pending;
    private int mask;
    private LibraryChain.Out out;

    LibrarySite(ProgramTranslator t, OpUnit unit, BlockStmt block, Map<String, Object> facts, String ruleId, LibraryChain chain,
            LibraryScope.Use use, ObjectNode part, String partLabel, Alternative alternative, Finisher finisher, ContextGraph graph) {
        this.t = t;
        this.unit = unit;
        this.block = block;
        this.facts = facts;
        this.ruleId = ruleId;
        this.chain = chain;
        this.use = use;
        this.part = part;
        this.partLabel = partLabel;
        this.alternative = alternative;
        this.finisher = finisher;
        this.graph = graph;
    }

    /** The first decision: which static method or constructor starts the call (empty options: no decision). */
    Decision start(List<LibraryChain.Option> starts) {
        List<Candidate> candidates = new ArrayList<>();
        Map<String, JavaMember> byId = new LinkedHashMap<>();
        for (LibraryChain.Option o : starts) {
            JavaMember m = o.member();
            String id = "library:" + chain.library().id() + ":" + m.id();
            byId.put(id, m);
            String what = m.kind() == JavaMember.Kind.CONSTRUCTOR ? "creates a " + m.ownerName() : "calls " + m.ownerName() + "." + m.name();
            candidates.add(new Candidate(id, "library", what + " of the library " + chain.library().id() + " (" + chain.library().java()
                    + ") in place of " + partLabel + "; not equivalence-checked. " + (m.params().isEmpty() ? "No arguments"
                    : "Its arguments (" + chain.describeBinding(m, o.bindings().getFirst()) + (o.bindings().size() > 1 ? ", or another order" : "") + ")")
                    + (m.returnType().equals("void") ? "" : "; further calls on its result and the receiver are chosen next if needed"),
                    preview(List.of(new LibraryChain.Step(m, o.bindings().getFirst()))), List.of(), List.of(m.doc())));
        }
        return decision("start", partLabel + " is claimed by the library " + chain.library().id()
                + ": choose the call that does what it does, or translate the COBOL", candidates, chosen -> {
                    pending = byId.get(chosen);
                    return afterMember(0);
                });
    }

    /** After a member is chosen: bind its arguments (asking when several bindings fit), then continue. */
    private List<Decision> afterMember(int depthBefore) {
        List<int[]> bindings = chain.bindingsAfter(pending, mask, depthBefore + 1);
        if (bindings.size() == 1) {
            return bound(bindings.getFirst());
        }
        List<Candidate> candidates = new ArrayList<>();
        Map<String, int[]> byId = new LinkedHashMap<>();
        for (int[] b : bindings) {
            LibraryChain.Step s = new LibraryChain.Step(pending, b);
            String id = "args:" + s.bindingId();
            byId.put(id, b);
            List<LibraryChain.Step> with = new ArrayList<>(steps);
            with.add(s);
            candidates.add(new Candidate(id, "library", "pass " + chain.describeBinding(pending, b), preview(with),
                    List.of(), List.of(pending.doc())));
        }
        return List.of(decision("args", partLabel + ": choose which COBOL item each parameter of " + pending.ownerName() + "."
                + (pending.kind() == JavaMember.Kind.CONSTRUCTOR ? "new" : pending.name()) + " takes", candidates,
                chosen -> bound(byId.get(chosen))));
    }

    private List<Decision> bound(int[] binding) {
        steps.add(new LibraryChain.Step(pending, binding));
        mask |= LibraryChain.mask(binding);
        pending = null;
        return next();
    }

    /** The next call on the value so far, or the end of the chain. */
    private List<Decision> next() {
        String type = type();
        List<LibraryChain.Option> nexts = chain.nexts(type, mask, steps.size());
        boolean canEnd = !chain.ends(type, mask).isEmpty();
        if (nexts.isEmpty()) {
            return ended();
        }
        if (nexts.size() == 1 && !canEnd) {
            pending = nexts.getFirst().member();
            return afterMember(steps.size());
        }
        List<Candidate> candidates = new ArrayList<>();
        Map<String, JavaMember> byId = new LinkedHashMap<>();
        if (canEnd) {
            LibraryChain.Out first = chain.ends(type, mask).getFirst();
            candidates.add(new Candidate(END, "library", "stop here: store the value of the calls so far (a "
                    + display(type) + ") into "
                    + String.join(" or ", chain.ends(type, mask).stream().map(LibraryChain.Out::label).toList()),
                    statementPreview(first)));
        }
        for (LibraryChain.Option o : nexts) {
            JavaMember m = o.member();
            String id = "library:" + chain.library().id() + ":" + m.id();
            byId.put(id, m);
            List<LibraryChain.Step> with = new ArrayList<>(steps);
            with.add(new LibraryChain.Step(m, o.bindings().getFirst()));
            candidates.add(new Candidate(id, "library", "then call ." + m.name() + " on the " + display(type)
                    + (m.params().isEmpty() ? "" : " with " + chain.describeBinding(m, o.bindings().getFirst())), preview(with),
                    List.of(), List.of(m.doc())));
        }
        return List.of(decision("next", partLabel + ": the calls so far give a " + display(type)
                + "; choose the next call on it, or stop", candidates, chosen -> {
                    if (chosen.equals(END)) {
                        return ended();
                    }
                    pending = byId.get(chosen);
                    return afterMember(steps.size());
                }));
    }

    /** The chain is complete: choose the output when several fit, then the lifetime of a constructed object. */
    private List<Decision> ended() {
        List<LibraryChain.Out> outs = chain.ends(type(), mask);
        if (outs.size() == 1) {
            out = outs.getFirst();
            return lifetime();
        }
        List<Candidate> candidates = new ArrayList<>();
        Map<String, LibraryChain.Out> byId = new LinkedHashMap<>();
        for (LibraryChain.Out o : outs) {
            byId.put("result:" + o.id(), o);
            candidates.add(new Candidate("result:" + o.id(), "library", o.receiver() == null ? "the call returns nothing"
                    : "MOVE the result into " + o.label(), statementPreview(o)));
        }
        return List.of(decision("result", partLabel + ": choose which COBOL item receives the result", candidates, chosen -> {
            out = byId.get(chosen);
            return lifetime();
        }));
    }

    private List<Decision> lifetime() {
        JavaMember first = steps.getFirst().member();
        if (first.kind() != JavaMember.Kind.CONSTRUCTOR || !first.params().isEmpty() || steps.size() < 2) {
            return complete(false);
        }
        List<Candidate> candidates = List.of(
                new Candidate(PER_CALL, "library", "a new " + first.ownerName() + " at each use", previewStatement(out, false)),
                new Candidate(PER_PROGRAM, "library", "one " + first.ownerName() + " per program, created on its first use and kept "
                        + "(correct only if the object keeps no state that changes the results)", previewStatement(out, true)));
        return List.of(decision("lifetime", partLabel + ": choose how long the " + first.ownerName() + " lives", candidates,
                chosen -> complete(chosen.equals(PER_PROGRAM))));
    }

    /** Replaces the exact translation with the library call; returns the decisions its code raises. */
    private List<Decision> complete(boolean perProgram) {
        Generated g = build(out, perProgram, true);
        List<Statement> generated = new ArrayList<>(g.statements());
        int index = block.getStatements().indexOf(alternative.anchor());
        if (index < 0) {
            throw new IllegalStateException("the anchor of " + partLabel + " is no longer in the AST");
        }
        alternative.statements().forEach(block.getStatements()::remove);
        block.getStatements().remove(index);
        block.getStatements().addAll(index, generated);
        String said = notes.isEmpty() ? "" : ": " + String.join("; ", notes);
        ProgramTranslator.annotate(generated, "Library " + chain.library().id() + ", not equivalence-checked" + said);
        use.outcome = "library";
        use.decidedBy = notes.isEmpty() ? null : String.join("; ", notes);
        steps.forEach(st -> use.chain.add(st.member().id()));
        steps.forEach(st -> use.types.add(st.member().importName()));
        Statement plain = generated.getFirst().clone();
        plain.removeComment();
        use.statement = plain.toString();
        return g.nested();
    }

    /** {@code cobol:translate} at any step: the exact translation stays, and its own decisions are raised. */
    private List<Decision> translate(Decision d) {
        int index = block.getStatements().indexOf(alternative.anchor());
        if (index >= 0) {
            block.getStatements().remove(index);
        }
        use.outcome = "translated";
        use.reason = "cobol:translate chosen at the " + d.library().path("step").asText() + " step";
        use.decidedBy = notes.isEmpty() ? null : String.join("; ", notes);
        if (!alternative.translated()) {
            d.leaveStatementOpen();
        }
        return alternative.nested();
    }

    private interface Step {
        List<Decision> apply(String candidateId);
    }

    private Decision decision(String step, String message, List<Candidate> candidates, Step then) {
        List<Candidate> all = new ArrayList<>(candidates);
        all.add(new Candidate(TRANSLATE, "translate", "translate " + partLabel + " exactly as the COBOL does it (equivalence-checked "
                + "translation; the library is not used here)", alternative.preview()));
        Map<String, Object> f = new LinkedHashMap<>(facts);
        f.put("library", chain.library().id());
        f.put("library_hash", chain.library().index().hash());
        f.put("step", step);
        List<String> so = new ArrayList<>();
        steps.forEach(s -> so.add(s.member().id() + "#" + s.bindingId()));
        if (pending != null) {
            so.add(pending.id());
        }
        f.put("chain", so);
        ObjectNode info = Json.object();
        info.put("id", chain.library().id());
        info.put("java", chain.library().java());
        info.put("step", step);
        so.forEach(info.putArray("chain")::add);
        info.set("part", part);
        Decision[] self = new Decision[1];
        Decision.Plan plan = (candidateId, note) -> {
            if (note != null) {
                notes.add(note);
            }
            if (candidateId.equals(TRANSLATE)) {
                return translate(self[0]);
            }
            List<Decision> raised = then.apply(candidateId);
            if (raised.stream().anyMatch(r -> r.reason() == Reason.LIBRARY_CALL && r.library() != null && isMine(r))) {
                self[0].leaveStatementOpen();
            }
            return raised;
        };
        Decision d = new Decision(Reason.LIBRARY_CALL, unit.operation(), message, unit.location(), t.programIdForLibrary(), f,
                ruleId, all, null, plan).withContext(graph).withLibrary(info);
        self[0] = d;
        mine.add(d);
        return d;
    }

    private final List<Decision> mine = new ArrayList<>();

    private boolean isMine(Decision d) {
        return mine.contains(d);
    }

    private String type() {
        return steps.getLast().member().returnType();
    }

    private static String display(String type) {
        return type.substring(type.lastIndexOf('.') + 1);
    }

    private String preview(List<LibraryChain.Step> with) {
        return chain.expression(with, null).toString();
    }

    private String statementPreview(LibraryChain.Out o) {
        return previewStatement(o, false);
    }

    private String previewStatement(LibraryChain.Out o, boolean perProgram) {
        return String.join("\n", build(o, perProgram, false).statements().stream().map(Statement::toString).toList());
    }

    /** {@code keep}: the code kept (adds the per-program field and accessor to the class); else a preview. */
    private Generated build(LibraryChain.Out o, boolean perProgram, boolean keep) {
        Expression instance = perProgram ? t.libraryInstance(chain.library().id(), steps.getFirst().member(), keep) : null;
        try {
            return finisher.generate(steps, o, instance, keep);
        } catch (HandlerException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
