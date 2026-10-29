package transpiler.cobol2java.context;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.proleap.cobol.CobolParser;
import io.proleap.cobol.asg.metamodel.ASGElement;
import io.proleap.cobol.asg.metamodel.Program;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.call.DataDescriptionEntryCall;
import io.proleap.cobol.asg.metamodel.call.ProcedureCall;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntry;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntryGroup;
import io.proleap.cobol.asg.metamodel.procedure.Paragraph;
import io.proleap.cobol.asg.metamodel.procedure.Statement;
import io.proleap.cobol.asg.metamodel.procedure.StatementTypeEnum;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import transpiler.cobol2java.cobol.SourceMap;
import transpiler.cobol2java.protocol.Json;

/**
 * The source around a decision, as a graph over ProLeap's ASG for a provider to read.
 *
 * <p>Nodes are data description entries, statements and paragraphs. Depth 0 is the decision's
 * operands. A data item leads to its parent group, its REDEFINES target, its condition-names
 * (VALUE) and the statements that write it, read it or pass it on (WRITE, CALL, DISPLAY: kind
 * {@code usage}); a statement to its paragraph and its operands; a paragraph to the statements
 * that PERFORM it. The traversal is breadth-first in source order, so the same source always
 * gives the same nodes. Refs are derived from source positions and stay stable for the session.
 *
 * <p>This is advisory context only: it never influences what is generated or the fingerprint.
 */
public final class ContextGraph {
    /** The budget of the rendered context. */
    public record Budget(int maxDepth, int maxChars) {
        public static final Budget DEFAULT = new Budget(2, 4000);
    }

    /** Statement neighbours listed per data item or paragraph; the rest are counted as omitted. */
    static final int MAX_STATEMENTS = 12;
    /** Nodes listed per rendering; the rest are counted as omitted. */
    static final int MAX_NODES = 60;
    static final int SUMMARY_LENGTH = 120;

    /** Grammar rules whose identifier receives a value (the rest of a statement's operands are read). */
    private static final Set<Class<?>> RECEIVING = Set.of(CobolParser.AddToContext.class, CobolParser.AddGivingContext.class,
            CobolParser.ComputeStoreContext.class, CobolParser.DivideIntoContext.class, CobolParser.DivideGivingContext.class,
            CobolParser.DivideRemainderContext.class, CobolParser.MultiplyRegularOperandContext.class,
            CobolParser.MultiplyGivingResultContext.class, CobolParser.SubtractMinuendContext.class,
            CobolParser.SubtractGivingContext.class, CobolParser.SubtractMinuendCorrespondingContext.class,
            CobolParser.ReadIntoContext.class, CobolParser.ReturnIntoContext.class, CobolParser.SearchVaryingContext.class,
            CobolParser.SetToContext.class, CobolParser.StringIntoPhraseContext.class, CobolParser.StringWithPointerPhraseContext.class,
            CobolParser.UnstringIntoContext.class, CobolParser.UnstringDelimiterInContext.class, CobolParser.UnstringCountInContext.class,
            CobolParser.UnstringWithPointerPhraseContext.class, CobolParser.UnstringTallyingPhraseContext.class,
            CobolParser.InspectForContext.class);
    /** Rules whose identifiers (directly below the rule) receive values. */
    private static final Set<Class<?>> RECEIVING_STATEMENTS = Set.of(CobolParser.MoveToStatementContext.class,
            CobolParser.MoveCorrespondingToStatementContext.class, CobolParser.InitializeStatementContext.class,
            CobolParser.AcceptStatementContext.class, CobolParser.PerformVaryingPhraseContext.class);
    /** Parts of an identifier that are read even when the identifier receives (subscripts, reference modification). */
    private static final Set<Class<?>> READ_PARTS = Set.of(CobolParser.SubscriptContext.class,
            CobolParser.ReferenceModifierContext.class, CobolParser.ArgumentContext.class, CobolParser.FunctionCallContext.class);
    private static final Set<StatementTypeEnum> USAGE = Set.of(StatementTypeEnum.WRITE, StatementTypeEnum.CALL,
            StatementTypeEnum.DISPLAY);

    /** A node's position in the graph: how it was first reached. */
    private record Placed(Object entity, String kind, int depth, String parent) {}

    private record Edge(String kind, Object entity) {}

