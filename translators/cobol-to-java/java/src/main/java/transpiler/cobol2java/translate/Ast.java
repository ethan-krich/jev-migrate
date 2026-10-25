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
}
