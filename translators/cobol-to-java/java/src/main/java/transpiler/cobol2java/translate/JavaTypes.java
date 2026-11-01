package transpiler.cobol2java.translate;

import static transpiler.cobol2java.translate.Ast.call;
import static transpiler.cobol2java.translate.Ast.staticCall;
import static transpiler.cobol2java.translate.Ast.stmt;
import static transpiler.cobol2java.translate.Ast.str;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.Statement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Which COBOL data passes exactly as which Java type, in both directions. A pair that is not
 * here has no binding, so {@link MethodChoice} offers no candidate for it: a candidate never
 * converts approximately.
 *
 * <table>
 *   <caption>Arguments (COBOL operand to Java parameter)</caption>
 *   <tr><th>Java</th><th>COBOL</th></tr>
 *   <tr><td>{@code String}</td><td>alphanumeric item, group or reference modification (its bytes as
 *       characters, padding included); alphanumeric literal</td></tr>
 *   <tr><td>{@code int}</td><td>integer item of up to 9 digits without P scaling; integer literal in range</td></tr>
 *   <tr><td>{@code long}</td><td>integer item of up to 18 digits without P scaling; integer literal in range</td></tr>
 *   <tr><td>{@code BigDecimal}</td><td>any numeric item, numeric literal, ZERO, RETURN-CODE</td></tr>
 *   <tr><td>{@code boolean}</td><td>level-88 condition-name (whether it is true)</td></tr>
 * </table>
 *
 * <p>Results are stored into the receiver as MOVE stores them: a {@code String} into an
 * alphanumeric item (padded or cut, JUSTIFIED honoured), {@code int}, {@code long} and
 * {@code BigDecimal} into a numeric item (decimal alignment, truncation of high-order digits). A
 * {@code void} method takes no receiver; a {@code boolean} result has none (SET condition-name
 * TO FALSE is not a MOVE).
 */
public final class JavaTypes {
    private JavaTypes() {}

    /** The Java types of the table. */
    public static final List<String> TYPES = List.of("String", "int", "long", "BigDecimal", "boolean");

    /** The expression passing {@code o} as a {@code type} argument, or empty when no exact binding exists. */
    public static Optional<Expression> argument(Operand o, String type, TranslationContext c, ParserRuleContext where)
            throws HandlerException {
        return switch (type) {
            case "String" -> switch (o) {
                case Operand.Field f when f.alphanumeric() && !f.item().isCondition() -> Optional.of(call(c.field(o), "get"));
                case Operand.AlphanumericLiteral a -> Optional.of(str(a.value()));
                default -> Optional.empty();
            };
            case "int" -> switch (o) {
                case Operand.Field f when integerDigits(f) > 0 && integerDigits(f) <= 9 -> Optional.of(call(c.field(o), "intValue"));
                case Operand.NumericLiteral n when n.isInteger() && fits(n.value(), Integer.MIN_VALUE, Integer.MAX_VALUE) ->
                        Optional.of(literal(n.value(), false));
                default -> Optional.empty();
            };
            case "long" -> switch (o) {
                case Operand.Field f when integerDigits(f) > 0 && integerDigits(f) <= 18 ->
                        Optional.of(call(c.numeric(o), "longValueExact"));
                case Operand.NumericLiteral n when n.isInteger() && fits(n.value(), Long.MIN_VALUE, Long.MAX_VALUE) ->
                        Optional.of(literal(n.value(), true));
                default -> Optional.empty();
            };
            case "BigDecimal" -> o.isNumericValued() && !(o instanceof Operand.Arithmetic) && !(o instanceof Operand.Computed)
                    ? Optional.of(c.numeric(o)) : Optional.empty();
            case "boolean" -> o instanceof Operand.Field f && f.item().isCondition() && f.refmod() == null
                    ? Optional.of(c.conditionName(f, where)) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * The statement running {@code value} (a call returning {@code type}) and storing its result
     * into {@code receiver} under MOVE rules; {@code receiver} null for a {@code void} call.
     * Empty when the result cannot be stored exactly.
     */
    public static Optional<Statement> result(Expression value, String type, Operand receiver, TranslationContext c)
            throws HandlerException {
        if (receiver == null) {
            return type.equals("void") ? Optional.of(stmt(value)) : Optional.empty();
        }
        return switch (type) {
            case "String" -> receiver instanceof Operand.Field f && f.alphanumeric() && !f.edited() && !f.item().isCondition()
                    && (f.refmod() != null || !f.item().isGroup())
                    ? Optional.of(Builtins.storeAlphanumeric(c.receiving(receiver), new Operand.Computed(value, false, null), c))
                    : Optional.empty();
            case "int", "long", "BigDecimal" -> receiver instanceof Operand.ReturnCode || receiver instanceof Operand.Field f && f.numeric()
                    ? Optional.of(Builtins.storeNumeric(c.field(receiver), new Operand.Computed(
                            type.equals("BigDecimal") ? value : staticCall("BigDecimal", "valueOf", value), true, null), c))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** The digits of an integer item without P scaling, else 0. */
    private static int integerDigits(Operand.Field f) {
        return f.integer() && f.item().numeric().scale() == 0 ? f.item().numeric().digits() : 0;
    }

    private static boolean fits(BigDecimal v, long min, long max) {
        return v.compareTo(BigDecimal.valueOf(min)) >= 0 && v.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    private static Expression literal(BigDecimal v, boolean asLong) {
        String digits = v.abs().toBigIntegerExact().toString();
        Expression lit = asLong ? new LongLiteralExpr(digits + "L") : new IntegerLiteralExpr(digits);
        return v.signum() < 0 ? new UnaryExpr(lit, UnaryExpr.Operator.MINUS) : lit;
    }
}