    private final Program program;
    private final SourceMap sourceMap;
    private final String file;
    private final List<Object> roots;
    private final ParserRuleContext origin;
    /** Every ref this graph has listed, so expand_context can find it again. */
    private final Map<String, Placed> known = new HashMap<>();

    private ContextGraph(Program program, SourceMap sourceMap, String file, List<Object> roots, ParserRuleContext origin) {
        this.program = program;
        this.sourceMap = sourceMap;
        this.file = file;
        this.roots = roots;
        this.origin = origin;
    }

    /**
     * The graph around the source at {@code ctx}: a statement (its operands are the roots), a data
     * description entry, or any other element (its data operands).
     */
    public static ContextGraph of(ParserRuleContext ctx, Program program, SourceMap sourceMap, String file) {
        List<Object> roots = new ArrayList<>();
        ASGElement element = program.getASGElementRegistry().getASGElement(ctx);
        if (element instanceof DataDescriptionEntry entry) {
            roots.add(entry);
        } else {
            roots.addAll(operands(ctx, program));
        }
        return new ContextGraph(program, sourceMap, file, roots, ctx);
    }

    /** {@code {refs, budget}}: nodes within the budget, then the next level as truncated summaries. */
    public ObjectNode render(Budget budget) {
        ObjectNode out = Json.object();
        ArrayNode refs = out.putArray("refs");
        int used = 0;
        int truncated = 0;
        int omitted = 0;
        Map<String, Placed> order = traverse(budget.maxDepth() + 1);
        for (Placed p : order.values()) {
            if (refs.size() >= MAX_NODES) {
                if (p.depth() <= budget.maxDepth()) {
                    omitted++;
                }
                continue;
            }
            String text = text(p.entity());
            boolean full = p.depth() <= budget.maxDepth() && used + text.length() <= budget.maxChars();
            if (full) {
                used += text.length();
            } else {
                truncated++;
            }
            refs.add(node(p, full ? text : null));
        }
        omitted += order.values().stream().filter(p -> p.depth() <= budget.maxDepth()).mapToInt(p -> overflow(p.entity())).sum();
        ObjectNode b = out.putObject("budget");
        b.put("max_depth", budget.maxDepth());
        b.put("max_chars", budget.maxChars());
        b.put("used_chars", used);
        b.put("truncated", truncated);
        b.put("omitted", omitted);
        return out;
    }

    /** Whether {@code ref} was listed by this graph. */
    public boolean knows(String ref) {
        return known.containsKey(ref);
    }

    /**
     * The text of listed nodes (within {@code maxChars}; the first is always given in full), then
     * their neighbours one level deeper as truncated summaries. The caller has checked {@link #knows}.
     */
    public List<ObjectNode> expand(List<String> refs, int maxChars) {
        List<ObjectNode> out = new ArrayList<>();
        Map<String, Placed> deeper = new LinkedHashMap<>();
        int used = 0;
        for (String ref : refs) {
            Placed p = known.get(ref);
            String text = text(p.entity());
            boolean full = out.isEmpty() || used + text.length() <= maxChars;
            if (full) {
                used += text.length();
            }
            out.add(node(p, full ? text : null));
            for (Edge e : edges(p.entity())) {
                String r = ref(e.entity());
                if (!refs.contains(r) && !deeper.containsKey(r)) {
                    deeper.put(r, new Placed(e.entity(), e.kind(), p.depth() + 1, ref));
                }
            }
        }
        for (Placed p : deeper.values()) {
            known.putIfAbsent(ref(p.entity()), p);
            out.add(node(known.get(ref(p.entity())), null));
        }
        return out;
    }

    /** Breadth-first from the roots (depth 0) and the origin's paragraph (depth 1), down to {@code depth}. */
    private Map<String, Placed> traverse(int depth) {
        Map<String, Placed> seen = new LinkedHashMap<>();
        ArrayDeque<Placed> queue = new ArrayDeque<>();
        for (Object r : roots) {
            queue.add(new Placed(r, "data", 0, null));
        }
        Paragraph paragraph = paragraphOf(program.getASGElementRegistry().getASGElement(origin));
        if (paragraph != null) {
            queue.add(new Placed(paragraph, "paragraph", 1, null));
        }
        while (!queue.isEmpty()) {
            Placed p = queue.poll();
            String ref = ref(p.entity());
            if (seen.containsKey(ref) || p.depth() > depth) {
                continue;
            }
            seen.put(ref, p);
            known.putIfAbsent(ref, p);
            if (p.depth() < depth) {
                for (Edge e : edges(p.entity())) {
                    queue.add(new Placed(e.entity(), e.kind(), p.depth() + 1, ref));
                }
            }
        }
        List<Placed> sorted = new ArrayList<>(seen.values());
        sorted.sort(Comparator.comparingInt(Placed::depth));
        Map<String, Placed> out = new LinkedHashMap<>();
        sorted.forEach(p -> out.put(ref(p.entity()), p));
        return out;
    }

