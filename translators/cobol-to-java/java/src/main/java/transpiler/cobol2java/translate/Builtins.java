package transpiler.cobol2java.translate;

import static transpiler.cobol2java.translate.Ast.bool;
import static transpiler.cobol2java.translate.Ast.call;
import static transpiler.cobol2java.translate.Ast.chr;
import static transpiler.cobol2java.translate.Ast.decimal;
import static transpiler.cobol2java.translate.Ast.declare;
import static transpiler.cobol2java.translate.Ast.integer;
import static transpiler.cobol2java.translate.Ast.name;
import static transpiler.cobol2java.translate.Ast.self;
import static transpiler.cobol2java.translate.Ast.staticCall;
import static transpiler.cobol2java.translate.Ast.stmt;
import static transpiler.cobol2java.translate.Ast.str;
import static transpiler.cobol2java.translate.Ast.thisCall;
import static transpiler.cobol2java.translate.Ast.type;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import io.proleap.cobol.asg.metamodel.ASGElement;
import io.proleap.cobol.asg.metamodel.Literal;
import io.proleap.cobol.asg.metamodel.Scope;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.procedure.NotOnSizeErrorPhrase;
import io.proleap.cobol.asg.metamodel.procedure.OnSizeErrorPhrase;
import io.proleap.cobol.asg.metamodel.procedure.add.AddStatement;
import io.proleap.cobol.asg.metamodel.procedure.call.CallStatement;
import io.proleap.cobol.asg.metamodel.procedure.compute.ComputeStatement;
import io.proleap.cobol.asg.metamodel.procedure.display.DisplayStatement;
import io.proleap.cobol.asg.metamodel.procedure.divide.DivideStatement;
import io.proleap.cobol.asg.metamodel.procedure.ifstmt.IfStatement;
import io.proleap.cobol.asg.metamodel.procedure.move.MoveStatement;
import io.proleap.cobol.asg.metamodel.procedure.multiply.MultiplyStatement;
import io.proleap.cobol.asg.metamodel.procedure.perform.PerformStatement;
import io.proleap.cobol.asg.metamodel.procedure.perform.PerformType;
import io.proleap.cobol.asg.metamodel.procedure.perform.TestClause;
import io.proleap.cobol.asg.metamodel.procedure.perform.VaryingPhrase;
import io.proleap.cobol.asg.metamodel.procedure.stop.StopStatement;
import io.proleap.cobol.asg.metamodel.procedure.subtract.SubtractStatement;
import io.proleap.cobol.asg.metamodel.valuestmt.LiteralValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.ValueStmt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import transpiler.cobol2java.mapping.MappingRegistry;

/**
 * The catalog of builtin handlers. Handler ids are a stable public API referenced from Python
 * mapping definitions; removing or renaming one is a breaking change.
 */
public final class Builtins implements MappingRegistry.Catalog {
    private final Map<String, Handler> handlers = new LinkedHashMap<>();

