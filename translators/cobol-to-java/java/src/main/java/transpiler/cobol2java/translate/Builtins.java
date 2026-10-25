package transpiler.cobol2java.translate;

import static transpiler.cobol2java.translate.Ast.bool;
import static transpiler.cobol2java.translate.Ast.call;
import static transpiler.cobol2java.translate.Ast.chr;
import static transpiler.cobol2java.translate.Ast.create;
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
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import io.proleap.cobol.asg.metamodel.ASGElement;
import io.proleap.cobol.asg.metamodel.Literal;
import io.proleap.cobol.asg.metamodel.Scope;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.procedure.NotOnSizeErrorPhrase;
import io.proleap.cobol.asg.metamodel.procedure.OnSizeErrorPhrase;
import io.proleap.cobol.asg.metamodel.procedure.accept.AcceptStatement;
import io.proleap.cobol.asg.metamodel.procedure.add.AddStatement;
import io.proleap.cobol.asg.metamodel.procedure.call.CallStatement;
import io.proleap.cobol.asg.metamodel.procedure.compute.ComputeStatement;
import io.proleap.cobol.asg.metamodel.procedure.display.DisplayStatement;
import io.proleap.cobol.asg.metamodel.procedure.divide.DivideStatement;
import io.proleap.cobol.asg.metamodel.procedure.evaluate.EvaluateStatement;
import io.proleap.cobol.asg.metamodel.procedure.ifstmt.IfStatement;
import io.proleap.cobol.asg.metamodel.procedure.move.MoveStatement;
import io.proleap.cobol.asg.metamodel.procedure.multiply.MultiplyStatement;
import io.proleap.cobol.asg.metamodel.procedure.perform.PerformStatement;
import io.proleap.cobol.asg.metamodel.procedure.perform.PerformType;
import io.proleap.cobol.asg.metamodel.procedure.perform.TestClause;
import io.proleap.cobol.asg.metamodel.procedure.perform.VaryingPhrase;
import io.proleap.cobol.asg.metamodel.procedure.set.SetStatement;
import io.proleap.cobol.asg.metamodel.procedure.stop.StopStatement;
import io.proleap.cobol.asg.metamodel.procedure.subtract.SubtractStatement;
import io.proleap.cobol.asg.metamodel.valuestmt.ConditionValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.LiteralValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.ValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.RelationalOperator.RelationalOperatorType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
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
        register(Handler.of("set_condition", "SET",
                "SET condition-name TO TRUE (moves its first value), or TO FALSE with WHEN SET TO FALSE", Builtins::setCondition));
        register(Handler.of("evaluate", "EVALUATE",
                "EVALUATE with ALSO, THRU, ANY, TRUE/FALSE and OTHER as an if/else chain", Builtins::evaluate));
        register(Handler.of("accept_stdin", "ACCEPT",
                "ACCEPT an alphanumeric item from standard input (one line; spaces at end of input)", Builtins::acceptStdin));
        register(Handler.of("accept_datetime", "ACCEPT",
                "ACCEPT FROM DATE [YYYYMMDD] / DAY [YYYYDDD] / DAY-OF-WEEK / TIME via the runtime Clock", Builtins::acceptDatetime));
        register(Handler.of("exhibit", "EXHIBIT", "EXHIBIT [NAMED] as GnuCOBOL: NAME = value for items", Builtins::exhibit));
        register(Handler.of("move_to_edited", "MOVE",
                "MOVE into a numeric-edited (PICTURE editing) or alphanumeric-edited (insertion) item", Builtins::moveToEdited));
        register(Handler.of("move_from_edited", "MOVE",
                "MOVE of a numeric-edited item to a numeric item: the value is de-edited", Builtins::moveFromEdited));
        register(Handler.of("string", "STRING",
                "STRING ... DELIMITED BY ... INTO [WITH POINTER] [ON OVERFLOW]", Builtins::stringStatement));
        register(Handler.of("unstring", "UNSTRING",
                "UNSTRING [DELIMITED BY [ALL] ... OR ...] INTO alphanumeric items [DELIMITER IN] [COUNT IN] [POINTER] [TALLYING]",
                Builtins::unstring));
        register(Handler.of("inspect", "INSPECT",
                "INSPECT TALLYING / REPLACING / CONVERTING with BEFORE / AFTER INITIAL", Builtins::inspect));
        register(Handler.of("move_group", "MOVE",
                "MOVE into a group item: the sending bytes as alphanumeric data, space-padded or cut", Builtins::moveToGroup));
        register(Handler.of("initialize", "INITIALIZE",
                "INITIALIZE [WITH FILLER] [ALL TO VALUE] [REPLACING ...] [THEN TO DEFAULT] item by item", Builtins::initialize));
        register(Handler.of("set_index", "SET", "SET index TO / UP BY / DOWN BY, SET item TO index", Builtins::setIndex));
        register(Handler.of("search", "SEARCH", "Serial SEARCH [VARYING] with AT END and WHEN", Builtins::search));
        register(Handler.of("search_all", "SEARCH",
                "SEARCH ALL where the table's keys are proven ascending/descending and unique", Builtins::searchAll));
        register(Handler.of("search_all_binary", "SEARCH",
                "SEARCH ALL as a binary search on the KEY (correct only if the keys are in order)", Builtins::searchAllBinary));
        register(Handler.of("search_all_linear", "SEARCH",
                "SEARCH ALL as a serial scan from the first occurrence (correct only if the keys are unique)", Builtins::searchAllLinear));
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

    /** The receiver of a MOVE unit: its receiving call, or the target of a CORRESPONDING pair. */
    private static Operand moveTarget(OpUnit u, TranslationContext c) throws HandlerException {
        if (u.corresponding() != null) {
            if (u.corresponding().problem() != null) {
                throw HandlerException.unsupported(u.corresponding().problem());
            }
            return new Operand.Field(u.corresponding().target());
        }
        return c.operand(u.receiver());
    }

    private static Operand moveSource(OpUnit u, TranslationContext c) throws HandlerException {
        MoveStatement move = (MoveStatement) u.statement();
        if (u.corresponding() != null) {
            return new Operand.Field(u.corresponding().source());
        }
        Operand src = moveAll(move, c.operand(move.getMoveToStatement().getSendingArea().getSendingAreaValueStmt()));
        if (move.getMoveToStatement().getReceivingAreaCalls().size() > 1) {
            // Each receiver is a separate store: a receiver that shares bytes with the sender would change it for the next.
            for (Call receiver : move.getMoveToStatement().getReceivingAreaCalls()) {
                if (c.overlaps(src, c.operand(receiver))) {
                    throw HandlerException.unsupported("MOVE to several receivers where one shares storage with the sending item");
                }
            }
        }
        return src;
    }

    /** {@code MOVE ALL "lit"}: ProLeap's grammar puts ALL on the MOVE statement, not on the literal. */
    static Operand moveAll(MoveStatement move, Operand src) throws HandlerException {
        if (((io.proleap.cobol.CobolParser.MoveStatementContext) move.getCtx()).ALL() == null) {
            return src;
        }
        return switch (src) {
            case Operand.AlphanumericLiteral a when !a.value().isEmpty() -> new Operand.Figurative(a.value(), "ALL");
            case Operand.Figurative f -> f;
            default -> throw HandlerException.unsupported("MOVE ALL with " + src.category() + " is not supported");
        };
    }

    private static List<Statement> moveToAlphanumeric(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = moveTarget(u, c);
        if (!(target instanceof Operand.Field t) || !t.alphanumeric() || (t.refmod() == null && (t.item().isGroup() || t.item().isEdited()))) {
            throw HandlerException.notApplicable("receiver " + receiverName(u, target) + " is not alphanumeric");
        }
        return List.of(storeAlphanumeric(c.field(target), moveSource(u, c), c));
    }

    private static List<Statement> moveToEdited(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = moveTarget(u, c);
        if (!(target instanceof Operand.Field t) || !t.edited()) {
            throw HandlerException.notApplicable("receiver " + receiverName(u, target) + " is not an edited item");
        }
        return List.of(storeEdited(c.field(target), t.item(), moveSource(u, c), c));
    }

    /** MOVE of {@code src} into an edited item (shared by MOVE and INITIALIZE). */
    static Statement storeEdited(Expression field, DataItem target, Operand src, TranslationContext c) throws HandlerException {
        if (target.edited().numeric()) {
            return switch (src) {
                case Operand.Field f when f.edited() && f.item().edited().numeric() -> stmt(call(field, "move", call(c.field(src), "value")));
                case Operand.Field f when f.numeric() -> stmt(call(field, "move", c.field(src)));
                case Operand.ReturnCode r -> stmt(call(field, "move", c.field(src)));
                case Operand.NumericLiteral n -> stmt(call(field, "move", c.numeric(src)));
                case Operand.Figurative f when f.isZero() -> stmt(call(field, "fill", chr('0')));
                default -> throw HandlerException.unsupported("MOVE from " + src.category() + " to numeric-edited is not supported");
            };
        }
        return switch (src) {
            case Operand.Figurative f when f.isSingle() -> stmt(call(field, "fill", chr(f.fill())));
            case Operand.Field f when f.alphanumeric() -> stmt(call(field, "set", call(c.field(src), "get")));
            case Operand.Field f when f.integer() -> stmt(call(field, "set", call(c.field(src), "digits")));
            case Operand.AlphanumericLiteral a -> stmt(call(field, "set", str(a.value())));
            default -> throw HandlerException.unsupported("MOVE from " + src.category() + " to alphanumeric-edited is not supported");
        };
    }

    private static List<Statement> moveFromEdited(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = moveTarget(u, c);
        Operand src = moveSource(u, c);
        if (!(src instanceof Operand.Field s) || !s.edited() || !s.item().edited().numeric()) {
            throw HandlerException.notApplicable("the sending item is not numeric-edited");
        }
        if (!(target instanceof Operand.ReturnCode || target instanceof Operand.Field t && t.numeric())) {
            throw HandlerException.notApplicable("receiver " + receiverName(u, target) + " is not numeric");
        }
        return List.of(stmt(call(c.field(target), "move", call(c.field(src), "value"))));
    }

    /** A group receives the sending item's bytes unconverted (a numeric item's digits and sign as stored). */
    private static List<Statement> moveToGroup(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = moveTarget(u, c);
        if (!(target instanceof Operand.Field t) || !t.item().isGroup() || t.refmod() != null) {
            throw HandlerException.notApplicable("receiver " + receiverName(u, target) + " is not a group item");
        }
        Operand src = moveSource(u, c);
        Expression field = c.receiving(target);
        Expression value = switch (src) {
            case Operand.Field f when f.numeric() -> {
                c.requireBytes(f.item(), "MOVE of " + f.item().cobolName() + " to a group");
                yield call(c.field(src), "bytes");
            }
            case Operand.Field f when f.alphanumeric() -> call(c.field(src), "get");
            case Operand.ReturnCode r -> throw HandlerException.unsupported("MOVE RETURN-CODE to a group item is not supported");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.NumericLiteral n when n.isInteger() && !n.isSigned() -> str(n.text());
            case Operand.Figurative f -> null;
            default -> throw HandlerException.unsupported("MOVE from " + src.category() + " to a group item is not supported");
        };
        if (src instanceof Operand.Figurative f) {
            return List.of(stmt(call(field, "fill", f.isSingle() ? chr(f.fill()) : str(f.pattern()))));
        }
        return List.of(stmt(call(field, "set", value)));
    }

    private static String receiverName(OpUnit u, Operand target) {
        return u.receiver() != null ? u.receiver().getName() : target instanceof Operand.Field f ? f.item().cobolName() : "?";
    }

    /** MOVE of {@code src} into an alphanumeric field (shared by MOVE, SET condition-name and ACCEPT). */
    static Statement storeAlphanumeric(Expression field, Operand src, TranslationContext c) throws HandlerException {
        Expression value = switch (src) {
            case Operand.Field f when f.alphanumeric() -> call(c.field(src), "get");
            case Operand.Field f when f.integer() -> c.field(src);
            case Operand.Field f when f.numeric() ->
                throw HandlerException.unsupported("MOVE of non-integer numeric " + f.item().cobolName() + " to alphanumeric is invalid COBOL");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.NumericLiteral n when n.isInteger() -> str(n.unsignedText());
            case Operand.NumericLiteral n -> throw HandlerException.unsupported("MOVE of non-integer literal to alphanumeric is invalid COBOL");
            case Operand.Figurative f -> null;
            default -> throw HandlerException.unsupported("MOVE from " + src.category() + " to alphanumeric is not supported");
        };
        if (src instanceof Operand.Figurative f) {
            return stmt(call(field, "fill", f.isSingle() ? chr(f.fill()) : str(f.pattern())));
        }
        return stmt(call(field, "set", value));
    }

    private static List<Statement> moveToNumeric(OpUnit u, TranslationContext c) throws HandlerException {
        Operand target = moveTarget(u, c);
        boolean numericTarget = target instanceof Operand.ReturnCode
                || target instanceof Operand.Field t && t.numeric();
        if (!numericTarget) {
            throw HandlerException.notApplicable("receiver " + receiverName(u, target) + " is not numeric");
        }
        return List.of(storeNumeric(c.field(target), moveSource(u, c), c));
    }

    /** MOVE of {@code src} into a numeric field. */
    static Statement storeNumeric(Expression field, Operand src, TranslationContext c) throws HandlerException {
        if (!src.isNumericValued() || src instanceof Operand.Arithmetic) {
            throw HandlerException.unsupported("MOVE from " + src.category() + " to numeric is not supported (no de-editing or alphanumeric-to-numeric conversion)");
        }
        Expression value = src instanceof Operand.Field || src instanceof Operand.ReturnCode ? c.field(src) : c.numeric(src);
        return stmt(call(field, "move", value));
    }

    // ---------------------------------------------------------- arithmetic

    /** A numeric receiver with its ROUNDED flag. */
    private record Receiver(Expression field, int scale, boolean rounded) {}

    private static Receiver receiver(Call call, ASGElement phrase, TranslationContext c) throws HandlerException {
        Operand o = c.operand(call);
        int scale;
        if (o instanceof Operand.Field f && f.numeric()) {
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
            case CORRESPONDING -> out.add(corresponding(u, c, "add"));
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
            case CORRESPONDING -> out.add(corresponding(u, c, "subtract"));
        }
        return out;
    }

    /** One pair of ADD / SUBTRACT CORRESPONDING: target = target op source. */
    private static Statement corresponding(OpUnit u, TranslationContext c, String op) throws HandlerException {
        var pair = u.corresponding();
        if (pair == null || pair.problem() != null) {
            throw HandlerException.unsupported(pair == null ? "CORRESPONDING operands must be group items" : pair.problem());
        }
        Expression target = c.field(new Operand.Field(pair.target()));
        Expression source = c.numeric(new Operand.Field(pair.source()));
        return stmt(call(target, "store", call(call(target.clone(), "get"), op, source), bool(pair.rounded()), bool(false)));
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
            return divideRemainder(div, c);
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

    /**
     * DIVIDE ... GIVING q REMAINDER r. The remainder is {@code dividend - q' * divisor} where q'
     * is the quotient truncated to q's scale, as in GnuCOBOL 3.2: never rounded, even with
     * ROUNDED, and not cut to q's integer digits. Both receivers are stored even if one has a
     * size error.
     */
    private static List<Statement> divideRemainder(DivideStatement div, TranslationContext c) throws HandlerException {
        if (div.getDivideType() == DivideStatement.DivideType.INTO) {
            throw HandlerException.unsupported("REMAINDER requires GIVING");
        }
        boolean into = div.getDivideType() == DivideStatement.DivideType.INTO_GIVING;
        var phrase = into ? div.getDivideIntoGivingStatement().getGivingPhrase() : div.getDivideByGivingStatement().getGivingPhrase();
        if (phrase.getGivings().size() != 1) {
            throw HandlerException.unsupported("DIVIDE ... REMAINDER with several GIVING receivers is invalid");
        }
        Expression operand = numericOperand(div.getOperandValueStmt(), c);
        Expression other = numericOperand(into ? div.getDivideIntoGivingStatement().getIntoValueStmt()
                : div.getDivideByGivingStatement().getByValueStmt(), c);
        var g = phrase.getGivings().getFirst();
        Receiver q = receiver(g.getGivingCall(), g, c);
        Receiver r = receiver(div.getRemainder().getRemainderCall(), div.getRemainder(), c);
        List<Statement> out = new ArrayList<>();
        Expression dividend = once(into ? other : operand, 2, out, c, "dividend");
        Expression divisor = once(into ? operand : other, 2, out, c, "divisor");
        Receiver remainder = new Receiver(r.field(), r.scale(), false);
        Map<Receiver, Expression> values = new LinkedHashMap<>();
        values.put(q, staticCall("Arithmetic", "divide", dividend.clone(), divisor.clone(), integer(q.scale()), bool(q.rounded())));
        values.put(remainder, staticCall("Arithmetic", "remainder", dividend.clone(), divisor.clone(), integer(q.scale())));
        out.addAll(storeAll(List.of(q, remainder), values::get, div.getOnSizeErrorPhrase(), div.getNotOnSizeErrorPhrase(), c));
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
        if (d.getAt() != null || d.getOnExceptionClause() != null || d.getNotOnExceptionClause() != null) {
            throw HandlerException.unsupported("DISPLAY with AT or exception phrases is not supported");
        }
        boolean err = false;
        if (d.getUpon() != null) {
            String device = c.device(deviceName(d.getUpon()));
            switch (device) {
                case "CONSOLE", "SYSOUT" -> err = false;
                case "SYSERR" -> err = true;
                default -> throw new HandlerException(HandlerException.Kind.MISSING_TARGET,
                        "DISPLAY UPON " + device + " (a device other than CONSOLE, SYSOUT or SYSERR) has no target");
            }
        }
        NodeList<Expression> args = new NodeList<>();
        for (var operand : d.getOperands()) {
            args.add(displayValue(c.operand(operand.getOperandValueStmt()), c));
        }
        boolean noAdvancing = d.getWith() != null;
        String method = err ? (noAdvancing ? "errNoAdvancing" : "err") : (noAdvancing ? "noAdvancing" : "line");
        return List.of(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(name("Display"), method, args)));
    }

    /** The DISPLAY text of an operand. */
    private static Expression displayValue(Operand o, TranslationContext c) throws HandlerException {
        return switch (o) {
            case Operand.Field f when f.numeric() && c.decimalPointComma() && f.item().numeric().scale() > 0 ->
                    call(c.field(o), "display", chr(','));
            case Operand.Field f when f.alphanumeric() || f.numeric() -> call(c.field(o), "display");
            case Operand.ReturnCode r -> call(c.field(o), "display");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.NumericLiteral n -> str(n.displayText());
            case Operand.Figurative f when !f.name().equals("ALL") -> str(f.pattern());
            default -> throw HandlerException.unsupported("DISPLAY of " + o.category() + " is not supported");
        };
    }

    /** EXHIBIT [NAMED]: items as {@code NAME = value} with the name as written, literals as is, space separated. */
    private static List<Statement> exhibit(OpUnit u, TranslationContext c) throws HandlerException {
        var ctx = (io.proleap.cobol.CobolParser.ExhibitStatementContext) u.statement().getCtx();
        if (ctx.CHANGED() != null) {
            throw HandlerException.unsupported("EXHIBIT CHANGED is not implemented by GnuCOBOL 3.2, so its behaviour has no reference");
        }
        NodeList<Expression> args = new NodeList<>();
        var operands = ((io.proleap.cobol.asg.metamodel.procedure.exhibit.ExhibitStatement) u.statement()).getOperands();
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                args.add(str(" "));
            }
            Operand o = c.operand(operands.get(i).getOperandValueStmt());
            if (o instanceof Operand.Field || o instanceof Operand.ReturnCode) {
                args.add(str(operands.get(i).getCtx().getText() + " = "));
            }
            args.add(displayValue(o, c));
        }
        return List.of(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(name("Display"), "line", args)));
    }

    // ------------------------------------------------------------- ACCEPT

    private static List<Statement> acceptStdin(OpUnit u, TranslationContext c) throws HandlerException {
        AcceptStatement a = (AcceptStatement) u.statement();
        rejectAcceptExceptions(a);
        if (a.getAcceptType() == AcceptStatement.AcceptType.MNEMONIC) {
            String device = c.device(deviceName(a.getAcceptFromMnemonicStatement()));
            if (!device.equals("SYSIN") && !device.equals("CONSOLE")) {
                throw new HandlerException(HandlerException.Kind.MISSING_TARGET, "ACCEPT FROM " + device
                        + " (environment, command line or another device) has no target");
            }
        } else if (a.getAcceptType() != AcceptStatement.AcceptType.NO_FROM) {
            throw HandlerException.notApplicable("ACCEPT FROM " + a.getAcceptType() + " is not standard input");
        }
        Operand target = c.operand(a.getAcceptCall());
        if (!(target instanceof Operand.Field f) || !f.alphanumeric() || f.edited()) {
            throw HandlerException.unsupported("ACCEPT into " + target.category() + " is not supported (only alphanumeric items)");
        }
        return List.of(stmt(call(c.receiving(target), "set", staticCall("Accept", "line"))));
    }

    private static List<Statement> acceptDatetime(OpUnit u, TranslationContext c) throws HandlerException {
        AcceptStatement a = (AcceptStatement) u.statement();
        rejectAcceptExceptions(a);
        if (a.getAcceptType() != AcceptStatement.AcceptType.DATE) {
            throw HandlerException.notApplicable("not ACCEPT FROM DATE / DAY / DAY-OF-WEEK / TIME");
        }
        String method = switch (Facts.acceptFrom(a)) {
            case "date" -> "date";
            case "date_yyyymmdd" -> "dateYyyymmdd";
            case "day" -> "day";
            case "day_yyyyddd" -> "dayYyyyddd";
            case "day_of_week" -> "dayOfWeek";
            case "time" -> "time";
            default -> throw HandlerException.unsupported("ACCEPT " + a.getCtx().getText() + " is not supported");
        };
        // The value is an unsigned integer of fixed width, moved as such.
        Operand target = c.operand(a.getAcceptCall());
        Expression value = staticCall("Clock", method);
        if (target instanceof Operand.Field f && f.alphanumeric()) {
            return List.of(stmt(call(c.field(target), "set", value)));
        }
        if (target instanceof Operand.ReturnCode || target instanceof Operand.Field f && f.numeric()) {
            return List.of(stmt(call(c.field(target), "move", create("BigDecimal", value))));
        }
        throw HandlerException.unsupported("ACCEPT into " + target.category() + " is not supported");
    }

    private static void rejectAcceptExceptions(AcceptStatement a) throws HandlerException {
        if (a.getOnExceptionClause() != null || a.getNotOnExceptionClause() != null) {
            throw HandlerException.unsupported("ACCEPT with exception phrases is not supported");
        }
    }

    // ---------------------------------------------------------------- SET

    private static List<Statement> setCondition(OpUnit u, TranslationContext c) throws HandlerException {
        SetStatement set = (SetStatement) u.statement();
        if (set.getSetType() != SetStatement.SetType.TO) {
            throw HandlerException.notApplicable("SET ... UP / DOWN BY is not a condition-name SET");
        }
        List<Statement> out = new ArrayList<>();
        for (var setTo : set.getSetTos()) {
            if (setTo.getValues().size() != 1) {
                throw HandlerException.unsupported("SET with several values is not supported");
            }
            Boolean value = booleanValue(setTo.getValues().getFirst().getValueStmt());
            if (value == null) {
                throw HandlerException.notApplicable("SET ... TO a value other than TRUE / FALSE");
            }
            for (var to : setTo.getTos()) {
                Operand o = c.operand(to.getToCall());
                if (!(o instanceof Operand.Field f) || !f.item().isCondition()) {
                    throw HandlerException.notApplicable(to.getToCall().getName() + " is not a condition-name");
                }
                DataItem cond = f.item();
                Operand v;
                if (value) {
                    v = cond.conditionValues().getFirst().from();
                } else if (cond.falseValue() != null) {
                    v = cond.falseValue();
                } else {
                    throw HandlerException.unsupported("SET " + cond.cobolName() + " TO FALSE needs VALUE ... WHEN SET TO FALSE");
                }
                Expression field = c.field(new Operand.Field(cond.parent()));
                out.add(cond.parent().isAlphanumeric() ? storeAlphanumeric(field, v, c) : storeNumeric(field, v, c));
            }
        }
        return out;
    }

    /** TRUE / FALSE as a value statement, or null. */
    static Boolean booleanValue(ValueStmt v) {
        if (v instanceof LiteralValueStmt lit && lit.getLiteral().getLiteralType() == Literal.LiteralType.BOOLEAN) {
            return lit.getLiteral().getCtx().getText().equalsIgnoreCase("TRUE");
        }
        return null;
    }

    // ------------------------------------------------------- control flow

    /** An IF branch: its statements, or a jump to the end of the sentence for NEXT SENTENCE. */
    private static BlockStmt branch(Scope scope, boolean nextSentence, TranslationContext c) throws HandlerException {
        return nextSentence ? new BlockStmt(NodeList.nodeList(c.jump(TranslationContext.Jump.NEXT_SENTENCE))) : c.nested(scope);
    }

    private static List<Statement> ifElse(OpUnit u, TranslationContext c) throws HandlerException {
        IfStatement s = (IfStatement) u.statement();
        Expression cond = c.condition(s.getCondition());
        BlockStmt then = branch(s.getThen(), s.getThen().isNextSentence(), c);
        BlockStmt otherwise = s.getElse() == null ? null : branch(s.getElse(), s.getElse().isNextSentence(), c);
        return List.of(new IfStmt(cond, then, otherwise));
    }

    /** An EVALUATE subject: a value, TRUE / FALSE, or a condition. */
    private record Subject(Operand value, Boolean bool, Expression condition) {}

    private static Subject subject(ValueStmt v, ParserRuleContext where, TranslationContext c) throws HandlerException {
        Boolean b = booleanValue(v);
        if (b != null) {
            return new Subject(null, b, null);
        }
        if (v instanceof ConditionValueStmt cond) {
            return new Subject(null, null, c.condition(cond));
        }
        Operand o = c.operand(v);
        if (o instanceof Operand.Field f && f.item().isCondition()) {
            return new Subject(null, null, c.conditionName(f.item(), where));
        }
        return new Subject(o, null, null);
    }

    /** The test of one WHEN object against its subject, or null for ANY. */
    private static Expression selection(Subject subject, io.proleap.cobol.asg.metamodel.procedure.evaluate.Condition object,
            TranslationContext c) throws HandlerException {
        ParserRuleContext where = object.getCtx();
        Expression test = switch (object.getConditionType()) {
            case ANY -> null;
            case VALUE, VALUE_THROUGH -> {
                Boolean literal = booleanValue(object.getValue().getValueStmt());
                if (literal != null && object.getThrough() == null) {
                    // ProLeap parses WHEN TRUE / FALSE as a value.
                    yield booleanObject(subject, literal, where);
                }
                Operand value = c.operand(object.getValue().getValueStmt());
                if (subject.value() == null) {
                    // TRUE / FALSE / condition subject with a condition-name object.
                    if (subject.bool() != null && value instanceof Operand.Field f && f.item().isCondition() && object.getThrough() == null) {
                        Expression e = c.conditionName(f.item(), where);
                        yield subject.bool() ? e : not(e);
                    }
                    throw HandlerException.unsupported("WHEN " + where.getText() + " does not match a TRUE/FALSE or condition subject");
                }
                if (object.getThrough() == null) {
                    yield c.compare(subject.value(), value, RelationalOperatorType.EQUAL, where);
                }
                Operand thru = c.operand(object.getThrough().getValue().getValueStmt());
                yield new EnclosedExpr(new BinaryExpr(c.compare(subject.value(), value, RelationalOperatorType.GREATER_OR_EQUAL, where),
                        c.compare(subject.value(), thru, RelationalOperatorType.LESS_OR_EQUAL, where), BinaryExpr.Operator.AND));
            }
            case BOOLEAN -> {
                Boolean b = booleanValue(object.getConditionValueStmt());
                yield booleanObject(subject, b == null ? where.getText().toUpperCase(java.util.Locale.ROOT).endsWith("TRUE") : b, where);
            }
            case CONDITION -> {
                if (subject.bool() == null) {
                    throw HandlerException.unsupported("WHEN condition needs a TRUE or FALSE subject");
                }
                Expression e = c.condition((ConditionValueStmt) object.getConditionValueStmt());
                yield subject.bool() ? e : not(e);
            }
        };
        return test != null && object.isNot() ? not(test) : test;
    }

    private static Expression booleanObject(Subject subject, boolean b, ParserRuleContext where) throws HandlerException {
        if (subject.condition() != null) {
            return b ? subject.condition() : not(subject.condition());
        }
        if (subject.bool() != null) {
            return bool(subject.bool() == b);
        }
        throw HandlerException.unsupported("WHEN " + where.getText() + " needs a TRUE, FALSE or condition subject");
    }

    /** The name after UPON / FROM, from the parse tree (ProLeap leaves environment names unnamed). */
    static String deviceName(io.proleap.cobol.asg.metamodel.ASGElement phrase) {
        var ctx = phrase.getCtx();
        return ctx.getChild(ctx.getChildCount() - 1).getText();
    }

    private static List<Statement> evaluate(OpUnit u, TranslationContext c) throws HandlerException {
        EvaluateStatement e = (EvaluateStatement) u.statement();
        List<Subject> subjects = new ArrayList<>();
        subjects.add(subject(e.getSelect().getSelectValueStmt(), e.getSelect().getCtx(), c));
        for (var also : e.getAlsoSelects()) {
            subjects.add(subject(also.getSelect().getSelectValueStmt(), also.getCtx(), c));
        }
        List<Expression> tests = new ArrayList<>();
        List<BlockStmt> bodies = new ArrayList<>();
        for (var phrase : e.getWhenPhrases()) {
            Expression any = null;
            for (var when : phrase.getWhens()) {
                List<io.proleap.cobol.asg.metamodel.procedure.evaluate.Condition> objects = new ArrayList<>();
                objects.add(when.getCondition());
                when.getAlsoConditions().forEach(a -> objects.add(a.getCondition()));
                if (objects.size() != subjects.size()) {
                    throw HandlerException.unsupported("WHEN " + when.getCtx().getText() + " has " + objects.size()
                            + " objects for " + subjects.size() + " subjects");
                }
                Expression all = null;
                for (int i = 0; i < objects.size(); i++) {
                    Expression t = selection(subjects.get(i), objects.get(i), c);
                    if (t != null) {
                        all = all == null ? t : new BinaryExpr(all, t, BinaryExpr.Operator.AND);
                    }
                }
                Expression whenTest = all == null ? bool(true) : all;
                any = any == null ? whenTest : new BinaryExpr(any, whenTest, BinaryExpr.Operator.OR);
            }
            tests.add(any);
            bodies.add(c.nested(phrase));
        }
        Statement chain = e.getWhenOther() == null ? null : c.nested(e.getWhenOther());
        for (int i = tests.size() - 1; i >= 0; i--) {
            chain = new IfStmt(tests.get(i), bodies.get(i), chain);
        }
        return chain == null ? List.of() : List.of(chain);
    }

    private static List<Statement> perform(OpUnit u, TranslationContext c) throws HandlerException {
        PerformStatement p = (PerformStatement) u.statement();
        BlockStmt body;
        PerformType type;
        TranslationContext.PerformFrame frame = null;
        if (p.getPerformStatementType() == PerformStatement.PerformStatementType.INLINE) {
            type = p.getPerformInlineStatement().getPerformType();
            frame = new TranslationContext.PerformFrame(c.fresh("perform"), c.fresh("cycle"));
            body = c.nestedPerform(p.getPerformInlineStatement(), frame);
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
            if (frame != null && (frame.exitUsed || frame.cycleUsed)) {
                // Without a loop, ending the iteration ends the PERFORM.
                String label = frame.exitUsed ? frame.exitLabel : frame.cycleLabel;
                if (frame.exitUsed && frame.cycleUsed) {
                    body = new BlockStmt(NodeList.nodeList(new LabeledStmt(frame.cycleLabel, body)));
                }
                return List.of(new LabeledStmt(label, body));
            }
            return body.getStatements().size() == 1 && p.getPerformStatementType() == PerformStatement.PerformStatementType.PROCEDURE
                    ? List.of(body.getStatement(0)) : List.of(body);
        }
        if (frame != null && frame.cycleUsed) {
            // EXIT PERFORM CYCLE ends this iteration: the loop's step and test still run.
            body = new BlockStmt(NodeList.nodeList(new LabeledStmt(frame.cycleLabel, body)));
        }
        List<Statement> loop = loop(type, body, c);
        if (frame != null && frame.exitUsed) {
            Statement last = loop.getLast();
            List<Statement> out = new ArrayList<>(loop.subList(0, loop.size() - 1));
            out.add(new LabeledStmt(frame.exitLabel, last));
            return out;
        }
        return loop;
    }

    /** The loop for TIMES / UNTIL / VARYING around {@code body}; its last statement is the loop itself. */
    private static List<Statement> loop(PerformType type, BlockStmt body, TranslationContext c) throws HandlerException {
        return switch (type.getPerformTypeType()) {
            case TIMES -> {
                Operand times = c.operand(type.getTimes().getTimesValueStmt());
                boolean integral = times instanceof Operand.NumericLiteral n && n.isInteger()
                        || times instanceof Operand.Field f && f.integer();
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
                if (var instanceof Operand.Index idx) {
                    // An index holds an occurrence number: FROM sets it, BY adds to it.
                    Expression from = intValue(c.operand(phrase.getFrom().getFromValueStmt()), c);
                    Expression by = intValue(c.operand(phrase.getBy().getByValueStmt()), c);
                    Expression until = c.condition(phrase.getUntil().getCondition());
                    Statement init = stmt(new AssignExpr(name(idx.index().javaName()), from, AssignExpr.Operator.ASSIGN));
                    Statement step = stmt(new AssignExpr(name(idx.index().javaName()), by, AssignExpr.Operator.PLUS));
                    BlockStmt loop = body;
                    if (testAfter(varying.getTestClause())) {
                        loop.addStatement(new IfStmt(until, new BreakStmt(), null));
                        loop.addStatement(step);
                        yield List.of(init, new WhileStmt(bool(true), loop));
                    }
                    loop.addStatement(step);
                    yield List.of(init, new WhileStmt(not(until), loop));
                }
                if (!(var instanceof Operand.Field f && f.numeric())) {
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
        switch (s.getStopType()) {
            case STOP_RUN -> { }
            case STOP_RUN_AND_DISPLAY -> throw new HandlerException(HandlerException.Kind.MISSING_TARGET,
                    "STOP literal shows the literal and waits for an operator reply; there is no operator console target");
            case STOP_RUN_GIVING -> throw HandlerException.unsupported("STOP RUN GIVING / RETURNING is not supported");
        }
        return List.of(stmt(staticCall("ProgramSupport", "stopRun", self())));
    }

    private static List<Statement> exit(OpUnit u, TranslationContext c) throws HandlerException {
        return switch (Facts.exitKind(u.statement())) {
            case "program" -> List.of(stmt(staticCall("ProgramSupport", "exitProgram")));
            case "paragraph" -> List.of(c.jump(TranslationContext.Jump.EXIT_PARAGRAPH));
            case "section" -> List.of(c.jump(TranslationContext.Jump.EXIT_SECTION));
            case "perform" -> List.of(c.jump(TranslationContext.Jump.EXIT_PERFORM));
            case "perform_cycle" -> List.of(c.jump(TranslationContext.Jump.EXIT_PERFORM_CYCLE));
            case "plain" -> List.of(noOp("EXIT"));
            default -> throw HandlerException.unsupported("EXIT " + u.statement().getCtx().getText() + " is not supported");
        };
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

    /** An integer-valued operand (literal, integer item or index) as a Java int. */
    static Expression intValue(Operand o, TranslationContext c) throws HandlerException {
        return switch (o) {
            case Operand.NumericLiteral n when n.isInteger() -> integer(n.value().intValueExact());
            case Operand.Field f when f.integer() -> call(c.field(f), "intValue");
            case Operand.Index i -> name(i.index().javaName());
            default -> throw HandlerException.unsupported(o.category() + " where an integer is required");
        };
    }

    // ---------------------------------------------------------- INITIALIZE

    private static List<Statement> initialize(OpUnit u, TranslationContext c) throws HandlerException {
        var s = (io.proleap.cobol.asg.metamodel.procedure.initialize.InitializeStatement) u.statement();
        var ctx = (io.proleap.cobol.CobolParser.InitializeStatementContext) s.getCtx();
        boolean withFiller = ctx.FILLER() != null;
        boolean toValue = ctx.VALUE() != null;
        Map<String, Operand> replacing = new LinkedHashMap<>();
        if (s.getReplacingPhrase() != null) {
            for (var by : s.getReplacingPhrase().getBys()) {
                String category = switch (by.getByType()) {
                    case ALPHANUMERIC -> "alphanumeric";
                    case NUMERIC -> "numeric";
                    // No supported item is alphabetic or edited, so these replace nothing.
                    case ALPHANUMERIC_EDITED, NUMERIC_EDITED -> by.getByType().name().toLowerCase(java.util.Locale.ROOT);
                    // No item is alphabetic here (PIC A is treated as alphanumeric), so this replaces nothing.
                    case ALPHABETIC -> "alphabetic";
                    default -> throw HandlerException.unsupported("INITIALIZE REPLACING " + by.getByType() + " is not supported");
                };
                replacing.put(category, c.operand(by.getValueStmt()));
            }
        }
        // Without REPLACING or TO VALUE every item gets its default; with them, only THEN TO DEFAULT adds it.
        boolean defaults = (replacing.isEmpty() && !toValue) || ctx.DEFAULT() != null;
        List<Statement> out = new ArrayList<>();
        for (Call call : s.getDataItemCalls()) {
            Operand o = c.operand(call);
            if (!(o instanceof Operand.Field target) || target.refmod() != null) {
                throw HandlerException.unsupported("INITIALIZE of " + o.category() + " is not supported");
            }
            if (target.item().isGroup() && target.item().variableTable() != null) {
                throw HandlerException.unsupported("INITIALIZE of a group with OCCURS DEPENDING ON is not supported");
            }
            for (DataItem e : target.item().elementaryItems()) {
                if (e != target.item() && (skippedByInitialize(e, target.item(), withFiller))) {
                    continue;
                }
                if (e.kind() == DataItem.Kind.UNSUPPORTED) {
                    throw HandlerException.unsupported(e.cobolName() + ": " + e.unsupportedReason());
                }
                Statement store = initializeItem(e, toValue, replacing, defaults, c, target, out.size());
                if (store != null) {
                    out.add(store);
                }
            }
        }
        return out;
    }

    /** FILLER (unless WITH FILLER) and items under a REDEFINES below the target are left alone. */
    private static boolean skippedByInitialize(DataItem e, DataItem target, boolean withFiller) {
        for (DataItem i = e; i != null && i != target; i = i.parent()) {
            if (i.redefines() != null || (i.isFiller() && !withFiller)) {
                return true;
            }
        }
        return false;
    }

    /** The store for one elementary item, inside loops over the tables between it and the target. */
    private static Statement initializeItem(DataItem e, boolean toValue, Map<String, Operand> replacing, boolean defaults,
            TranslationContext c, Operand.Field target, int n) throws HandlerException {
        List<DataItem> extra = e.dimensions().subList(target.item().dimensions().size(), e.dimensions().size());
        List<Operand.Subscript> subs = new ArrayList<>(target.subscripts());
        List<String> vars = new ArrayList<>();
        for (DataItem t : extra) {
            String v = c.fresh("occurrence");
            vars.add(v);
            subs.add(new Operand.Subscript(new Operand.Index(new DataItem.IndexName(v, v, t)), 0));
        }
        Operand.Field ref = new Operand.Field(e, subs, null);
        Expression field = c.field(ref);
        Statement store;
        if (toValue && e.initialValue() != null) {
            store = stmt(call(field, "initial", str(e.initialValue())));
        } else if (replacing.containsKey(e.category())) {
            Operand v = replacing.get(e.category());
            store = e.isEdited() ? storeEdited(field, e, v, c) : e.isAlphanumeric() ? storeAlphanumeric(field, v, c) : storeNumeric(field, v, c);
        } else if (defaults) {
            store = stmt(e.isEdited() ? call(field, "fill", chr(e.edited().numeric() ? '0' : ' '))
                    : e.isAlphanumeric() ? call(field, "fill", chr(' ')) : call(field, "move", decimal("0")));
        } else {
            return null;
        }
        for (int i = extra.size() - 1; i >= 0; i--) {
            String v = vars.get(i);
            store = new ForStmt(NodeList.nodeList(new VariableDeclarationExpr(new VariableDeclarator(Ast.intType(), v, integer(1)))),
                    new BinaryExpr(name(v), integer(extra.get(i).occurs()), BinaryExpr.Operator.LESS_EQUALS),
                    NodeList.nodeList(new UnaryExpr(name(v), UnaryExpr.Operator.POSTFIX_INCREMENT)),
                    new BlockStmt(NodeList.nodeList(store)));
        }
        return store;
    }

    // ---------------------------------------------------------- SET index

    private static List<Statement> setIndex(OpUnit u, TranslationContext c) throws HandlerException {
        SetStatement set = (SetStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        if (set.getSetType() == SetStatement.SetType.BY) {
            var by = set.getSetBy();
            Expression amount = intValue(c.operand(by.getBy().getByValueStmt()), c);
            var op = by.getSetByType() == io.proleap.cobol.asg.metamodel.procedure.set.SetBy.SetByType.UP
                    ? AssignExpr.Operator.PLUS : AssignExpr.Operator.MINUS;
            for (var to : by.getTos()) {
                Operand r = c.operand(to.getToCall());
                if (r instanceof Operand.Index idx) {
                    out.add(stmt(new AssignExpr(name(idx.index().javaName()), amount.clone(), op)));
                } else if (isIndexItem(r)) {
                    Expression f = c.field(r);
                    out.add(stmt(call(f, "move", call(call(f.clone(), "get"), op == AssignExpr.Operator.PLUS ? "add" : "subtract",
                            staticCall("BigDecimal", "valueOf", amount.clone())))));
                } else {
                    throw HandlerException.notApplicable("SET " + to.getToCall().getName() + " UP / DOWN BY: only indexes are supported");
                }
            }
            return out;
        }
        for (var setTo : set.getSetTos()) {
            if (setTo.getValues().size() != 1) {
                throw HandlerException.unsupported("SET with several values is not supported");
            }
            var value = setTo.getValues().getFirst();
            if (value.getValueType() != io.proleap.cobol.asg.metamodel.procedure.set.Value.ValueType.CALL
                    || booleanValue(value.getValueStmt()) != null) {
                throw HandlerException.notApplicable("SET ... TO " + value.getCtx().getText() + " is not an index SET");
            }
            Operand v = c.operand(value.getValueStmt());
            for (var to : setTo.getTos()) {
                Operand r = c.operand(to.getToCall());
                if (r instanceof Operand.Index idx) {
                    out.add(stmt(new AssignExpr(name(idx.index().javaName()), intValue(v, c), AssignExpr.Operator.ASSIGN)));
                } else if (isIndexItem(r)) {
                    out.add(stmt(call(c.field(r), "move", staticCall("BigDecimal", "valueOf", intValue(v, c)))));
                } else if (r instanceof Operand.Field f && f.integer() && v instanceof Operand.Index idx) {
                    out.add(stmt(call(c.field(f), "move", staticCall("BigDecimal", "valueOf", name(idx.index().javaName())))));
                } else {
                    throw HandlerException.notApplicable("SET " + to.getToCall().getName() + " TO " + value.getCtx().getText()
                            + ": neither side is an index");
                }
            }
        }
        return out;
    }

    /** A USAGE INDEX data item. */
    private static boolean isIndexItem(Operand o) {
        return o instanceof Operand.Field f && f.numeric() && f.item().usage() == DataItem.Usage.INDEX;
    }

    // ------------------------------------------------------------- SEARCH

    private static boolean isSearchAll(OpUnit u) {
        return ((io.proleap.cobol.CobolParser.SearchStatementContext) u.statement().getCtx()).ALL() != null;
    }

    /** The searched table and the index the search runs on (VARYING index, else the first INDEXED BY). */
    private record Searched(DataItem table, DataItem.IndexName index, Operand varying) {}

    private static Searched searched(io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement s, TranslationContext c)
            throws HandlerException {
        DataItem table = c.dataItem(s.getDataCall());
        if (table.occurs() == 0) {
            throw HandlerException.unsupported("SEARCH " + table.cobolName() + ": not a table");
        }
        Operand varying = s.getVaryingPhrase() == null ? null : c.operand(s.getVaryingPhrase().getDataCall());
        DataItem.IndexName index;
        if (varying instanceof Operand.Index i && i.index().table() == table) {
            index = i.index();
            varying = null;
        } else if (table.indexes().isEmpty()) {
            throw HandlerException.unsupported("SEARCH " + table.cobolName() + ": the table has no INDEXED BY");
        } else {
            index = table.indexes().getFirst();
        }
        if (varying != null && !(varying instanceof Operand.Index) && !(varying instanceof Operand.Field f && f.integer())) {
            throw HandlerException.unsupported("SEARCH VARYING " + varying.category() + " is not supported");
        }
        return new Searched(table, index, varying);
    }

    /** The number of occurrences in use: OCCURS, or the DEPENDING ON item's value. */
    private static Expression occurrences(DataItem table, TranslationContext c) throws HandlerException {
        return table.dependingOn() == null ? integer(table.occurs())
                : call(c.field(new Operand.Field(table.dependingOn())), "intValue");
    }

    /**
     * Serial SEARCH from the index's current value: AT END once it passes the last occurrence in
     * use, otherwise the first WHEN that holds; the index (and VARYING item) step by one.
     */
    private static List<Statement> search(OpUnit u, TranslationContext c) throws HandlerException {
        if (isSearchAll(u)) {
            throw HandlerException.notApplicable("SEARCH ALL is not a serial SEARCH");
        }
        var s = (io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement) u.statement();
        Searched t = searched(s, c);
        String label = c.fresh("search");
        String index = t.index().javaName();
        BlockStmt loop = new BlockStmt();
        BlockStmt atEnd = s.getAtEndPhrase() == null ? new BlockStmt() : c.nested(s.getAtEndPhrase());
        atEnd.addStatement(new BreakStmt(label));
        loop.addStatement(new IfStmt(new BinaryExpr(name(index), occurrences(t.table(), c), BinaryExpr.Operator.GREATER), atEnd, null));
        for (var when : s.getWhenPhrases()) {
            BlockStmt body = when.getWhenType() == io.proleap.cobol.asg.metamodel.procedure.search.WhenPhrase.WhenType.NEXT_SENTENCE
                    ? new BlockStmt(NodeList.nodeList(c.jump(TranslationContext.Jump.NEXT_SENTENCE))) : c.nested(when);
            body.addStatement(new BreakStmt(label));
            loop.addStatement(new IfStmt(c.condition(when.getCondition()), body, null));
        }
        loop.addStatement(stmt(new UnaryExpr(name(index), UnaryExpr.Operator.POSTFIX_INCREMENT)));
        if (t.varying() instanceof Operand.Index other) {
            loop.addStatement(stmt(new UnaryExpr(name(other.index().javaName()), UnaryExpr.Operator.POSTFIX_INCREMENT)));
        } else if (t.varying() != null) {
            Expression f = c.field(t.varying());
            loop.addStatement(stmt(call(f, "store", call(call(f.clone(), "get"), "add", decimal("1")), bool(false), bool(false))));
        }
        return List.of(new LabeledStmt(label, new WhileStmt(bool(true), loop)));
    }

    /**
     * SEARCH ALL finds a match only when the table is in KEY order; which occurrence it finds
     * for equal keys, and what it does on an unordered table, depend on the compiler's binary
     * search. Neither can be proven here, so it is always a decision offering both candidates.
     */
    private static List<Statement> searchAll(OpUnit u, TranslationContext c) throws HandlerException {
        if (!isSearchAll(u)) {
            throw HandlerException.notApplicable("not SEARCH ALL");
        }
        var s = (io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement) u.statement();
        Searched t = searched(s, c);
        if (t.table().keys().isEmpty()) {
            throw HandlerException.unsupported("SEARCH ALL " + t.table().cobolName() + ": the table has no ASCENDING / DESCENDING KEY");
        }
        throw HandlerException.unprovable("SEARCH ALL " + t.table().cobolName()
                + ": the keys cannot be proven to be in order and unique, so the occurrence found is not determined");
    }

    /** One WHEN of SEARCH ALL, which must be the only one. */
    private static io.proleap.cobol.asg.metamodel.procedure.search.WhenPhrase searchAllWhen(
            io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement s) throws HandlerException {
        if (s.getWhenPhrases().size() != 1) {
            throw HandlerException.unsupported("SEARCH ALL has exactly one WHEN");
        }
        return s.getWhenPhrases().getFirst();
    }

    private static List<Statement> searchAllLinear(OpUnit u, TranslationContext c) throws HandlerException {
        if (!isSearchAll(u)) {
            throw HandlerException.notApplicable("not SEARCH ALL");
        }
        var s = (io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement) u.statement();
        Searched t = searched(s, c);
        var when = searchAllWhen(s);
        String label = c.fresh("search");
        String index = t.index().javaName();
        BlockStmt found = c.nested(when);
        found.addStatement(new BreakStmt(label));
        BlockStmt atEnd = s.getAtEndPhrase() == null ? new BlockStmt() : c.nested(s.getAtEndPhrase());
        BlockStmt loop = new BlockStmt(NodeList.nodeList(new IfStmt(c.condition(when.getCondition()), found, null)));
        Statement scan = new ForStmt(NodeList.nodeList(new AssignExpr(name(index), integer(1), AssignExpr.Operator.ASSIGN)),
                new BinaryExpr(name(index), occurrences(t.table(), c), BinaryExpr.Operator.LESS_EQUALS),
                NodeList.nodeList(new UnaryExpr(name(index), UnaryExpr.Operator.POSTFIX_INCREMENT)), loop);
        BlockStmt body = new BlockStmt(NodeList.nodeList(scan));
        atEnd.getStatements().forEach(body::addStatement);
        return List.of(new LabeledStmt(label, body));
    }

    /** Binary search on the first KEY: halves the occurrences in use by comparing the key with the WHEN's value. */
    private static List<Statement> searchAllBinary(OpUnit u, TranslationContext c) throws HandlerException {
        if (!isSearchAll(u)) {
            throw HandlerException.notApplicable("not SEARCH ALL");
        }
        var s = (io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement) u.statement();
        Searched t = searched(s, c);
        var when = searchAllWhen(s);
        var cond = when.getCondition();
        var rel = cond.getAndOrConditions().isEmpty() ? cond.getCombinableCondition().getSimpleCondition().getRelationCondition() : null;
        if (rel == null || rel.getArithmeticComparison() == null
                || rel.getArithmeticComparison().getOperator().getRelationalOperatorType() != RelationalOperatorType.EQUAL) {
            throw HandlerException.unsupported("binary SEARCH ALL candidate needs WHEN key = value");
        }
        Operand key = c.operand(rel.getArithmeticComparison().getArithmeticExpressionLeft());
        Operand value = c.operand(rel.getArithmeticComparison().getArithmeticExpressionRight());
        DataItem.Key first = t.table().keys().getFirst();
        if (!(key instanceof Operand.Field kf) || kf.item() != first.item()) {
            throw HandlerException.unsupported("binary SEARCH ALL candidate needs WHEN on the first KEY");
        }
        String label = c.fresh("search");
        String low = c.fresh("low");
        String high = c.fresh("high");
        String index = t.index().javaName();
        BlockStmt found = c.nested(when);
        found.addStatement(new BreakStmt(label));
        BlockStmt atEnd = s.getAtEndPhrase() == null ? new BlockStmt() : c.nested(s.getAtEndPhrase());
        Expression below = c.compare(key, value, first.ascending() ? RelationalOperatorType.LESS : RelationalOperatorType.GREATER, when.getCtx());
        BlockStmt loop = new BlockStmt(NodeList.nodeList(
                stmt(new AssignExpr(name(index), new BinaryExpr(new EnclosedExpr(new BinaryExpr(name(low), name(high),
                        BinaryExpr.Operator.PLUS)), integer(2), BinaryExpr.Operator.DIVIDE), AssignExpr.Operator.ASSIGN)),
                new IfStmt(c.compare(key, value, RelationalOperatorType.EQUAL, when.getCtx()), found,
                        new IfStmt(below, stmt(new AssignExpr(name(low), new BinaryExpr(name(index), integer(1), BinaryExpr.Operator.PLUS),
                                AssignExpr.Operator.ASSIGN)),
                                stmt(new AssignExpr(name(high), new BinaryExpr(name(index), integer(1), BinaryExpr.Operator.MINUS),
                                        AssignExpr.Operator.ASSIGN))))));
        BlockStmt body = new BlockStmt();
        body.addStatement(declare(Ast.intType(), low, integer(1)));
        body.addStatement(declare(Ast.intType(), high, occurrences(t.table(), c)));
        body.addStatement(new WhileStmt(new BinaryExpr(name(low), name(high), BinaryExpr.Operator.LESS_EQUALS), loop));
        atEnd.getStatements().forEach(body::addStatement);
        return List.of(new LabeledStmt(label, body));
    }

    // ------------------------------------------------- STRING / UNSTRING / INSPECT

    /** Character data of a sending operand: alphanumeric items, literals, a figurative constant's character, unsigned integers' digits. */
    private static Expression text(Operand o, TranslationContext c) throws HandlerException {
        return switch (o) {
            case Operand.Field f when f.alphanumeric() -> call(c.field(o), "get");
            case Operand.Field f when f.integer() && !f.item().numeric().signed() && f.item().usage() == DataItem.Usage.DISPLAY ->
                    call(c.field(o), "bytes");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.Figurative f when f.isSingle() -> str(f.pattern());
            case Operand.Figurative f -> str(f.pattern());
            default -> throw HandlerException.unsupported(o.category() + " as character data is not supported");
        };
    }

    /** A POINTER / COUNT / TALLYING item: an unsubscripted-or-not integer numeric item. */
    private static Expression integerItem(Call call, TranslationContext c) throws HandlerException {
        Operand o = c.operand(call);
        if (!(o instanceof Operand.Field f) || !f.integer()) {
            throw HandlerException.unsupported(call.getName() + " must be an integer numeric item");
        }
        return c.field(o);
    }

    /** An alphanumeric item (or group) changed in place by STRING / INSPECT. */
    private static Expression alphanumericTarget(Call call, TranslationContext c) throws HandlerException {
        Operand o = c.operand(call);
        if (!(o instanceof Operand.Field f) || !f.alphanumeric() || f.edited()) {
            throw HandlerException.unsupported(call.getName() + " must be an alphanumeric item or group");
        }
        return c.field(o);
    }

    private static List<Statement> overflowPhrases(Expression overflow, ASGElement on, ASGElement notOn, TranslationContext c) {
        if (on == null && notOn == null) {
            return List.of(stmt(overflow));
        }
        String flag = c.fresh("overflow");
        return List.of(declare(Ast.booleanType(), flag, overflow),
                new IfStmt(name(flag), on == null ? new BlockStmt() : c.nested((Scope) on), notOn == null ? null : c.nested((Scope) notOn)));
    }

    private static List<Statement> stringStatement(OpUnit u, TranslationContext c) throws HandlerException {
        var s = (io.proleap.cobol.asg.metamodel.procedure.string.StringStatement) u.statement();
        Expression target = alphanumericTarget(s.getIntoPhrase().getIntoCall(), c);
        Expression pointer = s.getWithPointerPhrase() == null ? new com.github.javaparser.ast.expr.NullLiteralExpr()
                : integerItem(s.getWithPointerPhrase().getPointerCall(), c);
        Expression chain = create("StringOps.Concat", target, pointer);
        for (var sendings : s.getSendings()) {
            Expression delimiter;
            if (sendings.getSendingsType() != io.proleap.cobol.asg.metamodel.procedure.string.Sendings.SendingsType.DELIMITED_BY) {
                throw HandlerException.unsupported("STRING ... FOR is not supported");
            }
            var by = sendings.getDelimitedByPhrase();
            delimiter = by.getDelimitedByType() == io.proleap.cobol.asg.metamodel.procedure.string.DelimitedByPhrase.DelimitedByType.SIZE
                    ? new com.github.javaparser.ast.expr.NullLiteralExpr() : text(c.operand(by.getCharactersValueStmt()), c);
            for (ValueStmt v : sendings.getSendingValueStmts()) {
                chain = call(chain, "add", text(c.operand(v), c), delimiter.clone());
            }
        }
        return overflowPhrases(call(chain, "end"), s.getOnOverflowPhrase(), s.getNotOnOverflowPhrase(), c);
    }

    private static List<Statement> unstring(OpUnit u, TranslationContext c) throws HandlerException {
        var s = (io.proleap.cobol.asg.metamodel.procedure.unstring.UnstringStatement) u.statement();
        var sending = s.getSending();
        Operand source = c.operand(sending.getSendingCall());
        if (!(source instanceof Operand.Field sf) || !sf.alphanumeric()) {
            throw HandlerException.unsupported("UNSTRING of " + source.category() + " is not supported");
        }
        NodeList<Expression> delimiters = new NodeList<>();
        NodeList<Expression> all = new NodeList<>();
        if (sending.getDelimitedByPhrase() != null) {
            var by = sending.getDelimitedByPhrase();
            delimiters.add(text(c.operand(by.getDelimitedByValueStmt()), c));
            all.add(bool(((io.proleap.cobol.CobolParser.UnstringDelimitedByPhraseContext) by.getCtx()).ALL() != null));
            for (var or : sending.getOrAlls()) {
                delimiters.add(text(c.operand(or.getOrAllValueStmt()), c));
                all.add(bool(((io.proleap.cobol.CobolParser.UnstringOrAllPhraseContext) or.getCtx()).ALL() != null));
            }
        }
        boolean delimited = !delimiters.isEmpty();
        String u1 = c.fresh("unstring");
        List<Statement> out = new ArrayList<>();
        Expression pointer = s.getWithPointerPhrase() == null ? new com.github.javaparser.ast.expr.NullLiteralExpr()
                : integerItem(s.getWithPointerPhrase().getPointerCall(), c);
        out.add(declare(type("StringOps.Unstring"), u1, create("StringOps.Unstring", call(c.field(source), "get"), pointer,
                new com.github.javaparser.ast.expr.ArrayCreationExpr(type("String"), NodeList.nodeList(new com.github.javaparser.ast.ArrayCreationLevel()),
                        new com.github.javaparser.ast.expr.ArrayInitializerExpr(delimiters)),
                new com.github.javaparser.ast.expr.ArrayCreationExpr(Ast.booleanType(), NodeList.nodeList(new com.github.javaparser.ast.ArrayCreationLevel()),
                        new com.github.javaparser.ast.expr.ArrayInitializerExpr(all)))));
        for (var into : s.getIntoPhrase().getIntos()) {
            Operand r = c.operand(into.getIntoCall());
            if (!(r instanceof Operand.Field rf) || !rf.alphanumeric() || rf.edited()) {
                throw HandlerException.unsupported("UNSTRING INTO " + r.category() + " is not supported (only alphanumeric receivers)");
            }
            Expression field = c.field(r);
            BlockStmt then = new BlockStmt();
            then.addStatement(stmt(call(field, "set", call(name(u1), "data"))));
            if (into.getDelimiterIn() != null) {
                if (!delimited) {
                    throw HandlerException.unsupported("DELIMITER IN without DELIMITED BY");
                }
                then.addStatement(stmt(call(alphanumericTarget(into.getDelimiterIn().getDelimiterInCall(), c), "set", call(name(u1), "delimiter"))));
            }
            if (into.getCountIn() != null) {
                then.addStatement(stmt(call(integerItem(into.getCountIn().getCountInCall(), c), "move",
                        staticCall("BigDecimal", "valueOf", call(name(u1), "count")))));
            }
            Expression next = delimited ? call(name(u1), "next") : call(name(u1), "next", call(field.clone(), "length"));
            out.add(new IfStmt(next, then, null));
        }
        if (s.getTallyingPhrase() != null) {
            Expression t = integerItem(s.getTallyingPhrase().getTallyCountDataItemCall(), c);
            out.add(stmt(call(t, "store", call(call(t.clone(), "get"), "add", staticCall("BigDecimal", "valueOf", call(name(u1), "fields"))),
                    bool(false), bool(false))));
        }
        out.addAll(overflowPhrases(call(name(u1), "end"), s.getOnOverflowPhrase(), s.getNotOnOverflowPhrase(), c));
        return out;
    }

    /** BEFORE / AFTER INITIAL operands as {before, after} (null when absent). */
    private static Expression[] region(List<io.proleap.cobol.asg.metamodel.procedure.inspect.BeforeAfterPhrase> phrases,
            TranslationContext c) throws HandlerException {
        Expression before = new com.github.javaparser.ast.expr.NullLiteralExpr();
        Expression after = new com.github.javaparser.ast.expr.NullLiteralExpr();
        for (var p : phrases) {
            Expression v = text(c.operand(p.getDataItemValueStmt()), c);
            if (p.getBeforeAfterType() == io.proleap.cobol.asg.metamodel.procedure.inspect.BeforeAfterPhrase.BeforeAfterType.BEFORE) {
                before = v;
            } else {
                after = v;
            }
        }
        return new Expression[] {before, after};
    }

    /** A replacement as long as its pattern: a figurative constant is repeated to that length. */
    private static Expression replacement(ValueStmt by, Expression pattern, int patternLength, TranslationContext c) throws HandlerException {
        Operand o = c.operand(by);
        if (o instanceof Operand.Figurative f) {
            if (patternLength < 0) {
                throw HandlerException.unsupported("INSPECT REPLACING BY a figurative constant with a variable-length pattern");
            }
            return str(Compare_repeat(f.pattern(), patternLength));
        }
        return text(o, c);
    }

    private static String Compare_repeat(String pattern, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(pattern.charAt(i % pattern.length()));
        }
        return sb.toString();
    }

    private static int literalLength(ValueStmt v, TranslationContext c) throws HandlerException {
        Operand o = c.operand(v);
        return switch (o) {
            case Operand.AlphanumericLiteral a -> a.value().length();
            case Operand.Figurative f -> f.pattern().length();
            case Operand.Field f when f.refmod() == null && (f.item().isAlphanumeric() || f.item().isGroup()) -> f.item().size();
            default -> -1;
        };
    }

    private static List<Statement> inspect(OpUnit u, TranslationContext c) throws HandlerException {
        var s = (io.proleap.cobol.asg.metamodel.procedure.inspect.InspectStatement) u.statement();
        Operand item = c.operand(s.getDataItemCall());
        boolean changes = s.getInspectType() != io.proleap.cobol.asg.metamodel.procedure.inspect.InspectStatement.InspectType.TALLYING;
        Expression value;
        if (item instanceof Operand.Field f && f.alphanumeric() && !f.edited()) {
            value = call(c.field(item), "get");
        } else if (!changes && item instanceof Operand.Field f && f.numeric() && f.item().usage() == DataItem.Usage.DISPLAY) {
            value = call(c.field(item), "bytes");
        } else {
            throw HandlerException.unsupported("INSPECT " + (changes ? "REPLACING / CONVERTING " : "") + "of " + item.category() + " is not supported");
        }
        List<Statement> out = new ArrayList<>();
        switch (s.getInspectType()) {
            case TALLYING -> tallying(s.getTallying().getFors(), value, out, c);
            case REPLACING -> out.add(stmt(call(c.field(item), "set", replacing(List.of(s.getReplacing()), value, c))));
            case TALLYING_REPLACING -> {
                tallying(s.getTallyingReplacing().getFors(), value, out, c);
                out.add(stmt(call(c.field(item), "set", replacing(s.getTallyingReplacing().getReplacings(), value.clone(), c))));
            }
            case CONVERTING -> {
                var conv = s.getConverting();
                Expression[] r = region(conv.getBeforeAfterPhrases(), c);
                out.add(stmt(call(c.field(item), "set", call(create("StringOps.Inspect", value), "convert",
                        text(c.operand(conv.getFromValueStmt()), c), text(c.operand(conv.getTo().getToValueStmt()), c), r[0], r[1]))));
            }
        }
        return out;
    }

    /** A comparand with its place in the source, so the INSPECT order survives ProLeap's grouping. */
    private record Ordered(int token, ASGElement element, String kind) {}

    private static void tallying(List<io.proleap.cobol.asg.metamodel.procedure.inspect.For> fors, Expression value, List<Statement> out,
            TranslationContext c) throws HandlerException {
        List<Expression> counters = new ArrayList<>();
        List<Ordered> comparands = new ArrayList<>();
        Map<ASGElement, Integer> counterOf = new java.util.IdentityHashMap<>();
        for (var f : fors) {
            int index = counters.size();
            counters.add(integerItem(f.getTallyCountDataItemCall(), c));
            for (var ch : f.getCharacters()) {
                comparands.add(new Ordered(ch.getCtx().getStart().getTokenIndex(), ch, "CHARACTERS"));
                counterOf.put(ch, index);
            }
            for (var phrase : f.getAllLeadingPhrase()) {
                for (var al : phrase.getAllLeadings()) {
                    comparands.add(new Ordered(al.getCtx().getStart().getTokenIndex(), al, phrase.getAllLeadingsType().name()));
                    counterOf.put(al, index);
                }
            }
        }
        comparands.sort(java.util.Comparator.comparingInt(Ordered::token));
        Expression chain = create("StringOps.Inspect", value);
        for (Ordered o : comparands) {
            int counter = counterOf.get(o.element());
            if (o.element() instanceof io.proleap.cobol.asg.metamodel.procedure.inspect.Characters ch) {
                Expression[] r = region(ch.getBeforeAfterPhrases(), c);
                chain = call(chain, "tallyCharacters", integer(counter), r[0], r[1]);
            } else {
                var al = (io.proleap.cobol.asg.metamodel.procedure.inspect.AllLeading) o.element();
                Expression[] r = region(al.getBeforeAfterPhrases(), c);
                chain = call(chain, o.kind().equals("ALL") ? "tallyAll" : "tallyLeading", integer(counter),
                        text(c.operand(al.getPatternDataItemValueStmt()), c), r[0], r[1]);
            }
        }
        String counts = c.fresh("tally");
        out.add(declare(new com.github.javaparser.ast.type.ArrayType(Ast.intType()), counts, call(chain, "tally", integer(counters.size()))));
        for (int i = 0; i < counters.size(); i++) {
            Expression cnt = counters.get(i);
            out.add(stmt(call(cnt, "store", call(call(cnt.clone(), "get"), "add",
                    staticCall("BigDecimal", "valueOf", new com.github.javaparser.ast.expr.ArrayAccessExpr(name(counts), integer(i)))),
                    bool(false), bool(false))));
        }
    }

    private static Expression replacing(List<io.proleap.cobol.asg.metamodel.procedure.inspect.Replacing> replacings, Expression value,
            TranslationContext c) throws HandlerException {
        List<Ordered> comparands = new ArrayList<>();
        for (var rep : replacings) {
            for (var ch : rep.getCharacters()) {
                comparands.add(new Ordered(ch.getCtx().getStart().getTokenIndex(), ch, "CHARACTERS"));
            }
            for (var phrase : rep.getAllLeadings()) {
                for (var al : phrase.getAllLeadings()) {
                    comparands.add(new Ordered(al.getCtx().getStart().getTokenIndex(), al, phrase.getReplacingAllLeadingsType().name()));
                }
            }
        }
        comparands.sort(java.util.Comparator.comparingInt(Ordered::token));
        Expression chain = create("StringOps.Inspect", value);
        for (Ordered o : comparands) {
            if (o.element() instanceof io.proleap.cobol.asg.metamodel.procedure.inspect.ReplacingCharacters ch) {
                Expression[] r = region(ch.getBeforeAfterPhrases(), c);
                if (literalLength(ch.getBy().getByValueStmt(), c) != 1 && !(c.operand(ch.getBy().getByValueStmt()) instanceof Operand.Figurative)) {
                    throw HandlerException.unsupported("REPLACING CHARACTERS BY needs a single character");
                }
                chain = call(chain, "replaceCharacters", replacement(ch.getBy().getByValueStmt(), null, 1, c), r[0], r[1]);
            } else {
                var al = (io.proleap.cobol.asg.metamodel.procedure.inspect.ReplacingAllLeading) o.element();
                Expression[] r = region(al.getBeforeAfterPhrases(), c);
                int length = literalLength(al.getPatternDataItemValueStmt(), c);
                int byLength = literalLength(al.getBy().getByValueStmt(), c);
                if (length < 0 || (byLength >= 0 && byLength != length && !(c.operand(al.getBy().getByValueStmt()) instanceof Operand.Figurative))) {
                    throw HandlerException.unsupported("INSPECT REPLACING needs a pattern and replacement of the same, known length");
                }
                String method = switch (o.kind()) {
                    case "ALL" -> "replaceAll";
                    case "LEADING" -> "replaceLeading";
                    default -> "replaceFirst";
                };
                chain = call(chain, method, text(c.operand(al.getPatternDataItemValueStmt()), c),
                        replacement(al.getBy().getByValueStmt(), null, length, c), r[0], r[1]);
            }
        }
        return call(chain, "replace");
    }
}
