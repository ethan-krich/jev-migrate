package transpiler.cobol2java.translate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import transpiler.cobol2java.library.JavaMember;
import transpiler.cobol2java.library.Libraries;

/**
 * The calls into a library that can stand for one use of a claimed COBOL part: a chain that
 * starts with a static method or a constructor, continues with instance methods of the value so
 * far, and ends when that value can be stored into one of the use's outputs.
 *
 * <p>Rules that keep the search finite and exact:
 * <ul>
 *   <li>arguments come only from the use's inputs, bound through {@link JavaTypes#argument} (exact
 *       pairs only), and every required input is passed exactly once across the chain;</li>
 *   <li>the chain ends only when its value has an exact pair with an output ({@link JavaTypes#result});</li>
 *   <li>at most {@code max_chain} calls; a step is offered only if a complete chain can still be built from it.</li>
 * </ul>
 * A chain follows types the library declares (plus the {@link JavaTypes} table at its ends).
 */
final class LibraryChain {
    /** An input: an operand the COBOL part reads. */
    record In(Operand operand, String label) {}

    /**
     * An output: where the final value goes ({@code receiver} null: a {@code void} call), and
     * the input it may also read ({@code optional}: an in-out item; -1 when none). {@code value}
     * {@code numeric} or {@code text}: the value of an expression (a function's result), stored
     * nowhere, which must be an {@code int}, {@code long} or {@code BigDecimal}, or a {@code String}.
     */
    record Out(String id, Operand receiver, String label, int optional, String value) {
        Out(String id, Operand receiver, String label, int optional) {
            this(id, receiver, label, optional, null);
        }
    }

    /** A call of the chain: the member and the input each parameter takes. */
    record Step(JavaMember member, int[] binding) {
        int mask() {
            int m = 0;
            for (int i : binding) {
                m |= 1 << i;
            }
            return m;
        }

        String bindingId() {
            return String.join(",", Arrays.stream(binding).mapToObj(String::valueOf).toList());
        }
    }

    /** An offered call: a member and the bindings of it that can still complete a chain. */
    record Option(JavaMember member, List<int[]> bindings) {}

    static final int MAX_INPUTS = 6;

    private final Libraries.Library library;
    private final List<In> ins;
    private final List<Out> outs;
    private final TranslationContext c;
    private final ParserRuleContext where;
    private final List<String> hints;
    private final Map<String, Boolean> memo = new HashMap<>();
    private final Map<String, Optional<Expression>> args = new HashMap<>();
    private final Map<String, Boolean> storable = new HashMap<>();

    LibraryChain(Libraries.Library library, List<In> ins, List<Out> outs, List<String> hints, TranslationContext c, ParserRuleContext where) {
        this.library = library;
        this.ins = List.copyOf(ins);
        this.outs = List.copyOf(outs);
        this.hints = List.copyOf(hints);
        this.c = c;
        this.where = where;
    }

    Libraries.Library library() {
        return library;
    }

    List<In> ins() {
        return ins;
    }

    List<Out> outs() {
        return outs;
    }

    /** Whether the use can be searched at all (too many inputs are not permuted). */
    boolean searchable() {
        return ins.size() <= MAX_INPUTS;
    }

    /** The static methods and constructors a complete chain can start with (only the hinted ones when the part has hints). */
    List<Option> starts() {
        List<Option> out = new ArrayList<>();
        if (!searchable()) {
            return out;
        }
        for (JavaMember m : library.index().members()) {
            if (m.kind() == JavaMember.Kind.INSTANCE || !m.usable()) {
                continue;
            }
            if (!hints.isEmpty() && hints.stream().noneMatch(m::matches)) {
                continue;
            }
            List<int[]> ok = new ArrayList<>();
            for (int[] b : bindings(m, 0)) {
                if (outs.stream().anyMatch(o -> feasible(o, m.returnType(), mask(b), 1))) {
                    ok.add(b);
                }
            }
            if (!ok.isEmpty()) {
                out.add(new Option(m, ok));
            }
        }
        return out;
    }

    /** The bindings of a start or next member, given the inputs used so far, that can still complete a chain. */
    List<int[]> bindingsAfter(JavaMember m, int mask, int depth) {
        List<int[]> ok = new ArrayList<>();
        for (int[] b : bindings(m, mask)) {
            if (outs.stream().anyMatch(o -> feasible(o, m.returnType(), mask | mask(b), depth))) {
                ok.add(b);
            }
        }
        return ok;
    }

    /** The instance methods that can continue a chain whose value is a {@code type}, after {@code depth} calls. */
    List<Option> nexts(String type, int mask, int depth) {
        List<Option> out = new ArrayList<>();
        if (depth >= library.maxChain()) {
            return out;
        }
        for (JavaMember m : library.index().of(type)) {
            if (m.kind() != JavaMember.Kind.INSTANCE || !m.usable()) {
                continue;
            }
            List<int[]> ok = bindingsAfter(m, mask, depth + 1);
            if (!ok.isEmpty()) {
                out.add(new Option(m, ok));
            }
        }
        return out;
    }

    /** The outputs a chain ending here (value of {@code type}, inputs {@code mask} used) can store into. */
    List<Out> ends(String type, int mask) {
        return outs.stream().filter(o -> endable(o, type, mask)).toList();
    }