    public Builtins() {
        register(Handler.of("move_to_alphanumeric", "MOVE",
                "MOVE into a PIC X receiver: left-justify, space-pad and right-truncate", Builtins::moveToAlphanumeric));
        register(Handler.of("move_to_numeric", "MOVE",
                "MOVE into a PIC 9 receiver: decimal alignment with high- and low-order truncation", Builtins::moveToNumeric));
        register(Handler.of("add", "ADD", "ADD ... TO / GIVING with exact decimal arithmetic", Builtins::add));
        register(Handler.of("subtract", "SUBTRACT", "SUBTRACT ... FROM / GIVING with exact decimal arithmetic", Builtins::subtract));
        register(Handler.of("multiply", "MULTIPLY", "MULTIPLY ... BY / GIVING with exact decimal arithmetic", Builtins::multiply));
        register(Handler.of("divide", "DIVIDE", "DIVIDE ... INTO / BY / GIVING, truncated or ROUNDED at the receiver scale", Builtins::divide));
        register(Handler.of("compute", "COMPUTE", "COMPUTE with +, -, * and unary minus (exact)", Builtins::compute));
        register(Handler.of("display", "DISPLAY", "DISPLAY to standard output in GnuCOBOL format", Builtins::display));
        register(Handler.of("if", "IF", "IF / ELSE with relation, sign and combined conditions", Builtins::ifElse));
        register(Handler.of("perform", "PERFORM", "PERFORM paragraph/section or inline; TIMES, UNTIL, single VARYING", Builtins::perform));
        register(Handler.of("stop_run", "STOP", "STOP RUN ends the run unit with RETURN-CODE as exit status", Builtins::stopRun));
        register(Handler.of("goback", "GOBACK", "GOBACK returns to the caller, or ends the run unit", (u, c) ->
                List.of(stmt(staticCall("ProgramSupport", "goback", self())))));
        register(Handler.of("exit", "EXIT", "EXIT (no-op) and EXIT PROGRAM", Builtins::exit));
        register(Handler.of("continue", "CONTINUE", "CONTINUE (no-op)", (u, c) -> List.of(noOp("CONTINUE"))));
        register(Handler.of("call_program", "CALL",
                "Static CALL 'literal' of a program in the same translation project, without USING", Builtins::callProgram));
    }

    private void register(Handler h) {
        handlers.put(h.id(), h);
    }

    public Handler handler(String id) {
        return handlers.get(id);
    }

    public List<Handler> handlersFor(String operation) {
        return handlers.values().stream().filter(h -> h.operation().equals(operation)).toList();
    }

    public List<Handler> all() {
        return List.copyOf(handlers.values());
    }

    @Override
    public Set<String> operations() {
        return Facts.operations();
    }

    @Override
    public Set<String> factKeys(String operation) {
        return Facts.keys(operation);
    }

    @Override
    public String handlerOperation(String handler) {
        Handler h = handlers.get(handler);
        return h == null ? null : h.operation();
    }

    // ---------------------------------------------------------------- MOVE

    private static Operand moveSource(OpUnit u, TranslationContext c) throws HandlerException {
        MoveStatement move = (MoveStatement) u.statement();
        if (move.getMoveType() != MoveStatement.MoveType.MOVE_TO) {
            throw HandlerException.unsupported("MOVE CORRESPONDING is not supported");
        }
        Operand src = c.operand(move.getMoveToStatement().getSendingArea().getSendingAreaValueStmt());
        for (Call receiver : move.getMoveToStatement().getReceivingAreaCalls()) {
            if (src instanceof Operand.Field f && c.operand(receiver) instanceof Operand.Field r && r.item() == f.item()
                    && move.getMoveToStatement().getReceivingAreaCalls().size() > 1) {
                throw HandlerException.unsupported("MOVE whose sending item is also one of several receivers");
            }
        }
        return src;
    }

    private static List<Statement> moveToAlphanumeric(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = c.operand(u.receiver());
        if (!(target instanceof Operand.Field t) || !t.item().isAlphanumeric()) {
            throw HandlerException.notApplicable("receiver " + u.receiver().getName() + " is not alphanumeric");
        }
        Expression field = c.field(target);
        Operand src = moveSource(u, c);
        Expression value = switch (src) {
            case Operand.Field f when f.item().isAlphanumeric() -> call(c.field(src), "get");
            case Operand.Field f when f.item().isInteger() -> c.field(src);
            case Operand.Field f when f.item().isNumeric() ->
                throw HandlerException.unsupported("MOVE of non-integer numeric " + f.item().cobolName() + " to alphanumeric is invalid COBOL");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.NumericLiteral n when n.isInteger() -> str(n.unsignedText());
            case Operand.NumericLiteral n -> throw HandlerException.unsupported("MOVE of non-integer literal to alphanumeric is invalid COBOL");
            case Operand.Figurative f -> null;
            default -> throw HandlerException.unsupported("MOVE from " + src.category() + " to alphanumeric is not supported");
        };
        if (src instanceof Operand.Figurative f) {
            return List.of(stmt(call(field, "fill", chr(f.fill()))));
        }
        return List.of(stmt(call(field, "set", value)));
    }