    private List<Edge> edges(Object entity) {
        List<Edge> out = new ArrayList<>();
        switch (entity) {
            case DataDescriptionEntry entry -> {
                if (entry.getParentDataDescriptionEntryGroup() != null) {
                    out.add(new Edge("data", entry.getParentDataDescriptionEntryGroup()));
                }
                if (entry instanceof DataDescriptionEntryGroup g && g.getRedefinesClause() != null
                        && g.getRedefinesClause().getRedefinesCall() != null
                        && g.getRedefinesClause().getRedefinesCall().unwrap() instanceof DataDescriptionEntryCall c
                        && c.getDataDescriptionEntry() != null) {
                    out.add(new Edge("data", c.getDataDescriptionEntry()));
                }
                if (entry instanceof DataDescriptionEntryGroup g) {
                    for (DataDescriptionEntry child : g.getDataDescriptionEntries()) {
                        if (child.getDataDescriptionEntryType() == DataDescriptionEntry.DataDescriptionEntryType.CONDITION) {
                            out.add(new Edge("data", child));
                        }
                    }
                }
                List<Edge> statements = new ArrayList<>();
                for (Statement s : statementsUsing(entry)) {
                    statements.add(new Edge(role(entry, s), s));
                }
                out.addAll(statements.subList(0, Math.min(MAX_STATEMENTS, statements.size())));
            }
            case Statement s -> {
                Paragraph paragraph = paragraphOf(s);
                if (paragraph != null) {
                    out.add(new Edge("paragraph", paragraph));
                }
                for (DataDescriptionEntry e : operands(s.getCtx(), program)) {
                    out.add(new Edge("data", e));
                }
            }
            case Paragraph p -> {
                List<Statement> performs = new ArrayList<>();
                for (ProcedureCall call : p.getCalls()) {
                    Statement s = statementOf(call);
                    if (s != null && s.getStatementType() == StatementTypeEnum.PERFORM && !performs.contains(s)) {
                        performs.add(s);
                    }
                }
                performs.sort(Comparator.comparingInt(s -> s.getCtx().getStart().getTokenIndex()));
                performs.stream().limit(MAX_STATEMENTS).forEach(s -> out.add(new Edge("usage", s)));
            }
            default -> throw new IllegalStateException("unexpected context entity " + entity);
        }
        return out;
    }

    /** Statement neighbours an entity has beyond {@link #MAX_STATEMENTS}. */
    private int overflow(Object entity) {
        int n = switch (entity) {
            case DataDescriptionEntry entry -> statementsUsing(entry).size();
            case Paragraph p -> (int) p.getCalls().stream().map(ContextGraph::statementOf)
                    .filter(s -> s != null && s.getStatementType() == StatementTypeEnum.PERFORM).distinct().count();
            default -> 0;
        };
        return Math.max(0, n - MAX_STATEMENTS);
    }

    private static List<Statement> statementsUsing(DataDescriptionEntry entry) {
        List<Statement> out = new ArrayList<>();
        for (DataDescriptionEntryCall call : entry.getCalls()) {
            Statement s = statementOf(call);
            if (s != null && !out.contains(s)) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparingInt(s -> s.getCtx().getStart().getTokenIndex()));
        return out;
    }

    /** {@code writes}, {@code usage} (read by WRITE / CALL / DISPLAY) or {@code reads}. */
    private static String role(DataDescriptionEntry entry, Statement s) {
        for (DataDescriptionEntryCall call : entry.getCalls()) {
            if (statementOf(call) == s && receives(call.getCtx(), s.getCtx())) {
                return "writes";
            }
        }
        return USAGE.contains(s.getStatementType()) ? "usage" : "reads";
    }