    /** Whether a complete chain exists for output {@code o} from a value of {@code type} after {@code depth} calls. */
    boolean feasible(Out o, String type, int mask, int depth) {
        String key = o.id() + "|" + type + "|" + mask + "|" + depth;
        Boolean known = memo.get(key);
        if (known != null) {
            return known;
        }
        memo.put(key, false); // a type that leads back to itself does not complete through the cycle
        boolean ok = endable(o, type, mask);
        if (!ok && depth < library.maxChain() && !type.equals("void")) {
            search:
            for (JavaMember m : library.index().of(type)) {
                if (m.kind() != JavaMember.Kind.INSTANCE || !m.usable()) {
                    continue;
                }
                for (int[] b : bindings(m, mask)) {
                    if (feasible(o, m.returnType(), mask | mask(b), depth + 1)) {
                        ok = true;
                        break search;
                    }
                }
            }
        }
        memo.put(key, ok);
        return ok;
    }

    private boolean endable(Out o, String type, int mask) {
        int required = (1 << ins.size()) - 1;
        if (o.optional() >= 0) {
            required &= ~(1 << o.optional());
        }
        return (mask & required) == required && storable(type, o);
    }

    private boolean storable(String type, Out o) {
        return storable.computeIfAbsent(type + "|" + o.id(), k -> {
            if (o.value() != null) {
                return o.value().equals("text") ? type.equals("String") : Set.of("int", "long", "BigDecimal").contains(type);
            }
            if (o.receiver() == null) {
                return type.equals("void");
            }
            try {
                return JavaTypes.result(new NameExpr("value"), type, o.receiver(), c).isPresent();
            } catch (HandlerException e) {
                return false;
            }
        });
    }

    /** Every way to bind {@code m}'s parameters to distinct inputs not in {@code mask} (exact pairs only), in lexicographic order. */
    List<int[]> bindings(JavaMember m, int mask) {
        List<int[]> out = new ArrayList<>();
        bind(m, mask, new int[m.params().size()], 0, out);
        return out;
    }

    private void bind(JavaMember m, int mask, int[] at, int k, List<int[]> out) {
        if (k == at.length) {
            out.add(at.clone());
            return;
        }
        for (int i = 0; i < ins.size(); i++) {
            if ((mask & (1 << i)) == 0 && argument(i, m.params().get(k).type()).isPresent()) {
                at[k] = i;
                bind(m, mask | (1 << i), at, k + 1, out);
            }
        }
    }

    private Optional<Expression> argument(int input, String type) {
        return args.computeIfAbsent(input + "|" + type, k -> {
            try {
                return JavaTypes.argument(ins.get(input).operand(), type, c, where);
            } catch (HandlerException e) {
                return Optional.empty();
            }
        }).map(Expression::clone);
    }

    static int mask(int[] binding) {
        int m = 0;
        for (int i : binding) {
            m |= 1 << i;
        }
        return m;
    }

    /** The Java expression of a chain (fully qualified type names; imports are added when the file is written). */
    Expression expression(List<Step> steps, Expression perProgram) {
        Expression value = null;
        for (Step s : steps) {
            NodeList<Expression> a = new NodeList<>();
            for (int k = 0; k < s.binding().length; k++) {
                a.add(argument(s.binding()[k], s.member().params().get(k).type()).orElseThrow());
            }
            JavaMember m = s.member();
            value = switch (m.kind()) {
                case CONSTRUCTOR -> perProgram != null ? perProgram
                        : new ObjectCreationExpr(null, StaticJavaParser.parseClassOrInterfaceType(m.owner()), a);
                case STATIC -> new MethodCallExpr(StaticJavaParser.parseExpression(m.owner()), m.name(), a);
                case INSTANCE -> new MethodCallExpr(value, m.name(), a);
            };
        }
        return value;
    }

    /** The statement running the chain and storing its value into {@code out} under MOVE rules. */
    Statement statement(List<Step> steps, Out out, Expression perProgram) throws HandlerException {
        Expression value = expression(steps, perProgram);
        String type = steps.getLast().member().returnType();
        return JavaTypes.result(value, type, out.receiver(), c)
                .orElseThrow(() -> HandlerException.unsupported("the chain's " + type + " cannot be stored into " + out.label()));
    }

    /** The value of a chain ending in an expression output: a {@code BigDecimal} or a {@code String}. */
    Expression value(List<Step> steps, Expression perProgram) {
        Expression value = expression(steps, perProgram);
        String type = steps.getLast().member().returnType();
        return type.equals("int") || type.equals("long") ? Ast.staticCall("BigDecimal", "valueOf", value) : value;
    }

    /** How a binding passes the inputs: {@code yyyymmdd = WS-DATE, days = WS-DAYS}. */
    String describeBinding(JavaMember m, int[] binding) {
        List<String> parts = new ArrayList<>();
        for (int k = 0; k < binding.length; k++) {
            parts.add(m.params().get(k).name() + " = " + ins.get(binding[k]).label());
        }
        return parts.isEmpty() ? "no arguments" : String.join(", ", parts);
    }

    /** Inputs by label, for messages. */
    Map<String, Integer> inputIndexes() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < ins.size(); i++) {
            out.put(ins.get(i).label(), i);
        }
        return out;
    }
}