    private static List<Statement> moveToNumeric(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = c.operand(u.receiver());
        boolean numericTarget = target instanceof Operand.ReturnCode
                || target instanceof Operand.Field t && t.item().isNumeric();
        if (!numericTarget) {
            throw HandlerException.notApplicable("receiver " + u.receiver().getName() + " is not numeric");
        }
        Operand src = moveSource(u, c);
        if (!src.isNumericValued() || src instanceof Operand.Arithmetic) {
            throw HandlerException.unsupported("MOVE from " + src.category() + " to numeric is not supported (no de-editing or alphanumeric-to-numeric conversion)");
        }
        Expression value = src instanceof Operand.Field || src instanceof Operand.ReturnCode ? c.field(src) : c.numeric(src);
        return List.of(stmt(call(c.field(target), "move", value)));
    }

    // ---------------------------------------------------------- arithmetic

    /** A numeric receiver with its ROUNDED flag. */
    private record Receiver(Expression field, int scale, boolean rounded) {}

    private static Receiver receiver(Call call, ASGElement phrase, TranslationContext c) throws HandlerException {
        Operand o = c.operand(call);
        int scale;
        if (o instanceof Operand.Field f && f.item().isNumeric()) {
            scale = f.item().numeric().scale();
        } else if (o instanceof Operand.ReturnCode) {
            scale = 0;
        } else {
            throw HandlerException.unsupported("arithmetic receiver " + call.getName() + " is not a numeric item");
        }
        return new Receiver(c.field(o), scale, Facts.hasDirectToken(phrase.getCtx(), "ROUNDED"));
    }

    private static Expression numericOperand(ValueStmt v, TranslationContext c) throws HandlerException {
        Operand o = c.operand(v);
        if (!o.isNumericValued()) {
            throw HandlerException.unsupported("arithmetic operand of category " + o.category() + " is not numeric");
        }
        return c.numeric(o);
    }

    private static Expression numericOperand(Call call, TranslationContext c) throws HandlerException {
        Operand o = c.operand(call);
        if (!o.isNumericValued()) {
            throw HandlerException.unsupported("arithmetic operand " + call.getName() + " is not numeric");
        }
        return c.numeric(o);
    }

    /**
     * Stores {@code value(receiver)} into each receiver, then runs the SIZE ERROR phrases.
     * Operands are evaluated before any receiver is changed, as the standard requires.
     */
    private static List<Statement> storeAll(List<Receiver> receivers, Function<Receiver, Expression> value,
            OnSizeErrorPhrase on, NotOnSizeErrorPhrase notOn, TranslationContext c) {
        List<Statement> out = new ArrayList<>();
        boolean phrase = on != null || notOn != null;
        if (!phrase) {
            for (Receiver r : receivers) {
                out.add(stmt(call(r.field(), "store", value.apply(r), bool(r.rounded()), bool(false))));
            }
            return out;
        }
        String flag = c.fresh("sizeError");
        out.add(declare(Ast.booleanType(), flag, bool(false)));
        for (Receiver r : receivers) {
            out.add(stmt(new AssignExpr(name(flag), call(r.field(), "store", value.apply(r), bool(r.rounded()), bool(true)),
                    AssignExpr.Operator.BINARY_OR)));
        }
        BlockStmt onBlock = on == null ? new BlockStmt() : c.nested(on);
        BlockStmt notBlock = notOn == null ? null : c.nested(notOn);
        out.add(new IfStmt(name(flag), onBlock, notBlock));
        return out;
    }

    /** Evaluates an operand once into a local when it is used for several receivers. */
    private static Expression once(Expression e, int uses, List<Statement> out, TranslationContext c, String base) {
        if (uses <= 1) {
            return e;
        }
        String var = c.fresh(base);
        out.add(declare(type("BigDecimal"), var, e));
        return name(var);
    }

    private static Expression sum(List<Expression> operands) {
        return operands.size() == 1 ? operands.getFirst()
                : staticCall("Arithmetic", "sum", operands.toArray(Expression[]::new));
    }