    /** Whether the identifier at {@code ctx} receives a value in the statement (from the grammar rule it sits in). */
    private static boolean receives(ParserRuleContext ctx, ParserRuleContext statement) {
        for (ParserRuleContext p = ctx; p != null && p != statement; p = p.getParent()) {
            if (READ_PARTS.contains(p.getClass())) {
                return false;
            }
            if (RECEIVING.contains(p.getClass())) {
                return true;
            }
            ParserRuleContext parent = p.getParent();
            if (p instanceof CobolParser.IdentifierContext && parent != null) {
                if (RECEIVING_STATEMENTS.contains(parent.getClass())) {
                    return true;
                }
                if (parent instanceof CobolParser.InspectStatementContext i && i.inspectTallyingPhrase() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The data items a piece of source names, in source order. */
    private static List<DataDescriptionEntry> operands(ParserRuleContext ctx, Program program) {
        List<DataDescriptionEntry> out = new ArrayList<>();
        ArrayDeque<ParseTree> stack = new ArrayDeque<>();
        stack.push(ctx);
        List<ParseTree> ordered = new ArrayList<>();
        while (!stack.isEmpty()) {
            ParseTree t = stack.pop();
            ordered.add(t);
            for (int i = t.getChildCount() - 1; i >= 0; i--) {
                stack.push(t.getChild(i));
            }
        }
        for (ParseTree t : ordered) {
            if (t instanceof ParserRuleContext rc && program.getASGElementRegistry().getASGElement(rc) instanceof Call call
                    && call.unwrap() instanceof DataDescriptionEntryCall dc && dc.getDataDescriptionEntry() != null
                    && !out.contains(dc.getDataDescriptionEntry())) {
                out.add(dc.getDataDescriptionEntry());
            }
        }
        return out;
    }

    private static Statement statementOf(ASGElement element) {
        for (ASGElement e = element; e != null; e = e.getParent()) {
            if (e instanceof Statement s) {
                return s;
            }
        }
        return null;
    }

    private static Paragraph paragraphOf(ASGElement element) {
        for (ASGElement e = element; e != null; e = e.getParent()) {
            if (e instanceof Paragraph p) {
                return p;
            }
        }
        return null;
    }

    private ObjectNode node(Placed p, String text) {
        Object entity = p.entity();
        ParserRuleContext ctx = ctx(entity);
        int line = ctx.getStart().getLine();
        ObjectNode n = Json.object();
        n.put("ref", ref(entity));
        n.put("kind", p.kind());
        n.put("depth", p.depth());
        if (text != null) {
            n.put("text", text);
        } else {
            n.put("truncated", true);
        }
        n.put("summary", summary(entity, line));
        n.put("file", file);
        n.put("line", sourceMap.originalLine(line));
        if (p.parent() != null) {
            n.put("parent", p.parent());
        }
        if (entity instanceof DataDescriptionEntryGroup g && !g.getDataDescriptionEntries().isEmpty()) {
            ArrayNode items = n.putArray("items");
            g.getDataDescriptionEntries().forEach(c -> items.add(c.getName() == null ? "FILLER" : c.getName()));
        }
        return n;
    }

    private String summary(Object entity, int line) {
        String first = text(entity).strip().replaceAll("\\s+", " ");
        String s = switch (entity) {
            case Paragraph p -> "paragraph " + p.getName() + ", lines " + sourceMap.originalLine(line) + "-"
                    + sourceMap.originalLine(p.getCtx().getStop().getLine());
            default -> first;
        };
        if (s.length() > SUMMARY_LENGTH) {
            s = s.substring(0, SUMMARY_LENGTH - 3) + "...";
        }
        var copy = sourceMap.copyLine(line);
        return copy.isPresent() ? s + " (from the COPY at line " + copy.getAsInt() + ")" : s;
    }

    private static String ref(Object entity) {
        ParserRuleContext ctx = ctx(entity);
        String at = ctx.getStart().getLine() + ":" + (ctx.getStart().getCharPositionInLine() + 1);
        return switch (entity) {
            case DataDescriptionEntry e -> "data:" + (e.getName() == null ? "FILLER" : e.getName()) + "@" + at;
            case Paragraph p -> "para:" + p.getName() + "@" + at;
            default -> "stmt:" + at;
        };
    }

    private static ParserRuleContext ctx(Object entity) {
        return ((ASGElement) entity).getCtx();
    }

    private static String text(Object entity) {
        ParserRuleContext ctx = ctx(entity);
        var start = ctx.getStart();
        var stop = ctx.getStop() == null ? start : ctx.getStop();
        return start.getInputStream().getText(Interval.of(start.getStartIndex(), stop.getStopIndex())).strip();
    }
}
