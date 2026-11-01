package transpiler.cobol2java.translate;

import static transpiler.cobol2java.translate.Ast.staticCall;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import transpiler.cobol2java.decision.Candidate;

/**
 * Builds candidates that call a static Java method with a statement's operands: one candidate
 * per way of binding the operands to the method's parameters that {@link JavaTypes} passes
 * exactly, each with its generated statement (JavaParser nodes), its preview and the method's
 * docs. A method whose parameters no binding fits, or whose result cannot be stored in the
 * receiver, gives no candidate.
 *
 * <p>Every operand is passed exactly once. Candidate ids are {@code method:Scope.name(Types)},
 * with {@code #i,j,...} (the operand index each parameter takes) when more than one binding fits.
 */
public final class MethodChoice {
    /** Operands beyond this are not permuted (8! bindings); such a call gives no candidates. */
    static final int MAX_OPERANDS = 6;

    /** A candidate and the statements selecting it inserts. */
    public record Built(Candidate candidate, List<Statement> statements) {}

    private MethodChoice() {}

    /**
     * The candidates calling {@code method} with {@code operands} (in any order that binds
     * exactly), storing the result into {@code receiver} (null for a {@code void} method).
     */
    public static List<Built> candidates(JavaMethod method, List<Operand> operands, Operand receiver, TranslationContext c,
            ParserRuleContext where) throws HandlerException {
        int n = method.params().size();
        if (n != operands.size() || n > MAX_OPERANDS || method.params().stream().anyMatch(p -> p.type().endsWith("..."))) {
            return List.of();
        }
        List<int[]> orders = new ArrayList<>();
        permute(new int[n], new boolean[n], 0, orders);
        List<int[]> bound = new ArrayList<>();
        List<Statement> statements = new ArrayList<>();
        for (int[] order : orders) {
            List<Expression> args = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Optional<Expression> arg = JavaTypes.argument(operands.get(order[i]), method.params().get(i).type(), c, where);
                if (arg.isEmpty()) {
                    break;
                }
                args.add(arg.get());
            }
            if (args.size() < n) {
                continue;
            }
            Expression call = staticCall(method.scope(), method.name(), args.toArray(Expression[]::new));
            Optional<Statement> s = JavaTypes.result(call, method.returnType(), receiver, c);
            if (s.isEmpty()) {
                return List.of();
            }
            bound.add(order);
            statements.add(s.get());
        }
        List<Built> out = new ArrayList<>();
        for (int k = 0; k < bound.size(); k++) {
            int[] order = bound.get(k);
            String id = "method:" + method.id() + (bound.size() > 1 ? "#" + String.join(",",
                    java.util.Arrays.stream(order).mapToObj(String::valueOf).toList()) : "");
            Statement s = statements.get(k);
            out.add(new Built(new Candidate(id, "method", describe(method, operands, order, receiver), s.toString(),
                    List.of(), List.of(method.doc())), List.of(s)));
        }
        return out;
    }

    /** What the candidate does: the call and each parameter's operand, then where the result goes. */
    private static String describe(JavaMethod m, List<Operand> operands, int[] order, Operand receiver) {
        List<String> binds = new ArrayList<>();
        for (int i = 0; i < order.length; i++) {
            binds.add(m.params().get(i).name() + " = " + name(operands.get(order[i])));
        }
        String text = "calls " + m.scope() + "." + m.name() + (binds.isEmpty() ? "()" : " with " + String.join(", ", binds));
        return receiver == null ? text : text + "; the result is MOVEd to " + name(receiver);
    }

    private static String name(Operand o) {
        return switch (o) {
            case Operand.Field f -> f.item().cobolName() + (f.refmod() != null ? " (reference-modified)" : "");
            case Operand.AlphanumericLiteral a -> "\"" + a.value() + "\"";
            case Operand.NumericLiteral n -> n.text();
            case Operand.Figurative f -> f.name();
            case Operand.ReturnCode r -> "RETURN-CODE";
            default -> o.category();
        };
    }

    /** Every permutation of 0..n-1, in lexicographic order (the as-written order first). */
    private static void permute(int[] order, boolean[] used, int at, List<int[]> out) {
        if (at == order.length) {
            out.add(order.clone());
            return;
        }
        for (int i = 0; i < order.length; i++) {
            if (!used[i]) {
                used[i] = true;
                order[at] = i;
                permute(order, used, at + 1, out);
                used[i] = false;
            }
        }
    }
}