    private static List<Statement> add(OpUnit u, TranslationContext c) throws HandlerException {
        AddStatement add = (AddStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        switch (add.getAddType()) {
            case TO -> {
                List<Expression> ops = new ArrayList<>();
                for (var from : add.getAddToStatement().getFroms()) {
                    ops.add(numericOperand(from.getFromValueStmt(), c));
                }
                List<Receiver> rs = new ArrayList<>();
                for (var to : add.getAddToStatement().getTos()) {
                    rs.add(receiver(to.getToCall(), to, c));
                }
                Expression addend = once(sum(ops), rs.size(), out, c, "addend");
                out.addAll(storeAll(rs, r -> call(call(r.field(), "get"), "add", addend.clone()),
                        add.getOnSizeErrorPhrase(), add.getNotOnSizeErrorPhrase(), c));
            }
            case TO_GIVING -> {
                List<Expression> ops = new ArrayList<>();
                for (var from : add.getAddToGivingStatement().getFroms()) {
                    ops.add(numericOperand(from.getFromValueStmt(), c));
                }
                for (var to : add.getAddToGivingStatement().getTos()) {
                    ops.add(numericOperand(to.getToValueStmt(), c));
                }
                List<Receiver> rs = new ArrayList<>();
                for (var g : add.getAddToGivingStatement().getGivings()) {
                    rs.add(receiver(g.getGivingCall(), g, c));
                }
                Expression total = once(sum(ops), rs.size(), out, c, "sum");
                out.addAll(storeAll(rs, r -> total.clone(), add.getOnSizeErrorPhrase(), add.getNotOnSizeErrorPhrase(), c));
            }
            case CORRESPONDING -> throw HandlerException.unsupported("ADD CORRESPONDING is not supported");
        }
        return out;
    }

    private static List<Statement> subtract(OpUnit u, TranslationContext c) throws HandlerException {
        SubtractStatement sub = (SubtractStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        switch (sub.getSubtractType()) {
            case FROM -> {
                List<Expression> ops = new ArrayList<>();
                for (var s : sub.getSubtractFromStatement().getSubtrahends()) {
                    ops.add(numericOperand(s.getSubtrahendValueStmt(), c));
                }
                List<Receiver> rs = new ArrayList<>();
                for (var m : sub.getSubtractFromStatement().getMinuends()) {
                    rs.add(receiver(m.getMinuendCall(), m, c));
                }
                Expression subtrahend = once(sum(ops), rs.size(), out, c, "subtrahend");
                out.addAll(storeAll(rs, r -> call(call(r.field(), "get"), "subtract", subtrahend.clone()),
                        sub.getOnSizeErrorPhrase(), sub.getNotOnSizeErrorPhrase(), c));
            }
            case FROM_GIVING -> {
                var giving = sub.getSubtractFromGivingStatement();
                List<Expression> ops = new ArrayList<>();
                for (var s : giving.getSubtrahends()) {
                    ops.add(numericOperand(s.getSubtrahendValueStmt(), c));
                }
                Expression minuend = numericOperand(giving.getMinuend().getMinuendValueStmt(), c);
                List<Receiver> rs = new ArrayList<>();
                for (var g : giving.getGivings()) {
                    rs.add(receiver(g.getGivingCall(), g, c));
                }
                Expression difference = once(call(minuend, "subtract", sum(ops)), rs.size(), out, c, "difference");
                out.addAll(storeAll(rs, r -> difference.clone(), sub.getOnSizeErrorPhrase(), sub.getNotOnSizeErrorPhrase(), c));
            }
            case CORRESPONDING -> throw HandlerException.unsupported("SUBTRACT CORRESPONDING is not supported");
        }
        return out;
    }

    private static List<Statement> multiply(OpUnit u, TranslationContext c) throws HandlerException {
        MultiplyStatement mul = (MultiplyStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        List<Receiver> rs = new ArrayList<>();
        if (mul.getMultiplyType() == MultiplyStatement.MultiplyType.BY) {
            for (var by : mul.getByPhrase().getByOperands()) {
                rs.add(receiver(by.getOperandCall(), by, c));
            }
            Expression factor = once(numericOperand(mul.getOperandValueStmt(), c), rs.size(), out, c, "factor");
            out.addAll(storeAll(rs, r -> call(factor.clone(), "multiply", call(r.field(), "get")),
                    mul.getOnSizeErrorPhrase(), mul.getNotOnSizeErrorPhrase(), c));
        } else {
            var giving = mul.getGivingPhrase();
            Expression left = numericOperand(mul.getOperandValueStmt(), c);
            Expression right = numericOperand(giving.getGivingOperand().getOperandValueStmt(), c);
            for (var res : giving.getGivingResults()) {
                rs.add(receiver(res.getResultCall(), res, c));
            }
            Expression product = once(call(left, "multiply", right), rs.size(), out, c, "product");
            out.addAll(storeAll(rs, r -> product.clone(), mul.getOnSizeErrorPhrase(), mul.getNotOnSizeErrorPhrase(), c));
        }
        return out;
    }

    private static List<Statement> divide(OpUnit u, TranslationContext c) throws HandlerException {
        DivideStatement div = (DivideStatement) u.statement();
        if (div.getRemainder() != null) {
            throw HandlerException.unsupported("DIVIDE ... REMAINDER is not supported");
        }
        List<Statement> out = new ArrayList<>();
        List<Receiver> rs = new ArrayList<>();
        Expression operand = numericOperand(div.getOperandValueStmt(), c);
        switch (div.getDivideType()) {
            case INTO -> {
                for (var into : div.getDivideIntoStatement().getIntos()) {
                    rs.add(receiver(into.getGivingCall(), into, c));
                }
                Expression divisor = once(operand, rs.size(), out, c, "divisor");
                out.addAll(storeAll(rs, r -> staticCall("Arithmetic", "divide", call(r.field(), "get"), divisor.clone(),
                        integer(r.scale()), bool(r.rounded())), div.getOnSizeErrorPhrase(), div.getNotOnSizeErrorPhrase(), c));
            }
            case INTO_GIVING, BY_GIVING -> {
                boolean into = div.getDivideType() == DivideStatement.DivideType.INTO_GIVING;
                var phrase = into ? div.getDivideIntoGivingStatement().getGivingPhrase() : div.getDivideByGivingStatement().getGivingPhrase();
                Expression other = numericOperand(into ? div.getDivideIntoGivingStatement().getIntoValueStmt()
                        : div.getDivideByGivingStatement().getByValueStmt(), c);
                for (var g : phrase.getGivings()) {
                    rs.add(receiver(g.getGivingCall(), g, c));
                }
                Expression dividend = once(into ? other : operand, rs.size(), out, c, "dividend");
                Expression divisor = once(into ? operand : other, rs.size(), out, c, "divisor");
                out.addAll(storeAll(rs, r -> staticCall("Arithmetic", "divide", dividend.clone(), divisor.clone(),
                        integer(r.scale()), bool(r.rounded())), div.getOnSizeErrorPhrase(), div.getNotOnSizeErrorPhrase(), c));
            }
        }
        return out;
    }

    private static List<Statement> compute(OpUnit u, TranslationContext c) throws HandlerException {
        ComputeStatement comp = (ComputeStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        List<Receiver> rs = new ArrayList<>();
        for (var store : comp.getStores()) {
            rs.add(receiver(store.getStoreCall(), store, c));
        }
        Expression value = once(c.numeric(new Operand.Arithmetic(comp.getArithmeticExpression())), rs.size(), out, c, "result");
        out.addAll(storeAll(rs, r -> value.clone(), comp.getOnSizeErrorPhrase(), comp.getNotOnSizeErrorPhrase(), c));
        return out;
    }

    // ------------------------------------------------------------- DISPLAY

    private static List<Statement> display(OpUnit u, TranslationContext c) throws HandlerException {
        DisplayStatement d = (DisplayStatement) u.statement();
        if (d.getUpon() != null || d.getAt() != null || d.getOnExceptionClause() != null || d.getNotOnExceptionClause() != null) {
            throw HandlerException.unsupported("DISPLAY with UPON, AT or exception phrases is not supported");
        }
        NodeList<Expression> args = new NodeList<>();
        for (var operand : d.getOperands()) {
            Operand o = c.operand(operand.getOperandValueStmt());
            args.add(switch (o) {
                case Operand.Field f when f.item().isAlphanumeric() || f.item().isNumeric() -> call(c.field(o), "display");
                case Operand.ReturnCode r -> call(c.field(o), "display");
                case Operand.AlphanumericLiteral a -> str(a.value());
                case Operand.NumericLiteral n -> str(n.displayText());
                default -> throw HandlerException.unsupported("DISPLAY of " + o.category() + " is not supported");
            });
        }
        String method = d.getWith() != null ? "noAdvancing" : "line";
        return List.of(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(name("Display"), method, args)));
    }

    // ------------------------------------------------------- control flow

    private static void rejectNextSentence(Scope scope, String where) throws HandlerException {
        if (scope != null && Facts.hasDirectToken(scope.getCtx(), "NEXT")) {
            throw HandlerException.unsupported("NEXT SENTENCE in " + where + " is not supported");
        }
    }

    private static List<Statement> ifElse(OpUnit u, TranslationContext c) throws HandlerException {
        IfStatement s = (IfStatement) u.statement();
        rejectNextSentence(s.getThen(), "IF");
        rejectNextSentence(s.getElse(), "ELSE");
        Expression cond = c.condition(s.getCondition());
        BlockStmt then = c.nested(s.getThen());
        BlockStmt otherwise = s.getElse() == null ? null : c.nested(s.getElse());
        return List.of(new IfStmt(cond, then, otherwise));
    }

    private static List<Statement> perform(OpUnit u, TranslationContext c) throws HandlerException {
        PerformStatement p = (PerformStatement) u.statement();
        BlockStmt body;
        PerformType type;
        if (p.getPerformStatementType() == PerformStatement.PerformStatementType.INLINE) {
            type = p.getPerformInlineStatement().getPerformType();
            body = c.nested(p.getPerformInlineStatement());
        } else {
            var proc = p.getPerformProcedureStatement();
            List<Call> calls = proc.getCalls();
            if (calls.size() > 1 && !calls.get(0).getName().equalsIgnoreCase(calls.get(calls.size() - 1).getName())) {
                throw HandlerException.unsupported("PERFORM ... THRU a range of procedures is not supported");
            }
            type = proc.getPerformType();
            body = new BlockStmt(NodeList.nodeList(stmt(thisCall(c.procedureMethod(calls.getFirst())))));
        }
        if (type == null) {
            return body.getStatements().size() == 1 && p.getPerformStatementType() == PerformStatement.PerformStatementType.PROCEDURE
                    ? List.of(body.getStatement(0)) : List.of(body);
        }
        return switch (type.getPerformTypeType()) {
            case TIMES -> {
                Operand times = c.operand(type.getTimes().getTimesValueStmt());
                boolean integral = times instanceof Operand.NumericLiteral n && n.isInteger()
                        || times instanceof Operand.Field f && f.item().isInteger();
                if (!integral) {
                    throw HandlerException.unsupported("PERFORM ... TIMES requires an integer literal or integer item");
                }
                String i = c.fresh("i");
                String n = c.fresh("times");
                var init = new NodeList<Expression>(new VariableDeclarationExpr(NodeList.nodeList(
                        new VariableDeclarator(Ast.intType(), i, integer(0)),
                        new VariableDeclarator(Ast.intType(), n, times instanceof Operand.NumericLiteral lit
                                ? integer(lit.value().intValueExact()) : call(c.numeric(times), "intValueExact")))));
                yield List.of(new ForStmt(init, new BinaryExpr(name(i), name(n), BinaryExpr.Operator.LESS),
                        NodeList.nodeList(new UnaryExpr(name(i), UnaryExpr.Operator.POSTFIX_INCREMENT)), body));
            }
            case UNTIL -> {
                Expression until = c.condition(type.getUntil().getCondition());
                yield List.of(testAfter(type.getUntil().getTestClause())
                        ? new DoStmt(body, not(until))
                        : new WhileStmt(not(until), body));
            }
            case VARYING -> {
                var varying = type.getVarying();
                if (!varying.getVaryingClause().getAfters().isEmpty()) {
                    throw HandlerException.unsupported("PERFORM VARYING ... AFTER is not supported");
                }
                VaryingPhrase phrase = varying.getVaryingClause().getVaryingPhrase();
                Operand var = c.operand(phrase.getVaryingValueStmt());
                if (!(var instanceof Operand.Field f && f.item().isNumeric())) {
                    throw HandlerException.unsupported("PERFORM VARYING requires a numeric data item");
                }
                Expression field = c.field(var);
                Expression from = numericOperand(phrase.getFrom().getFromValueStmt(), c);
                Expression by = numericOperand(phrase.getBy().getByValueStmt(), c);
                Expression until = c.condition(phrase.getUntil().getCondition());
                // FROM is stored as if by MOVE; BY augments as if by ADD (no SIZE ERROR phrase).
                Statement init = stmt(call(field, "move", from));
                Statement step = stmt(call(field.clone(), "store", call(call(field.clone(), "get"), "add", by), bool(false), bool(false)));
                // Not a clone: pending decisions hold references to placeholders inside body.
                BlockStmt loop = body;
                if (testAfter(varying.getTestClause())) {
                    loop.addStatement(new IfStmt(until, new BreakStmt(), null));
                    loop.addStatement(step);
                    yield List.of(init, new WhileStmt(bool(true), loop));
                }
                loop.addStatement(step);
                yield List.of(init, new WhileStmt(not(until), loop));
            }
        };
    }

    private static boolean testAfter(TestClause clause) {
        return clause != null && clause.getTestClauseType() == TestClause.TestClauseType.AFTER;
    }

    private static Expression not(Expression e) {
        return new UnaryExpr(new EnclosedExpr(e), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
    }

    private static List<Statement> stopRun(OpUnit u, TranslationContext c) throws HandlerException {
        StopStatement s = (StopStatement) u.statement();
        if (s.getStopType() != StopStatement.StopType.STOP_RUN) {
            throw HandlerException.notApplicable("only STOP RUN is handled; STOP literal / GIVING are not");
        }
        return List.of(stmt(staticCall("ProgramSupport", "stopRun", self())));
    }

    private static List<Statement> exit(OpUnit u, TranslationContext c) {
        if (Facts.hasDirectToken(u.statement().getCtx(), "PROGRAM")) {
            return List.of(stmt(staticCall("ProgramSupport", "exitProgram")));
        }
        return List.of(noOp("EXIT"));
    }

    private static Statement noOp(String verb) {
        EmptyStmt empty = new EmptyStmt();
        empty.setComment(new LineComment(" " + verb + " (no operation)"));
        return empty;
    }

    private static List<Statement> callProgram(OpUnit u, TranslationContext c) throws HandlerException {
        CallStatement call = (CallStatement) u.statement();
        if (call.getUsingPhrase() != null || call.getGivingPhrase() != null || call.getOnExceptionClause() != null
                || call.getNotOnExceptionClause() != null || call.getOnOverflowPhrase() != null) {
            throw HandlerException.unsupported("CALL with USING, RETURNING/GIVING or exception phrases is not supported");
        }
        if (!(call.getProgramValueStmt() instanceof LiteralValueStmt lit)
                || lit.getLiteral().getLiteralType() != Literal.LiteralType.NON_NUMERIC) {
            throw HandlerException.unsupported("dynamic CALL through an identifier is not supported");
        }
        String program = lit.getLiteral().getNonNumericLiteral();
        String cls = c.programClass(program).orElseThrow(() -> new HandlerException(HandlerException.Kind.MISSING_TARGET,
                "program '" + program + "' is not part of the translation project (unresolved cross-file dependency)"));
        return List.of(stmt(staticCall("ProgramSupport", "call", self(), call(name(cls), "instance"))));
    }
}
