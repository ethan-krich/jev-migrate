package transpiler.cobol2java.translate;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.body.VariableDeclarator;

/** Small JavaParser node builders, so handlers construct ASTs rather than source text. */
public final class Ast {
    private Ast() {}

    public static NameExpr name(String name) {
        return new NameExpr(name);
    }

    /** A class type; {@code Outer.Inner} is built as a scoped type, so import pruning sees {@code Outer}. */
    public static ClassOrInterfaceType type(String simpleName) {
        int dot = simpleName.lastIndexOf('.');
        if (dot > 0) {
            return new ClassOrInterfaceType(type(simpleName.substring(0, dot)), simpleName.substring(dot + 1));
        }
        return new ClassOrInterfaceType(null, simpleName);
    }

    public static MethodCallExpr call(Expression scope, String method, Expression... args) {
        return new MethodCallExpr(scope, method, NodeList.nodeList(args));
    }

    public static MethodCallExpr staticCall(String type, String method, Expression... args) {
        return call(name(type), method, args);
    }

    public static MethodCallExpr thisCall(String method, Expression... args) {
        return new MethodCallExpr(null, method, NodeList.nodeList(args));
    }

    public static Statement stmt(Expression e) {
        return new ExpressionStmt(e);
    }

    public static StringLiteralExpr str(String raw) {
        return new StringLiteralExpr().setString(raw);
    }

    public static CharLiteralExpr chr(char c) {
        return new CharLiteralExpr(c == '\'' ? "\\'" : String.valueOf(c));
    }

    public static IntegerLiteralExpr integer(int value) {
        return new IntegerLiteralExpr(String.valueOf(value));
    }

    public static BooleanLiteralExpr bool(boolean value) {
        return new BooleanLiteralExpr(value);
    }

    public static ThisExpr self() {
        return new ThisExpr();
    }

    public static ObjectCreationExpr create(String type, Expression... args) {
        return new ObjectCreationExpr(null, type(type), NodeList.nodeList(args));
    }

    /** {@code new BigDecimal("text")}, or a BigDecimal constant for 0 and 1. */
    public static Expression decimal(String plain) {
        return switch (plain) {
            case "0" -> new FieldAccessExpr(name("BigDecimal"), "ZERO");
            case "1" -> new FieldAccessExpr(name("BigDecimal"), "ONE");
            default -> create("BigDecimal", str(plain));
        };
    }

    public static Statement declare(Type type, String var, Expression init) {
        return stmt(new VariableDeclarationExpr(new VariableDeclarator(type, var, init)));
    }

    public static Type booleanType() {
        return PrimitiveType.booleanType();
    }

    public static Type intType() {
        return PrimitiveType.intType();
    }

    public static Expression compareToZero(Expression comparison, BinaryExpr.Operator op) {
        return new BinaryExpr(comparison, integer(0), op);
    }

    /**
     * {@code left op right} with the parentheses Java precedence needs (JavaParser prints a tree
     * as is): an operand that binds looser than {@code op} is enclosed, and so is a right operand
     * that binds the same ({@code a - (b - c)}).
     */
    public static Expression binary(Expression left, BinaryExpr.Operator op, Expression right) {
        int p = precedence(op);
        Expression l = needsParens(left, p, false) ? new com.github.javaparser.ast.expr.EnclosedExpr(left) : left;
        Expression r = needsParens(right, p, true) ? new com.github.javaparser.ast.expr.EnclosedExpr(right) : right;
        return new BinaryExpr(l, r, op);
    }

    /** {@code -e}, enclosing an operand that is itself an operation. */
    public static Expression negate(Expression e) {
        boolean wrap = e instanceof BinaryExpr || e instanceof com.github.javaparser.ast.expr.ConditionalExpr
                || e instanceof com.github.javaparser.ast.expr.UnaryExpr;
        return new com.github.javaparser.ast.expr.UnaryExpr(wrap ? new com.github.javaparser.ast.expr.EnclosedExpr(e) : e,
                com.github.javaparser.ast.expr.UnaryExpr.Operator.MINUS);
    }

    private static boolean needsParens(Expression e, int outer, boolean right) {
        if (e instanceof BinaryExpr b) {
            int inner = precedence(b.getOperator());
            return inner < outer || right && inner == outer;
        }
        return e instanceof com.github.javaparser.ast.expr.ConditionalExpr || e instanceof com.github.javaparser.ast.expr.AssignExpr
                || e instanceof com.github.javaparser.ast.expr.LambdaExpr;
    }

    private static int precedence(BinaryExpr.Operator op) {
        return switch (op) {
            case OR -> 1;
            case AND -> 2;
            case BINARY_OR -> 3;
            case XOR -> 4;
            case BINARY_AND -> 5;
            case EQUALS, NOT_EQUALS -> 6;
            case LESS, GREATER, LESS_EQUALS, GREATER_EQUALS -> 7;
            case LEFT_SHIFT, SIGNED_RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT -> 8;
            case PLUS, MINUS -> 9;
            case MULTIPLY, DIVIDE, REMAINDER -> 10;
        };
    }

    /** A Java {@code double} literal for a decimal value ({@code 5.0}, {@code 0.25}). */
    public static Expression doubleLiteral(java.math.BigDecimal value) {
        String text = value.stripTrailingZeros().toPlainString();
        Expression lit = new com.github.javaparser.ast.expr.DoubleLiteralExpr(
                (text.startsWith("-") ? text.substring(1) : text) + (text.contains(".") ? "" : ".0"));
        return value.signum() < 0 ? negate(lit) : lit;
    }
}
