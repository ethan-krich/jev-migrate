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
import java.math.BigDecimal;
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
                "MOVE into an alphanumeric receiver (PIC X, not edited): left-justify, space-pad and right-truncate",
                Builtins::moveToAlphanumeric));
        register(Handler.of("move_to_numeric", "MOVE",
                "MOVE into a numeric receiver (PIC 9, signed or unsigned, with or without decimal places; not edited): "
                        + "decimal alignment with high- and low-order truncation; alphanumeric data converted as libcob does",
                Builtins::moveToNumeric));
        register(Handler.of("add", "ADD", "ADD ... TO / GIVING with exact decimal arithmetic", Builtins::add));
        register(Handler.of("subtract", "SUBTRACT", "SUBTRACT ... FROM / GIVING with exact decimal arithmetic", Builtins::subtract));
        register(Handler.of("multiply", "MULTIPLY", "MULTIPLY ... BY / GIVING with exact decimal arithmetic", Builtins::multiply));
        register(Handler.of("divide", "DIVIDE", "DIVIDE ... INTO / BY / GIVING, truncated or ROUNDED at the receiver scale", Builtins::divide));
        register(Handler.of("compute", "COMPUTE", "COMPUTE with +, -, * and unary minus (exact)", Builtins::compute));
        register(Handler.of("compute_gnucobol", "COMPUTE",
                "COMPUTE with / and integer ** as GnuCOBOL 3.2 computes them (quotients keep 38 extra digits, truncated)",
                Builtins::computeGnucobol));
        register(Handler.of("display", "DISPLAY", "DISPLAY of literals and items, each item in its GnuCOBOL DISPLAY form", Builtins::display));
        register(Handler.of("if", "IF", "IF / ELSE with relation, sign and combined conditions", Builtins::ifElse));
        register(Handler.of("perform", "PERFORM", "PERFORM paragraph/section or inline; TIMES, UNTIL, single VARYING", Builtins::perform));
        register(Handler.of("stop_literal_accept", "STOP",
                "STOP literal as GnuCOBOL runs it: the literal is displayed on standard output, then one line of standard input "
                        + "is read (the operator's reply) and the program continues", Builtins::stopLiteralAccept));
        register(Handler.of("stop_literal_continue", "STOP",
                "STOP literal shown on standard output, then the program continues without reading a reply "
                        + "(correct only if nothing must wait for an operator)", Builtins::stopLiteralContinue));
        register(Handler.of("display_printer", "DISPLAY",
                "DISPLAY UPON PRINTER as GnuCOBOL runs it: appended to the file COB_DISPLAY_PRINT_FILE names, else standard output",
                Builtins::displayPrinter));
        register(Handler.of("display_device_stdout", "DISPLAY",
                "DISPLAY UPON PRINTER / SYSPCH written to standard output (correct if the device is the job's output)",
                Builtins::displayDeviceStdout));
        register(Handler.of("display_device_stderr", "DISPLAY",
                "DISPLAY UPON PRINTER / SYSPCH written to standard error (correct if the device is the job's error log)",
                Builtins::displayDeviceStderr));
        register(Handler.of("stop_run", "STOP", "STOP RUN ends the run unit with RETURN-CODE as exit status", Builtins::stopRun));
        register(Handler.of("goback", "GOBACK", "GOBACK returns to the caller, or ends the run unit", (u, c) ->
                List.of(stmt(staticCall("ProgramSupport", "goback", self())))));
        register(Handler.of("exit", "EXIT", "EXIT (no-op) and EXIT PROGRAM", Builtins::exit));
        register(Handler.of("continue", "CONTINUE", "CONTINUE (no-op)", (u, c) -> List.of(noOp("CONTINUE"))));
        register(Handler.of("call_program", "CALL",
                "CALL 'literal' of a program in the project, or CALL identifier (Dialect.dynamic_calls), [USING] [ON EXCEPTION]",
                Builtins::callProgram));
        register(Handler.of("call_external", "CALL",
                "candidate only: CALL of a program outside the project, bound through its target and args slots",
                (u, c) -> {
                    throw HandlerException.notApplicable("call_external is offered only for a CALL of a program outside the project");
                }));
        register(Handler.of("entry", "ENTRY", "ENTRY reached in the flow of the program (no operation; CALL starts there)",
                (u, c) -> List.of(noOp("ENTRY"))));
        register(Handler.of("cancel_program", "CANCEL", "CANCEL 'literal' / identifier: the program's next CALL starts it afresh",
                Builtins::cancelProgram));
        register(Handler.of("set_condition", "SET",
                "SET condition-name TO TRUE (moves its first value), or TO FALSE with WHEN SET TO FALSE", Builtins::setCondition));
        register(Handler.of("evaluate", "EVALUATE",
                "EVALUATE with ALSO, THRU, ANY, TRUE/FALSE and OTHER as an if/else chain", Builtins::evaluate));
        register(Handler.of("accept_stdin", "ACCEPT",
                "ACCEPT an alphanumeric item from standard input (one line; spaces at end of input)", Builtins::acceptStdin));
        register(Handler.of("accept_datetime", "ACCEPT",
                "ACCEPT FROM DATE [YYYYMMDD] / DAY [YYYYDDD] / DAY-OF-WEEK / TIME / MICROSECOND-TIME via the runtime Clock", Builtins::acceptDatetime));
        register(Handler.of("accept_environment", "ACCEPT",
                "ACCEPT FROM ENVIRONMENT-VALUE / ENVIRONMENT name / ARGUMENT-NUMBER / ARGUMENT-VALUE / COMMAND-LINE [ON EXCEPTION]",
                Builtins::acceptEnvironment));
        register(Handler.of("exhibit", "EXHIBIT", "EXHIBIT [NAMED] as GnuCOBOL: NAME = value for items", Builtins::exhibit));
        register(Handler.of("move_to_edited", "MOVE",
                "MOVE into a numeric-edited (PICTURE editing) or alphanumeric-edited (insertion) item", Builtins::moveToEdited));
        register(Handler.of("move_from_edited", "MOVE",
                "MOVE of a numeric-edited item to a numeric item: the value is de-edited", Builtins::moveFromEdited));
        register(Handler.of("string", "STRING",
                "STRING ... DELIMITED BY ... INTO [WITH POINTER] [ON OVERFLOW]", Builtins::stringStatement));
        register(Handler.of("unstring", "UNSTRING",
                "UNSTRING [DELIMITED BY [ALL] ... OR ...] INTO alphanumeric or numeric items [DELIMITER IN] [COUNT IN] [POINTER] [TALLYING]",
                Builtins::unstring));
        register(Handler.of("inspect", "INSPECT",
                "INSPECT TALLYING / REPLACING / CONVERTING with BEFORE / AFTER INITIAL", Builtins::inspect));
        register(Handler.of("go_to", "GO_TO", "GO TO procedure and GO TO ... DEPENDING ON (procedure dispatcher)", Builtins::goTo));
        register(Handler.of("alter", "ALTER",
                "ALTER paragraph TO [PROCEED TO] procedure: sets the GO TO target of the altered paragraph (procedure dispatcher)",
                Builtins::alter));
        register(Handler.of("open_file", "OPEN", "OPEN INPUT / OUTPUT / I-O / EXTEND of SEQUENTIAL, LINE SEQUENTIAL, RELATIVE and INDEXED files",
                Builtins::openFile));
        register(Handler.of("close_file", "CLOSE", "CLOSE files", Builtins::closeFile));
        register(Handler.of("read_file", "READ",
                "READ [NEXT / PREVIOUS] [INTO] [AT END], or READ by key [KEY IS] [INVALID KEY] of RELATIVE and INDEXED files", Builtins::readFile));
        register(Handler.of("write_file", "WRITE",
                "WRITE record [FROM] [BEFORE / AFTER ADVANCING n LINES / PAGE] [AT END-OF-PAGE] [INVALID KEY]", Builtins::writeFile));
        register(Handler.of("read_file_unlocked", "READ",
                "READ ... WITH LOCK / NO LOCK / WAIT as a READ without the lock phrase (correct when no other process uses the file "
                        + "at the same time: within one run unit record locks have no effect)", Builtins::readFileUnlocked));
        register(Handler.of("rewrite_file", "REWRITE", "REWRITE record [FROM] [INVALID KEY] of a file opened I-O", Builtins::rewriteFile));
        register(Handler.of("delete_file", "DELETE", "DELETE file RECORD [INVALID KEY] of RELATIVE and INDEXED files", Builtins::deleteFile));
        register(Handler.of("start_file", "START", "START file [KEY IS =, >, >=, <, <= item] [INVALID KEY] of RELATIVE and INDEXED files",
                Builtins::startFile));
        register(Handler.of("sort", "SORT",
                "SORT [WITH DUPLICATES IN ORDER] as a stable sort (GnuCOBOL returns equal keys in release order either way), "
                        + "USING / INPUT PROCEDURE, GIVING / OUTPUT PROCEDURE", Builtins::sort));
        register(Handler.of("merge", "MERGE", "MERGE ... USING files in key order, GIVING / OUTPUT PROCEDURE", Builtins::merge));
        register(Handler.of("release", "RELEASE", "RELEASE sort record [FROM]", Builtins::release));
        register(Handler.of("return_record", "RETURN", "RETURN sort file [INTO] AT END", Builtins::returnRecord));
        register(Handler.of("move_group", "MOVE",
                "MOVE into a group item: the sending bytes as alphanumeric data, space-padded or cut", Builtins::moveToGroup));
        register(Handler.of("initialize", "INITIALIZE",
                "INITIALIZE [WITH FILLER] [ALL TO VALUE] [REPLACING ...] [THEN TO DEFAULT] item by item", Builtins::initialize));
        register(Handler.of("set_switch", "SET", "SET switch-name TO ON / OFF (SPECIAL-NAMES SWITCH-0 .. SWITCH-36)", Builtins::setSwitch));
        register(Handler.of("set_index", "SET", "SET index TO / UP BY / DOWN BY, SET item TO index", Builtins::setIndex));
        register(Handler.of("search", "SEARCH", "Serial SEARCH [VARYING] with AT END and WHEN", Builtins::search));
        register(Handler.of("search_all", "SEARCH",
                "SEARCH ALL ... WHEN key = value [AT END] as GnuCOBOL's binary search, whatever order the keys are in",
                Builtins::searchAll));

        // JDK twins: the same statement with plain JDK calls instead of the runtime library. A
        // twin is offered next to the handler it shadows, as a runtime_choice decision, only
        // when the run asks for it (options.jdk_alternatives); no default rule selects one.
        String doubles = "approximate: binary floating point can be off in the last digits "
                + "(0.1 + 0.2 is 0.30000000000000004, which a PIC 9V99 receiver truncates to 0.30, and 0.29 * 100 is "
                + "28.999999999999996, which a PIC 999 receiver truncates to 28), and more than 15 significant digits are lost";
        twin("add", "ADD ... TO / GIVING in Java double arithmetic; the receiver's PICTURE still truncates the result, "
                + "or ROUNDED rounds it, and SIZE ERROR is still detected; " + doubles);
        twin("subtract", "SUBTRACT ... FROM / GIVING in Java double arithmetic; the receiver's PICTURE still truncates the "
                + "result, or ROUNDED rounds it, and SIZE ERROR is still detected; " + doubles);
        twin("multiply", "MULTIPLY ... BY / GIVING in Java double arithmetic; the receiver's PICTURE still truncates the "
                + "result, or ROUNDED rounds it, and SIZE ERROR is still detected; " + doubles);
        twin("divide", "DIVIDE ... INTO / BY / GIVING (not REMAINDER) in Java double arithmetic; the receiver's PICTURE "
                + "still truncates the quotient, or ROUNDED rounds it; " + doubles + "; division by zero gives Infinity, "
                + "which stops the program with a NumberFormatException instead of raising SIZE ERROR");
        twin("compute_gnucobol", "COMPUTE in Java double arithmetic: + - * /, ** of any exponent (Math.pow), and "
                + "FUNCTION SQRT, LOG, LOG10, EXP, EXP10, SIN, COS, TAN, ASIN, ACOS, ATAN, PI, E, RANDOM, ABS, MAX, MIN, "
                + "MOD, REM, INTEGER, INTEGER-PART, SUM, MEAN with java.lang.Math; the receiver's PICTURE still truncates the "
                + "result, or ROUNDED rounds it; " + doubles + "; GnuCOBOL's own floating-point functions differ in the "
                + "last digits too; division by zero stops the program with a NumberFormatException instead of SIZE ERROR");
        twin("display", "DISPLAY with System.out / System.err print / println of the operands' DISPLAY text; "
                + "approximate only for characters outside ASCII when no runtime DISPLAY ran before it (the JVM's default "
                + "charset is used instead of one byte per character)");
        twin("if", "IF / ELSE with plain JDK comparisons: numeric operands as Java doubles (<, ==, Math.signum), "
                + "alphanumeric ones with String.equals / compareTo, class tests with Character methods; approximate: "
                + "strings of different lengths are compared without padding the shorter one with spaces (\"AB\" < \"AB \" "
                + "in Java, equal in COBOL), the class tests accept any Unicode letter or digit, and numeric values with more "
                + "than 15 significant digits lose precision");
        twin("string", "STRING ... DELIMITED BY SIZE / literal ... INTO with a StringBuilder (no POINTER, no OVERFLOW); "
                + "approximate: the result replaces the whole receiver (space-padded or cut) where COBOL leaves the "
                + "receiver's characters after the last one written unchanged");
        twin("unstring", "UNSTRING ... DELIMITED BY [ALL] literal [OR ...] INTO items with String.split (no DELIMITER IN, "
                + "COUNT IN, POINTER, TALLYING or OVERFLOW); approximate: a numeric receiver takes "
                + "new BigDecimal of the trimmed field, which fails with NumberFormatException where COBOL moves the "
                + "characters as digits");
        twin("inspect", "INSPECT TALLYING / REPLACING ALL, LEADING, FIRST or CHARACTERS (no BEFORE / AFTER INITIAL, no "
                + "CONVERTING) with String.replace / regionMatches and regular expressions; approximate: several "
                + "TALLYING or REPLACING phrases are applied one after the other where COBOL scans the item once for all "
                + "of them at each position");
    }

    private void register(Handler h) {
        handlers.put(h.id(), h);
    }

    /** Runtime handler id to its JDK twin. */
    private final Map<String, Handler> twins = new LinkedHashMap<>();
    private final Set<Handler> jdkHandlers = new java.util.HashSet<>();

    /** Registers {@code <base>_jdk}: {@code base}'s generator, run in {@link TranslationContext#jdk} mode. */
    private void twin(String base, String description) {
        Handler b = handlers.get(base);
        Handler t = Handler.of(base + "_jdk", b.operation(), description, b::generate);
        register(t);
        twins.put(base, t);
        jdkHandlers.add(t);
    }

    /** The JDK twin of a runtime handler, or null when it has none. */
    public Handler jdkTwin(Handler runtime) {
        return runtime == null ? null : twins.get(runtime.id());
    }

    /** Whether {@code h} is a JDK twin (it generates in {@link TranslationContext#jdk} mode). */
    public boolean isJdk(Handler h) {
        return jdkHandlers.contains(h);
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
        Operand src = moveSource(u, c);
        Statement forward = overlappingCopy(c.field(target), t, src, u, c);
        return List.of(forward != null ? forward : storeAlphanumeric(c.field(target), src, c));
    }

    /**
     * A MOVE whose receiver may start inside its sender depends on the copy direction, which
     * Dialect.move_overlap settles. With "ibm" the bytes are copied left to right where cobc's
     * -fmove-ibm does it (cob_move_ibm: alphanumeric sender and receiver of fixed sizes, the sender
     * at least as long, the receiver not JUSTIFIED); anything else, and "temporary", is the
     * ordinary store, which reads the sender before it writes (GnuCOBOL's memmove). Null for the
     * ordinary store.
     */
    private static Statement overlappingCopy(Expression field, Operand.Field target, Operand src, OpUnit u, TranslationContext c)
            throws HandlerException {
        if (!(src instanceof Operand.Field sf) || !sf.alphanumeric() || sf.edited() || !c.receiverInsideSender(src, target)) {
            return null;
        }
        String mode = c.require("move_overlap", c.dialect().moveOverlap(), u.statement().getCtx(),
                "a MOVE whose receiver may start inside its sender");
        var srcSize = c.fixedSize(sf);
        var dstSize = c.fixedSize(target);
        if (!mode.equals("ibm") || srcSize.isEmpty() || dstSize.isEmpty() || srcSize.getAsInt() < dstSize.getAsInt()
                || target.item().justified()) {
            return null;
        }
        return stmt(call(field, "copyForward", c.field(src)));
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
        Statement forward = overlappingCopy(field, (Operand.Field) target, src, u, c);
        if (forward != null) {
            return List.of(forward);
        }
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
            case Operand.Computed v when !v.numeric() -> v.value();
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
        Operand src = moveSource(u, c);
        if (target instanceof Operand.Field t && !src.isNumericValued()) {
            Statement s = alphanumericToNumeric(c.field(target), t, src, c);
            if (s != null) {
                return List.of(s);
            }
        }
        return List.of(storeNumeric(c.field(target), src, c));
    }

    /**
     * MOVE of alphanumeric data into numeric item {@code target} as GnuCOBOL 3.2 compiles it, or
     * null when that is not reproduced (the caller refuses it). An alphanumeric item or reference
     * modification moves through libcob's cob_move_alphanum_to_display. cobc copies an
     * alphanumeric literal into a DISPLAY item without decimals or separate sign as written,
     * right-aligned (cb_build_move_literal); into other DISPLAY items it moves like an item.
     * {@code ALL "digits"} moves the 38 repeated digits libcob builds (cob_move_all).
     */
    static Statement alphanumericToNumeric(Expression field, Operand.Field target, Operand src, TranslationContext c)
            throws HandlerException {
        DataItem t = target.item();
        boolean zoned = t.usage() == DataItem.Usage.DISPLAY;
        if (!takesAlphanumeric(target)) {
            return null;
        }
        return switch (src) {
            case Operand.Field f when f.alphanumeric() && !f.edited() && (f.refmod() != null || !f.item().isGroup()) ->
                    moveAlphanumeric(field, call(c.field(src), "get"), c);
            case Operand.AlphanumericLiteral a when a.value().isEmpty() || a.value().chars().allMatch(ch -> ch == ' ') -> null;
            case Operand.AlphanumericLiteral a when zoned && t.numeric().scale() == 0 && !t.signSeparate() -> {
                int digits = t.numeric().digits();
                String lit = a.value();
                String bytes = lit.length() >= digits ? lit.substring(lit.length() - digits) : "0".repeat(digits - lit.length()) + lit;
                yield stmt(call(call(field, "view"), "set", str(bytes)));
            }
            case Operand.AlphanumericLiteral a when zoned -> moveAlphanumeric(field, str(a.value()), c);
            case Operand.Figurative f when f.name().equals("ALL") && f.pattern().chars().allMatch(ch -> ch >= '0' && ch <= '9') ->
                    stmt(call(field, "move", staticCall("NumericField", "all", str(f.pattern()))));
            default -> null;
        };
    }

    /** A numeric receiver of an alphanumeric MOVE: not BLANK WHEN ZERO, no P scaling in a DISPLAY item. */
    private static boolean takesAlphanumeric(Operand.Field target) {
        DataItem t = target.item();
        return target.numeric() && !t.blankWhenZero() && (t.usage() != DataItem.Usage.DISPLAY
                || (t.numeric().scale() >= 0 && t.numeric().scale() <= t.numeric().digits()));
    }

    private static Statement moveAlphanumeric(Expression field, Expression bytes, TranslationContext c) {
        boolean comma = c.decimalPointComma();
        return stmt(call(field, "moveAlphanumeric", bytes, chr(comma ? ',' : '.'), chr(comma ? '.' : ',')));
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
        if (c.jdk()) {
            // A double result is stored through its decimal value (BigDecimal.valueOf: Double.toString's digits).
            Function<Receiver, Expression> computed = value;
            value = r -> staticCall("BigDecimal", "valueOf", computed.apply(r));
        }
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
        out.add(declare(c.jdk() ? com.github.javaparser.ast.type.PrimitiveType.doubleType() : type("BigDecimal"), var, e));
        return name(var);
    }

    private static Expression sum(List<Expression> operands, TranslationContext c) {
        if (c.jdk()) {
            Expression e = operands.getFirst();
            for (int i = 1; i < operands.size(); i++) {
                e = Ast.binary(e, BinaryExpr.Operator.PLUS, operands.get(i));
            }
            return e;
        }
        return operands.size() == 1 ? operands.getFirst()
                : staticCall("Arithmetic", "sum", operands.toArray(Expression[]::new));
    }

    /** A receiver's current value: its BigDecimal, or its double in JDK mode. */
    private static Expression current(Receiver r, TranslationContext c) {
        return c.jdk() ? call(call(r.field(), "get"), "doubleValue") : call(r.field(), "get");
    }

    /** {@code a.add(b)} / {@code subtract} / {@code multiply}, or {@code a + b} / {@code -} / {@code *} in JDK mode. */
    private static Expression arith(Expression a, String op, Expression b, TranslationContext c) {
        if (!c.jdk()) {
            return call(a, op, b);
        }
        return Ast.binary(a, switch (op) {
            case "add" -> BinaryExpr.Operator.PLUS;
            case "subtract" -> BinaryExpr.Operator.MINUS;
            case "multiply" -> BinaryExpr.Operator.MULTIPLY;
            default -> throw new IllegalArgumentException(op);
        }, b);
    }

    /** DIVIDE's quotient at the receiver's scale ({@code Arithmetic.divide}), or {@code a / b} in JDK mode. */
    private static Expression quotient(Expression dividend, Expression divisor, Receiver r, TranslationContext c) {
        return c.jdk() ? Ast.binary(dividend, BinaryExpr.Operator.DIVIDE, divisor)
                : staticCall("Arithmetic", "divide", dividend, divisor, integer(r.scale()), bool(r.rounded()));
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
                Expression addend = once(sum(ops, c), rs.size(), out, c, "addend");
                out.addAll(storeAll(rs, r -> arith(current(r, c), "add", addend.clone(), c),
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
                Expression total = once(sum(ops, c), rs.size(), out, c, "sum");
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
                Expression subtrahend = once(sum(ops, c), rs.size(), out, c, "subtrahend");
                out.addAll(storeAll(rs, r -> arith(current(r, c), "subtract", subtrahend.clone(), c),
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
                Expression difference = once(arith(minuend, "subtract", sum(ops, c), c), rs.size(), out, c, "difference");
                out.addAll(storeAll(rs, r -> difference.clone(), sub.getOnSizeErrorPhrase(), sub.getNotOnSizeErrorPhrase(), c));
            }
            case CORRESPONDING -> out.add(corresponding(u, c, "subtract"));
        }
        return out;
    }

    /** One pair of ADD / SUBTRACT CORRESPONDING: target = target op source. */
    private static Statement corresponding(OpUnit u, TranslationContext c, String op) throws HandlerException {
        if (c.jdk()) {
            throw HandlerException.unsupported("CORRESPONDING has no JDK form");
        }
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
            out.addAll(storeAll(rs, r -> arith(factor.clone(), "multiply", current(r, c), c),
                    mul.getOnSizeErrorPhrase(), mul.getNotOnSizeErrorPhrase(), c));
        } else {
            var giving = mul.getGivingPhrase();
            Expression left = numericOperand(mul.getOperandValueStmt(), c);
            Expression right = numericOperand(giving.getGivingOperand().getOperandValueStmt(), c);
            for (var res : giving.getGivingResults()) {
                rs.add(receiver(res.getResultCall(), res, c));
            }
            Expression product = once(arith(left, "multiply", right, c), rs.size(), out, c, "product");
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
                out.addAll(storeAll(rs, r -> quotient(current(r, c), divisor.clone(), r, c),
                        div.getOnSizeErrorPhrase(), div.getNotOnSizeErrorPhrase(), c));
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
                out.addAll(storeAll(rs, r -> quotient(dividend.clone(), divisor.clone(), r, c),
                        div.getOnSizeErrorPhrase(), div.getNotOnSizeErrorPhrase(), c));
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
        if (c.jdk()) {
            throw HandlerException.unsupported("DIVIDE ... REMAINDER has no JDK form");
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

    private static List<Statement> computeGnucobol(OpUnit u, TranslationContext c) throws HandlerException {
        ComputeStatement comp = (ComputeStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        List<Receiver> rs = new ArrayList<>();
        for (var store : comp.getStores()) {
            rs.add(receiver(store.getStoreCall(), store, c));
        }
        Expression value = once(c.gnuArithmetic(new Operand.Arithmetic(comp.getArithmeticExpression())), rs.size(), out, c, "result");
        out.addAll(storeAll(rs, r -> value.clone(), comp.getOnSizeErrorPhrase(), comp.getNotOnSizeErrorPhrase(), c));
        return out;
    }

    // ------------------------------------------------------------- DISPLAY

    /** Where a DISPLAY writes. */
    private enum Output { STDOUT, STDERR, PRINTER }

    private static List<Statement> display(OpUnit u, TranslationContext c) throws HandlerException {
        DisplayStatement d = (DisplayStatement) u.statement();
        String device = d.getUpon() == null ? "SYSOUT" : c.device(deviceName(d.getUpon()));
        if (device.equals("ENVIRONMENT-NAME") || device.equals("ENVIRONMENT-VALUE") || device.equals("ARGUMENT-NUMBER")) {
            return displayEnvironment(d, device, c);
        }
        Output output = switch (device) {
            // cobc's device names for standard output and standard error.
            case "CONSOLE", "SYSOUT", "SYSLIST", "SYSLST", "STDOUT", "PRINT", "TERMINAL", "ALTERNATE-CONSOLE" -> Output.STDOUT;
            case "SYSERR", "STDERR" -> Output.STDERR;
            // GnuCOBOL sends these to files named by the environment; the target system decides what they are.
            case "PRINTER", "PRINTER-1", "SYSPCH", "SYSPUNCH" -> throw HandlerException.missingTarget("DISPLAY UPON " + device
                    + ": the target system decides where the " + (device.startsWith("SYSP") ? "punch" : "printer") + " output goes", true);
            default -> throw new HandlerException(HandlerException.Kind.MISSING_TARGET,
                    "DISPLAY UPON " + device + " (not an output device of GnuCOBOL) has no target");
        };
        return displayTo(d, output, c);
    }

    /** DISPLAY written to {@code output}. */
    private static List<Statement> displayTo(DisplayStatement d, Output output, TranslationContext c) throws HandlerException {
        if (d.getAt() != null || d.getOnExceptionClause() != null || d.getNotOnExceptionClause() != null) {
            throw HandlerException.unsupported("DISPLAY with AT or exception phrases is not supported");
        }
        NodeList<Expression> args = new NodeList<>();
        for (var operand : d.getOperands()) {
            args.add(displayValue(c.operand(operand.getOperandValueStmt()), c));
        }
        boolean noAdvancing = d.getWith() != null;
        if (c.jdk()) {
            if (output == Output.PRINTER) {
                throw HandlerException.unsupported("DISPLAY UPON PRINTER has no JDK form");
            }
            if (args.isEmpty()) {
                throw HandlerException.unsupported("DISPLAY without operands has no JDK form");
            }
            Expression text = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                text = Ast.binary(text, BinaryExpr.Operator.PLUS, args.get(i));
            }
            var stream = new com.github.javaparser.ast.expr.FieldAccessExpr(name("System"), output == Output.STDERR ? "err" : "out");
            return List.of(stmt(call(stream, noAdvancing ? "print" : "println", text)));
        }
        if (output == Output.PRINTER) {
            args.addFirst(bool(!noAdvancing));
            return List.of(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(name("Display"), "printer", args)));
        }
        String method = output == Output.STDERR ? (noAdvancing ? "errNoAdvancing" : "err") : (noAdvancing ? "noAdvancing" : "line");
        return List.of(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(name("Display"), method, args)));
    }

    /** The device of DISPLAY UPON PRINTER / SYSPCH (candidates only), or null for another DISPLAY. */
    private static String printDevice(OpUnit u, TranslationContext c) {
        DisplayStatement d = (DisplayStatement) u.statement();
        String device = d.getUpon() == null ? null : c.device(deviceName(d.getUpon()));
        return device != null && List.of("PRINTER", "PRINTER-1", "SYSPCH", "SYSPUNCH").contains(device) ? device : null;
    }

    /** Candidate for DISPLAY UPON PRINTER: GnuCOBOL's print file (COB_DISPLAY_PRINT_FILE), else standard output. */
    private static List<Statement> displayPrinter(OpUnit u, TranslationContext c) throws HandlerException {
        String device = printDevice(u, c);
        if (device == null || device.startsWith("SYSP")) {
            throw HandlerException.notApplicable("not DISPLAY UPON PRINTER");
        }
        return displayTo((DisplayStatement) u.statement(), Output.PRINTER, c);
    }

    /** Candidate for DISPLAY UPON PRINTER / SYSPCH: the device is the job's standard output. */
    private static List<Statement> displayDeviceStdout(OpUnit u, TranslationContext c) throws HandlerException {
        if (printDevice(u, c) == null) {
            throw HandlerException.notApplicable("not DISPLAY UPON PRINTER / SYSPCH");
        }
        return displayTo((DisplayStatement) u.statement(), Output.STDOUT, c);
    }

    /** Candidate for DISPLAY UPON PRINTER / SYSPCH: the device is the job's standard error. */
    private static List<Statement> displayDeviceStderr(OpUnit u, TranslationContext c) throws HandlerException {
        if (printDevice(u, c) == null) {
            throw HandlerException.notApplicable("not DISPLAY UPON PRINTER / SYSPCH");
        }
        return displayTo((DisplayStatement) u.statement(), Output.STDERR, c);
    }

    /** The DISPLAY text of an operand. */
    /** DISPLAY ... UPON ENVIRONMENT-NAME / ENVIRONMENT-VALUE (an alphanumeric value) or ARGUMENT-NUMBER (a number). */
    private static List<Statement> displayEnvironment(DisplayStatement d, String device, TranslationContext c) throws HandlerException {
        if (d.getOperands().size() != 1 || d.getWith() != null) {
            throw HandlerException.unsupported("DISPLAY UPON " + device + " takes one value");
        }
        Operand o = c.operand(d.getOperands().getFirst().getOperandValueStmt());
        if (device.equals("ARGUMENT-NUMBER")) {
            if (!o.isNumericValued() || o instanceof Operand.Arithmetic) {
                throw HandlerException.unsupported("DISPLAY " + o.category() + " UPON ARGUMENT-NUMBER");
            }
            return List.of(stmt(staticCall("Environment", "argumentNumber", call(c.numeric(o), "intValue"))));
        }
        Expression value = switch (o) {
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.Field f when f.alphanumeric() && !f.edited() -> call(c.field(o), "get");
            default -> throw HandlerException.unsupported("DISPLAY " + o.category() + " UPON " + device);
        };
        return List.of(stmt(staticCall("Environment", device.equals("ENVIRONMENT-NAME") ? "name" : "value", value)));
    }

    private static Expression displayValue(Operand o, TranslationContext c) throws HandlerException {
        return switch (o) {
            case Operand.Field f when f.numeric() && c.decimalPointComma() && f.item().numeric().scale() > 0 ->
                    call(c.field(o), "display", chr(','));
            case Operand.Field f when f.alphanumeric() || f.numeric() -> call(c.field(o), "display");
            case Operand.ReturnCode r -> call(c.field(o), "display");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.NumericLiteral n -> str(n.displayText());
            case Operand.Figurative f when !f.name().equals("ALL") -> str(f.pattern());
            case Operand.Computed v when v.shown() != null -> v.shown();
            case Operand.Computed v when "text".equals(v.display()) -> v.value();
            case Operand.Computed v when "double".equals(v.display()) -> staticCall("String", "valueOf", v.value());
            case Operand.Computed v when "decimal".equals(v.display()) ->
                    staticCall("Functions", "decimalDisplay", v.value(), chr(c.decimalPointComma() ? ',' : '.'));
            case Operand.Computed v when "integer".equals(v.display()) -> staticCall("Functions", "integerDisplay", v.value());
            case Operand.Computed v when v.display() != null && v.display().startsWith("digits") ->
                    staticCall("Functions", "digits", v.value(), integer(Integer.parseInt(v.display().substring(6))));
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
        if (((io.proleap.cobol.CobolParser.AcceptStatementContext) a.getCtx()).ENVIRONMENT() != null) {
            // ACCEPT ... FROM ENVIRONMENT "name" has no FROM type in the ASG, but it is not standard input.
            throw HandlerException.notApplicable("ACCEPT FROM ENVIRONMENT is not standard input");
        }
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
        boolean micro = a.getAcceptType() == AcceptStatement.AcceptType.MNEMONIC
                && c.device(deviceName(a.getAcceptFromMnemonicStatement())).equals("MICROSECOND-TIME");
        String from = micro ? "microsecond_time" : Facts.acceptFrom(a);
        if (a.getAcceptType() != AcceptStatement.AcceptType.DATE && !micro) {
            throw HandlerException.notApplicable("not ACCEPT FROM DATE / DAY / DAY-OF-WEEK / TIME / MICROSECOND-TIME");
        }
        String method = switch (from) {
            case "microsecond_time" -> "microsecondTime";
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

    /**
     * ACCEPT of an environment variable or of the command line, as GnuCOBOL gives them: moved as
     * alphanumeric data; a variable that is not set (or no further argument) raises the exception
     * (ON EXCEPTION), and an unset variable moves spaces.
     */
    private static List<Statement> acceptEnvironment(OpUnit u, TranslationContext c) throws HandlerException {
        AcceptStatement a = (AcceptStatement) u.statement();
        var ctx = (io.proleap.cobol.CobolParser.AcceptStatementContext) a.getCtx();
        String from;
        Expression variable = null;
        if (ctx.ENVIRONMENT() != null) {
            from = "ENVIRONMENT";
            if (ctx.literal() == null) {
                throw HandlerException.unsupported("ACCEPT ... FROM ENVIRONMENT item (only a literal name)");
            }
            variable = str(literalText(ctx.literal()));
        } else if (a.getAcceptType() == AcceptStatement.AcceptType.MNEMONIC) {
            from = c.device(deviceName(a.getAcceptFromMnemonicStatement()));
        } else {
            throw HandlerException.notApplicable("not ACCEPT FROM the environment or command line");
        }
        Operand target = c.operand(a.getAcceptCall());
        Expression value;
        boolean canFail;
        switch (from) {
            case "ENVIRONMENT" -> {
                value = staticCall("Environment", "get", variable);
                canFail = true;
            }
            case "ENVIRONMENT-VALUE" -> {
                value = staticCall("Environment", "value");
                canFail = true;
            }
            case "ARGUMENT-VALUE" -> {
                value = staticCall("Environment", "nextArgument");
                canFail = true;
            }
            case "COMMAND-LINE" -> {
                value = staticCall("Environment", "commandLine");
                canFail = false;
            }
            case "ARGUMENT-NUMBER" -> {
                if (!(target instanceof Operand.Field f && f.numeric())) {
                    throw HandlerException.unsupported("ACCEPT ... FROM ARGUMENT-NUMBER into " + target.category());
                }
                List<Statement> out = new ArrayList<>();
                out.add(stmt(call(c.field(target), "move", staticCall("BigDecimal", "valueOf", staticCall("Environment", "argumentCount")))));
                if (a.getNotOnExceptionClause() != null) {
                    out.add(c.nested(a.getNotOnExceptionClause()));
                }
                return out;
            }
            default -> throw HandlerException.notApplicable("ACCEPT FROM " + from + " is not the environment or command line");
        }
        if (!(target instanceof Operand.Field f) || !f.alphanumeric() || f.edited()) {
            throw HandlerException.unsupported("ACCEPT ... FROM " + from + " into " + target.category() + " (only alphanumeric items)");
        }
        String v = c.fresh("value");
        List<Statement> out = new ArrayList<>();
        out.add(declare(type("String"), v, value));
        Expression receiver = c.receiving(target);
        if (!canFail) {
            out.add(stmt(call(receiver, "set", name(v))));
            if (a.getNotOnExceptionClause() != null) {
                out.add(c.nested(a.getNotOnExceptionClause()));
            }
            return out;
        }
        // ARGUMENT-VALUE leaves the item unchanged after the last argument; an unset variable moves spaces.
        BlockStmt missing = from.equals("ARGUMENT-VALUE") ? new BlockStmt() : new BlockStmt(NodeList.nodeList(stmt(call(receiver.clone(), "set", str(" ")))));
        if (a.getOnExceptionClause() != null) {
            missing.getStatements().addAll(c.nested(a.getOnExceptionClause()).getStatements());
        }
        BlockStmt found = new BlockStmt(NodeList.nodeList(stmt(call(receiver.clone(), "set", name(v)))));
        if (a.getNotOnExceptionClause() != null) {
            found.getStatements().addAll(c.nested(a.getNotOnExceptionClause()).getStatements());
        }
        out.add(new IfStmt(new BinaryExpr(name(v), new com.github.javaparser.ast.expr.NullLiteralExpr(), BinaryExpr.Operator.EQUALS),
                missing, found));
        return out;
    }

    private static String literalText(io.proleap.cobol.CobolParser.LiteralContext lit) throws HandlerException {
        if (lit.NONNUMERICLITERAL() == null) {
            throw HandlerException.unsupported("ACCEPT ... FROM ENVIRONMENT " + lit.getText());
        }
        String t = lit.getText();
        return t.substring(1, t.length() - 1);
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
                Operand parent = new Operand.Field(cond.parent(), f.subscripts(), null);
                if (cond.parent().isGroup()) {
                    Expression field = c.receiving(parent);
                    out.add(v instanceof Operand.Figurative fig ? stmt(call(field, "fill", fig.isSingle() ? chr(fig.fill()) : str(fig.pattern())))
                            : stmt(call(field, "set", str(((Operand.AlphanumericLiteral) v).value()))));
                    continue;
                }
                Expression field = c.field(parent);
                out.add(cond.parent().isAlphanumeric() ? storeAlphanumeric(field, v, c) : storeNumeric(field, v, c));
            }
        }
        return out;
    }

    /** SET switch TO ON / OFF: libcob's cob_set_switch on the SWITCH-n the mnemonic name stands for. */
    private static List<Statement> setSwitch(OpUnit u, TranslationContext c) throws HandlerException {
        SetStatement set = (SetStatement) u.statement();
        if (set.getSetType() != SetStatement.SetType.TO) {
            throw HandlerException.notApplicable("SET ... UP / DOWN BY is not a switch SET");
        }
        List<Statement> out = new ArrayList<>();
        for (var setTo : set.getSetTos()) {
            if (setTo.getValues().size() != 1) {
                throw HandlerException.unsupported("SET with several values is not supported");
            }
            var type = setTo.getValues().getFirst().getValueType();
            if (type != io.proleap.cobol.asg.metamodel.procedure.set.Value.ValueType.ON
                    && type != io.proleap.cobol.asg.metamodel.procedure.set.Value.ValueType.OFF) {
                throw HandlerException.notApplicable("SET ... TO a value other than ON / OFF");
            }
            boolean on = type == io.proleap.cobol.asg.metamodel.procedure.set.Value.ValueType.ON;
            for (var to : setTo.getTos()) {
                String name = to.getToCall().getCtx().getText();
                int n = c.switchNumberOf(name).orElseThrow(() -> HandlerException.unsupported(
                        "SET " + name + " TO ON / OFF: not a mnemonic name of SWITCH-0 .. SWITCH-36"));
                out.add(stmt(staticCall("Switches", "set", integer(n), bool(on))));
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
        var sw = c.switchCondition(v.getCtx().getText());
        if (sw.isPresent()) {
            return new Subject(null, null, sw.get());
        }
        Operand o = c.operand(v);
        if (o instanceof Operand.Field f && f.item().isCondition()) {
            return new Subject(null, null, c.conditionName(f, where));
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
                var sw = c.switchCondition(object.getValue().getValueStmt().getCtx().getText());
                if (sw.isPresent() && subject.value() == null && subject.bool() != null && object.getThrough() == null) {
                    yield subject.bool() ? sw.get() : not(sw.get());
                }
                Operand value = c.operand(object.getValue().getValueStmt());
                if (subject.value() == null) {
                    // TRUE / FALSE / condition subject with a condition-name object.
                    if (subject.bool() != null && value instanceof Operand.Field f && f.item().isCondition() && object.getThrough() == null) {
                        Expression e = c.conditionName(f, where);
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
            type = proc.getPerformType();
            if (c.dispatcher()) {
                body = new BlockStmt(NodeList.nodeList(c.performRange(calls.getFirst(), calls.size() > 1 ? calls.getLast() : null)));
            } else {
                if (calls.size() > 1 && !calls.get(0).getName().equalsIgnoreCase(calls.get(calls.size() - 1).getName())) {
                    throw HandlerException.unsupported("PERFORM ... THRU a range of procedures is not supported");
                }
                body = new BlockStmt(NodeList.nodeList(stmt(thisCall(c.procedureMethod(calls.getFirst())))));
            }
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
                    throw HandlerException.unsupported("PERFORM ... TIMES of a non-integer is invalid COBOL (cobc rejects it)");
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
                    List<VaryingPhrase> phrases = new ArrayList<>();
                    phrases.add(varying.getVaryingClause().getVaryingPhrase());
                    varying.getVaryingClause().getAfters().forEach(a -> phrases.add(a.getVaryingPhrase()));
                    yield testAfter(varying.getTestClause()) ? varyingAfterTestAfter(phrases, body, c) : varyingAfter(phrases, body, c);
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

    /** Initialisation, step and end test of one VARYING / AFTER phrase. */
    private record Varying(Statement init, Statement step, Expression until) {}

    private static Varying varyingParts(VaryingPhrase phrase, TranslationContext c) throws HandlerException {
        Operand var = c.operand(phrase.getVaryingValueStmt());
        Expression until = c.condition(phrase.getUntil().getCondition());
        if (var instanceof Operand.Index idx) {
            Expression from = intValue(c.operand(phrase.getFrom().getFromValueStmt()), c);
            Expression by = intValue(c.operand(phrase.getBy().getByValueStmt()), c);
            return new Varying(stmt(new AssignExpr(name(idx.index().javaName()), from, AssignExpr.Operator.ASSIGN)),
                    stmt(new AssignExpr(name(idx.index().javaName()), by, AssignExpr.Operator.PLUS)), until);
        }
        if (!(var instanceof Operand.Field f && f.numeric())) {
            throw HandlerException.unsupported("PERFORM VARYING requires a numeric data item or an index");
        }
        Expression field = c.field(var);
        Expression from = numericOperand(phrase.getFrom().getFromValueStmt(), c);
        Expression by = numericOperand(phrase.getBy().getByValueStmt(), c);
        return new Varying(stmt(call(field, "move", from)),
                stmt(call(field.clone(), "store", call(call(field.clone(), "get"), "add", by), bool(false), bool(false))), until);
    }

    /**
     * VARYING ... AFTER ... (test before): every variable starts at its FROM; the innermost loop
     * runs the body; when a level's condition holds, the level above steps and this level
     * restarts from its FROM, as the standard (and GnuCOBOL) order it.
     */
    private static List<Statement> varyingAfter(List<VaryingPhrase> phrases, BlockStmt body, TranslationContext c) throws HandlerException {
        List<Varying> parts = new ArrayList<>();
        for (VaryingPhrase p : phrases) {
            parts.add(varyingParts(p, c));
        }
        List<Statement> out = new ArrayList<>();
        parts.forEach(v -> out.add(v.init()));
        Statement loop = null;
        for (int k = parts.size() - 1; k >= 0; k--) {
            // Not a clone at the innermost level: pending decisions hold references to placeholders inside body.
            BlockStmt levelBody = loop == null ? body : new BlockStmt(NodeList.nodeList(loop));
            levelBody.addStatement(parts.get(k).step().clone());
            if (k + 1 < parts.size()) {
                levelBody.addStatement(parts.get(k + 1).init().clone());
            }
            loop = new WhileStmt(not(parts.get(k).until()), levelBody);
        }
        out.add(loop);
        return out;
    }

    /**
     * WITH TEST AFTER VARYING ... AFTER ...: every variable starts at its FROM and the body runs;
     * then the innermost level whose condition is false steps, and every level inside it restarts
     * from its FROM. When all conditions hold, the PERFORM ends.
     */
    private static List<Statement> varyingAfterTestAfter(List<VaryingPhrase> phrases, BlockStmt body, TranslationContext c)
            throws HandlerException {
        List<Varying> parts = new ArrayList<>();
        for (VaryingPhrase p : phrases) {
            parts.add(varyingParts(p, c));
        }
        List<Statement> out = new ArrayList<>();
        parts.forEach(v -> out.add(v.init()));
        Statement next = new BreakStmt();
        for (int k = 0; k < parts.size(); k++) {
            BlockStmt step = new BlockStmt(NodeList.nodeList(parts.get(k).step().clone()));
            for (int j = k + 1; j < parts.size(); j++) {
                step.addStatement(parts.get(j).init().clone());
            }
            next = new IfStmt(not(parts.get(k).until()), step, next);
        }
        // Not a clone: pending decisions hold references to placeholders inside body.
        body.addStatement(next);
        out.add(new WhileStmt(bool(true), body));
        return out;
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
            case STOP_RUN_AND_DISPLAY -> throw HandlerException.missingTarget(
                    "STOP literal shows the literal and waits for an operator reply; the target system decides what replaces the console",
                    true);
            case STOP_RUN_GIVING -> {
                // cobc: cob_stop_run (cob_get_int (x)), the integer part of the value.
                Operand status = c.operand(s.getStopStatementGiving().getGivingValueStmt());
                Expression value = switch (status) {
                    case Operand.NumericLiteral n when n.isInteger() -> integer(n.value().intValueExact());
                    case Operand.Field f when f.numeric() -> call(c.field(status), "intValue");
                    default -> throw HandlerException.unsupported("STOP RUN GIVING " + status.category() + " is not supported");
                };
                return List.of(stmt(staticCall("ProgramSupport", "stopRun", value)));
            }
        }
        return List.of(stmt(staticCall("ProgramSupport", "stopRun", self())));
    }

    /**
     * Candidate for STOP literal, as GnuCOBOL compiles it: DISPLAY of the literal on standard
     * output, then an ACCEPT that reads one line of standard input and discards it; the program
     * continues.
     */
    private static List<Statement> stopLiteralAccept(OpUnit u, TranslationContext c) throws HandlerException {
        return List.of(stmt(staticCall("Display", "line", stopLiteral(u, c))), stmt(staticCall("Accept", "line")));
    }

    /** Candidate for STOP literal: DISPLAY of the literal on standard output, and the program continues without waiting. */
    private static List<Statement> stopLiteralContinue(OpUnit u, TranslationContext c) throws HandlerException {
        return List.of(stmt(staticCall("Display", "line", stopLiteral(u, c))));
    }

    private static Expression stopLiteral(OpUnit u, TranslationContext c) throws HandlerException {
        StopStatement s = (StopStatement) u.statement();
        if (s.getStopType() != StopStatement.StopType.STOP_RUN_AND_DISPLAY) {
            throw HandlerException.notApplicable("not STOP literal");
        }
        return displayValue(c.operand(s.getDisplayValueStmt()), c);
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

    /**
     * CALL: a literal names a program of the project (checked against its USING items); an
     * identifier is resolved at run time among the project's programs (Dialect.dynamic_calls).
     * ON EXCEPTION / ON OVERFLOW runs when the program is not found or is active; NOT ON
     * EXCEPTION after it returns.
     */
    private static List<Statement> callProgram(OpUnit u, TranslationContext c) throws HandlerException {
        return callTo(u, c, null, false, false);
    }

    /**
     * The {@code call_external} candidate of a CALL 'literal' whose program is not in the project,
     * once its slots are chosen. {@code target} is the project program the name stands for, or
     * null when the program is absent at run time: then the CALL behaves as GnuCOBOL's CALL of a
     * module it cannot load (ON EXCEPTION runs; without it the run unit ends with "module not
     * found"). {@code byContent} passes every argument as a copy (correct only if the target
     * never changes its parameters).
     */
    static List<Statement> callExternal(OpUnit u, TranslationContext c, String target, boolean byContent) throws HandlerException {
        CallStatement call = (CallStatement) u.statement();
        if (!(call.getProgramValueStmt() instanceof LiteralValueStmt lit && lit.getLiteral().getLiteralType() == Literal.LiteralType.NON_NUMERIC)) {
            throw HandlerException.notApplicable("call_external binds CALL 'literal' only");
        }
        return callTo(u, c, target, byContent, target == null);
    }

    /**
     * CALL of the program the statement names, or of {@code target} (a project program) instead of
     * a literal name; {@code absent}: the literal names no program at run time (the arguments are
     * still evaluated, as GnuCOBOL does before it looks the module up).
     */
    private static List<Statement> callTo(OpUnit u, TranslationContext c, String target, boolean byContent, boolean absent)
            throws HandlerException {
        CallStatement call = (CallStatement) u.statement();
        Expression returning = null;
        if (call.getGivingPhrase() != null) {
            Operand r = c.operand(call.getGivingPhrase().getGivingCall());
            if (!(r instanceof Operand.Field rf && rf.numeric())) {
                throw HandlerException.unsupported("CALL ... RETURNING " + r.category() + " is not supported (a numeric item)");
            }
            if (call.getOnExceptionClause() != null || call.getOnOverflowPhrase() != null) {
                throw HandlerException.unsupported("CALL ... RETURNING with ON EXCEPTION / ON OVERFLOW is not supported");
            }
            returning = c.field(r);
        }
        if (call.getOnExceptionClause() != null && call.getOnOverflowPhrase() != null) {
            throw HandlerException.unsupported("CALL with both ON OVERFLOW and ON EXCEPTION");
        }
        Scope onException = call.getOnExceptionClause() != null ? call.getOnExceptionClause() : call.getOnOverflowPhrase();
        boolean dynamic = target == null && !(call.getProgramValueStmt() instanceof LiteralValueStmt lit
                && lit.getLiteral().getLiteralType() == Literal.LiteralType.NON_NUMERIC);
        if (!dynamic && target == null && returning == null
                && SYSTEM_ROUTINES.containsKey(((LiteralValueStmt) call.getProgramValueStmt()).getLiteral().getNonNumericLiteral())) {
            return systemRoutine(call, c);
        }
        NodeList<Expression> args = NodeList.nodeList(self());
        List<Integer> expected = null;
        String program = null;
        if (dynamic) {
            c.require("dynamic_calls", c.dialect().dynamicCalls(), call.getCtx(), "CALL identifier");
            Operand named = c.operand(call.getProgramValueStmt());
            if (!(named instanceof Operand.Field f) || !f.alphanumeric() || f.edited()) {
                throw HandlerException.unsupported("CALL through a " + named.category() + " item");
            }
            args.add(call(c.field(named), "get"));
            args.add(new com.github.javaparser.ast.expr.MethodReferenceExpr(new com.github.javaparser.ast.expr.TypeExpr(type(c.className())),
                    new NodeList<>(), c.programResolver()));
        } else {
            program = target != null ? target : ((LiteralValueStmt) call.getProgramValueStmt()).getLiteral().getNonNumericLiteral();
            String name = program;
            if (absent) {
                args.add(str(name));
                args.add(new com.github.javaparser.ast.expr.LambdaExpr(
                        new com.github.javaparser.ast.body.Parameter(new com.github.javaparser.ast.type.UnknownType(), "name"),
                        new com.github.javaparser.ast.expr.NullLiteralExpr()));
            }
        }
        if (!dynamic && !absent) {
            String name = program;
            String cls = c.programClass(name).orElseThrow(() -> target != null
                    ? HandlerException.notApplicable("program '" + name + "' is not part of the translation project")
                    : HandlerException.missingProgram(name,
                            "program '" + name + "' is not part of the translation project (unresolved cross-file dependency)"));
            args.add(call(name(cls), "instance"));
            if (c.entryIndex(name) > 0) {
                args.add(integer(c.entryIndex(name)));
            }
            if (call.getUsingPhrase() != null) {
                expected = c.programParameters(name).orElseThrow(() -> HandlerException.unsupported(
                        "the USING items of program '" + name + "' cannot be determined"));
            }
        }
        args.add(bool(onException != null));
        List<Integer> sizes = new ArrayList<>();
        var using = call.getUsingPhrase();
        for (var parameter : using == null ? List.<io.proleap.cobol.asg.metamodel.procedure.call.UsingParameter>of() : using.getUsingParameters()) {
            switch (parameter.getParameterType()) {
                case REFERENCE -> {
                    for (var ref : parameter.getByReferencePhrase().getByReferences()) {
                        if (ref.getByReferenceType() != null) {
                            throw HandlerException.unsupported("CALL USING BY REFERENCE " + ref.getByReferenceType() + " is not supported");
                        }
                        Operand o = c.operand(ref.getValueStmt());
                        Expression view = call(argumentField(o, c), "view");
                        args.add(byContent ? call(view, "copy") : view);
                        sizes.add(argumentSize(o));
                    }
                }
                case CONTENT -> {
                    for (var content : parameter.getByContentPhrase().getByContents()) {
                        if (content.getByContentType() != null) {
                            throw HandlerException.unsupported("CALL USING BY CONTENT " + content.getByContentType() + " is not supported");
                        }
                        Operand o = c.operand(content.getValueStmt());
                        if (o instanceof Operand.AlphanumericLiteral a) {
                            args.add(create("AlphanumericField", integer(Math.max(1, a.value().length())), str(a.value())));
                            sizes.add(a.value().length());
                        } else {
                            args.add(call(call(argumentField(o, c), "view"), "copy"));
                            sizes.add(argumentSize(o));
                        }
                    }
                }
                case VALUE -> throw HandlerException.unsupported("CALL USING BY VALUE is not supported");
            }
        }
        if (expected != null && !sizes.equals(expected)) {
            throw HandlerException.unsupported("CALL '" + program + "' USING passes items of " + sizes + " bytes but the program expects "
                    + expected + " (USING items must match in number and size)");
        }
        String method = dynamic || absent ? "callNamed" : c.entryIndex(program) > 0 ? "callEntry" : "call";
        Expression invoke = new com.github.javaparser.ast.expr.MethodCallExpr(name("ProgramSupport"), method, args);
        BlockStmt notOnException = call.getNotOnExceptionClause() == null ? new BlockStmt() : c.nested(call.getNotOnExceptionClause());
        if (returning != null) {
            // RETURNING receives the int the callee returns (its RETURN-CODE) with cob_set_int; the
            // caller's RETURN-CODE is not updated, so it is put back after the call.
            String saved = c.fresh("returnCode");
            Expression rc = c.field(new Operand.ReturnCode());
            List<Statement> out = new ArrayList<>();
            out.add(declare(type("BigDecimal"), saved, call(rc, "get")));
            out.add(stmt(invoke));
            out.add(stmt(call(returning, "move", call(rc.clone(), "get"))));
            out.add(stmt(call(rc.clone(), "move", name(saved))));
            if (!notOnException.isEmpty()) {
                out.add(notOnException);
            }
            return out;
        }
        if (onException == null) {
            return notOnException.isEmpty() ? List.of(stmt(invoke)) : List.of(stmt(invoke), notOnException);
        }
        return List.of(new IfStmt(invoke, c.nested(onException), notOnException.isEmpty() ? null : notOnException));
    }

    /** GnuCOBOL system routines translated through SystemRoutines, with the number of items they take before the length. */
    private static final Map<String, Integer> SYSTEM_ROUTINES = Map.of("CBL_TOUPPER", 1, "CBL_TOLOWER", 1, "CBL_NOT", 1,
            "CBL_AND", 2, "CBL_OR", 2, "CBL_XOR", 2, "CBL_EQ", 2, "CBL_IMP", 2, "CBL_NIMP", 2);

    /**
     * CALL "CBL_..." USING items BY VALUE length: libcob's routine on the items' bytes; its result
     * (0) becomes RETURN-CODE. The routine always exists, so ON EXCEPTION never runs and NOT ON
     * EXCEPTION always does. Items are passed BY REFERENCE (BY CONTENT: a copy); the length BY
     * VALUE, an integer literal or numeric item (cob_get_int).
     */
    private static List<Statement> systemRoutine(CallStatement call, TranslationContext c) throws HandlerException {
        String name = ((LiteralValueStmt) call.getProgramValueStmt()).getLiteral().getNonNumericLiteral();
        int items = SYSTEM_ROUTINES.get(name);
        NodeList<Expression> args = new NodeList<>();
        Expression length = null;
        var using = call.getUsingPhrase();
        for (var parameter : using == null ? List.<io.proleap.cobol.asg.metamodel.procedure.call.UsingParameter>of() : using.getUsingParameters()) {
            switch (parameter.getParameterType()) {
                case REFERENCE -> {
                    for (var ref : parameter.getByReferencePhrase().getByReferences()) {
                        if (ref.getByReferenceType() != null || length != null) {
                            throw HandlerException.unsupported("CALL '" + name + "' USING: items, then the length BY VALUE");
                        }
                        args.add(call(argumentField(c.operand(ref.getValueStmt()), c), "view"));
                    }
                }
                case CONTENT -> {
                    for (var content : parameter.getByContentPhrase().getByContents()) {
                        if (content.getByContentType() != null || length != null) {
                            throw HandlerException.unsupported("CALL '" + name + "' USING: items, then the length BY VALUE");
                        }
                        args.add(call(call(argumentField(c.operand(content.getValueStmt()), c), "view"), "copy"));
                    }
                }
                case VALUE -> {
                    for (var value : parameter.getByValuePhrase().getByValues()) {
                        if (length != null) {
                            throw HandlerException.unsupported("CALL '" + name + "' takes one length BY VALUE");
                        }
                        var byValue = (io.proleap.cobol.CobolParser.CallByValueContext) value.getCtx();
                        Operand o = c.operand(value.getValueStmt());
                        if (byValue.ADDRESS() != null) {
                            throw HandlerException.unsupported("CALL '" + name + "' USING BY VALUE ADDRESS OF");
                        }
                        if (byValue.LENGTH() != null) {
                            // BY VALUE LENGTH OF item: the item's size.
                            o = o instanceof Operand.Field f && c.fixedSize(f).isPresent()
                                    ? new Operand.NumericLiteral(BigDecimal.valueOf(c.fixedSize(f).getAsInt()), Integer.toString(c.fixedSize(f).getAsInt()))
                                    : null;
                            if (o == null) {
                                throw HandlerException.unsupported("CALL '" + name + "' USING BY VALUE LENGTH OF an item of variable size");
                            }
                        }
                        length = switch (o) {
                            case Operand.NumericLiteral n when n.isInteger() -> integer(n.value().intValueExact());
                            case Operand.Field f when f.numeric() -> call(c.field(o), "intValue");
                            case Operand o2 when o2.isNumericValued() && !(o2 instanceof Operand.Arithmetic) -> call(c.numeric(o2), "intValue");
                            default -> throw HandlerException.unsupported("CALL '" + name + "': the length BY VALUE is an integer literal or numeric item");
                        };
                    }
                }
            }
        }
        if (args.size() != items || length == null) {
            throw HandlerException.unsupported("CALL '" + name + "' takes " + items + " item(s) and a length BY VALUE");
        }
        args.add(length);
        Expression invoke = switch (name) {
            case "CBL_TOUPPER" -> new com.github.javaparser.ast.expr.MethodCallExpr(name("SystemRoutines"), "toUpper", args);
            case "CBL_TOLOWER" -> new com.github.javaparser.ast.expr.MethodCallExpr(name("SystemRoutines"), "toLower", args);
            case "CBL_NOT" -> new com.github.javaparser.ast.expr.MethodCallExpr(name("SystemRoutines"), "not", args);
            default -> {
                args.addFirst(str(name));
                yield new com.github.javaparser.ast.expr.MethodCallExpr(name("SystemRoutines"), "bits", args);
            }
        };
        List<Statement> out = new ArrayList<>();
        out.add(stmt(call(c.field(new Operand.ReturnCode()), "move", staticCall("BigDecimal", "valueOf", invoke))));
        if (call.getNotOnExceptionClause() != null) {
            out.add(c.nested(call.getNotOnExceptionClause()));
        }
        return out;
    }

    /** CANCEL: a program of the project starts afresh at its next CALL; any other name is ignored, as in GnuCOBOL. */
    private static List<Statement> cancelProgram(OpUnit u, TranslationContext c) throws HandlerException {
        var cancel = (io.proleap.cobol.asg.metamodel.procedure.cancel.CancelStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        for (var target : cancel.getCancelCalls()) {
            if (target.getCancelType() != null) {
                throw HandlerException.unsupported("CANCEL of a library name");
            }
            if (target.getValueStmt() instanceof LiteralValueStmt lit && lit.getLiteral().getLiteralType() == Literal.LiteralType.NON_NUMERIC) {
                // As GnuCOBOL's cob_cancel: a directory part is ignored.
                String written = lit.getLiteral().getNonNumericLiteral();
                String name = written.substring(Math.max(written.lastIndexOf('/'), written.lastIndexOf('\\')) + 1);
                var cls = c.programClass(name);
                if (cls.isPresent()) {
                    out.add(stmt(call(name(cls.get()), "cancel")));
                } else {
                    out.add(noOp("CANCEL '" + name + "' (not a program of the project)"));
                }
                continue;
            }
            Operand o = c.operand(target.getValueStmt());
            if (!(o instanceof Operand.Field f) || !f.alphanumeric() || f.edited()) {
                throw HandlerException.unsupported("CANCEL of a " + o.category() + " item");
            }
            out.add(stmt(call(null, c.cancelResolver(), staticCall("ProgramSupport", "cancelName", call(c.field(o), "get")))));
        }
        return out;
    }

    /** A CALL argument: a data item (a reference modification or variable-length group cannot be checked). */
    private static Expression argumentField(Operand o, TranslationContext c) throws HandlerException {
        if (!(o instanceof Operand.Field f) || f.refmod() != null || (f.item().isGroup() && f.item().variableTable() != null)) {
            throw HandlerException.unsupported("CALL argument of category " + o.category() + " is not supported");
        }
        return c.field(o);
    }

    private static int argumentSize(Operand o) {
        DataItem item = ((Operand.Field) o).item();
        return item.subscriptsOrNone() ? item.totalSize() : item.size();
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
            if (target.item().isGroup() && target.item().isRenames()) {
                throw HandlerException.unsupported("INITIALIZE of a RENAMES group");
            }
            DataItem variable = target.item().isGroup() ? target.item().variableTable() : null;
            if (variable != null && toValue) {
                throw HandlerException.unsupported("INITIALIZE ... TO VALUE of a group with OCCURS DEPENDING ON: GnuCOBOL 3.2 "
                        + "sets occurrences beyond the current count unevenly");
            }
            if (variable != null && variable.occursMin() < 1) {
                throw HandlerException.unsupported("INITIALIZE of a group with OCCURS 0 TO ... DEPENDING ON: GnuCOBOL 3.2 "
                        + "initializes the first occurrence even when none is in use");
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
            // A table with OCCURS DEPENDING ON is initialized up to its current count, as GnuCOBOL does.
            store = new ForStmt(NodeList.nodeList(new VariableDeclarationExpr(new VariableDeclarator(Ast.intType(), v, integer(1)))),
                    new BinaryExpr(name(v), occurrences(extra.get(i), c), BinaryExpr.Operator.LESS_EQUALS),
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
     * SEARCH ALL as cobc 3.2 generates it (codegen.c output_search_all): head = 0, tail = occurs + 1,
     * probe (head + tail) / 2, head = probe while the key is below the value, else tail = probe.
     * With low = head + 1 and high = tail - 1 this loop probes the same occurrences, so duplicate
     * keys find the same occurrence and keys out of order miss the same values as GnuCOBOL.
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
        var when = searchAllWhen(s);
        var cond = when.getCondition();
        var rel = cond.getAndOrConditions().isEmpty() ? cond.getCombinableCondition().getSimpleCondition().getRelationCondition() : null;
        if (rel == null || rel.getArithmeticComparison() == null
                || rel.getArithmeticComparison().getOperator().getRelationalOperatorType() != RelationalOperatorType.EQUAL) {
            throw HandlerException.unsupported("SEARCH ALL needs WHEN key = value (other conditions are not supported)");
        }
        Operand key = c.operand(rel.getArithmeticComparison().getArithmeticExpressionLeft());
        Operand value = c.operand(rel.getArithmeticComparison().getArithmeticExpressionRight());
        DataItem.Key first = t.table().keys().getFirst();
        if (!(key instanceof Operand.Field kf) || kf.item() != first.item()) {
            throw HandlerException.unsupported("SEARCH ALL needs WHEN on the first KEY, written on the left");
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

    /** One WHEN of SEARCH ALL, which must be the only one. */
    private static io.proleap.cobol.asg.metamodel.procedure.search.WhenPhrase searchAllWhen(
            io.proleap.cobol.asg.metamodel.procedure.search.SearchStatement s) throws HandlerException {
        if (s.getWhenPhrases().size() != 1) {
            throw HandlerException.unsupported("SEARCH ALL has exactly one WHEN");
        }
        return s.getWhenPhrases().getFirst();
    }

    // ------------------------------------------------- STRING / UNSTRING / INSPECT

    /** Character data of a sending operand: alphanumeric items, literals, a figurative constant's character, unsigned integers' digits. */
    private static Expression text(Operand o, TranslationContext c) throws HandlerException {
        return switch (o) {
            case Operand.Field f when f.alphanumeric() -> call(c.field(o), "get");
            case Operand.Field f when f.integer() && !f.item().numeric().signed() && f.item().usage() == DataItem.Usage.DISPLAY ->
                    call(c.field(o), "bytes");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.Computed v when !v.numeric() -> v.value();
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
        if (c.jdk()) {
            return stringJdk(s, c);
        }
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
        if (c.jdk()) {
            return unstringJdk(s, c);
        }
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
            // A numeric receiver gets the field as an alphanumeric MOVE (libcob's cob_str_memcpy is cob_move).
            boolean numeric = r instanceof Operand.Field nf && nf.numeric();
            if (!(r instanceof Operand.Field rf) || (numeric ? !takesAlphanumeric(rf) : !rf.alphanumeric() || rf.edited())) {
                throw HandlerException.unsupported("UNSTRING INTO " + r.category() + " is not supported (alphanumeric and numeric receivers)");
            }
            Expression field = c.field(r);
            BlockStmt then = new BlockStmt();
            then.addStatement(numeric ? moveAlphanumeric(field, call(name(u1), "data"), c) : stmt(call(field, "set", call(name(u1), "data"))));
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
            // Without DELIMITED BY a receiver takes as many characters as its COB_FIELD_SIZE: the digits of a
            // DISPLAY item (a separate sign excluded), the bytes of a packed or binary one.
            DataItem ri = ((Operand.Field) r).item();
            Expression length = !numeric ? call(field.clone(), "length")
                    : integer(ri.usage() == DataItem.Usage.DISPLAY ? ri.numeric().digits() : ri.size());
            Expression next = delimited ? call(name(u1), "next") : call(name(u1), "next", length);
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
        if (c.jdk()) {
            return inspectJdk(s, c);
        }
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

    // ------------------------------------------------- STRING / UNSTRING / INSPECT with the JDK

    /** {@code Pattern.quote(e)}: a regular expression matching the text of {@code e}. */
    private static Expression quote(Expression e) {
        return staticCall("java.util.regex.Pattern", "quote", e);
    }

    private static List<Statement> stringJdk(io.proleap.cobol.asg.metamodel.procedure.string.StringStatement s, TranslationContext c)
            throws HandlerException {
        if (s.getWithPointerPhrase() != null || s.getOnOverflowPhrase() != null || s.getNotOnOverflowPhrase() != null) {
            throw HandlerException.unsupported("STRING with POINTER or OVERFLOW has no JDK form");
        }
        Expression target = alphanumericTarget(s.getIntoPhrase().getIntoCall(), c);
        Expression chain = create("StringBuilder");
        for (var sendings : s.getSendings()) {
            if (sendings.getSendingsType() != io.proleap.cobol.asg.metamodel.procedure.string.Sendings.SendingsType.DELIMITED_BY) {
                throw HandlerException.unsupported("STRING ... FOR is not supported");
            }
            var by = sendings.getDelimitedByPhrase();
            boolean size = by.getDelimitedByType() == io.proleap.cobol.asg.metamodel.procedure.string.DelimitedByPhrase.DelimitedByType.SIZE;
            Expression delimiter = size ? null : text(c.operand(by.getCharactersValueStmt()), c);
            for (ValueStmt v : sendings.getSendingValueStmts()) {
                Expression part = text(c.operand(v), c);
                // Up to the first delimiter: the first piece of a split in two.
                chain = call(chain, "append", size ? part : new com.github.javaparser.ast.expr.ArrayAccessExpr(
                        call(part, "split", quote(delimiter.clone()), integer(2)), integer(0)));
            }
        }
        return List.of(stmt(call(target, "set", call(chain, "toString"))));
    }

    private static List<Statement> unstringJdk(io.proleap.cobol.asg.metamodel.procedure.unstring.UnstringStatement s, TranslationContext c)
            throws HandlerException {
        var sending = s.getSending();
        if (sending.getDelimitedByPhrase() == null || s.getWithPointerPhrase() != null || s.getTallyingPhrase() != null
                || s.getOnOverflowPhrase() != null || s.getNotOnOverflowPhrase() != null) {
            throw HandlerException.unsupported("UNSTRING without DELIMITED BY, or with POINTER, TALLYING or OVERFLOW, has no JDK form");
        }
        Operand source = c.operand(sending.getSendingCall());
        if (!(source instanceof Operand.Field sf) || !sf.alphanumeric()) {
            throw HandlerException.unsupported("UNSTRING of " + source.category() + " is not supported");
        }
        // The delimiters as one regular expression: Pattern.quote(d) each, (?:...)+ for ALL, joined by |.
        Expression regex = null;
        var by = sending.getDelimitedByPhrase();
        List<Map.Entry<ValueStmt, Boolean>> delimiters = new ArrayList<>();
        delimiters.add(Map.entry(by.getDelimitedByValueStmt(),
                ((io.proleap.cobol.CobolParser.UnstringDelimitedByPhraseContext) by.getCtx()).ALL() != null));
        for (var or : sending.getOrAlls()) {
            delimiters.add(Map.entry(or.getOrAllValueStmt(), ((io.proleap.cobol.CobolParser.UnstringOrAllPhraseContext) or.getCtx()).ALL() != null));
        }
        for (var d : delimiters) {
            Expression one = quote(text(c.operand(d.getKey()), c));
            if (d.getValue()) {
                one = Ast.binary(Ast.binary(str("(?:"), BinaryExpr.Operator.PLUS, one), BinaryExpr.Operator.PLUS, str(")+"));
            }
            regex = regex == null ? one : Ast.binary(Ast.binary(regex, BinaryExpr.Operator.PLUS, str("|")), BinaryExpr.Operator.PLUS, one);
        }
        String parts = c.fresh("parts");
        List<Statement> out = new ArrayList<>();
        out.add(declare(new com.github.javaparser.ast.type.ArrayType(type("String")), parts,
                call(call(c.field(source), "get"), "split", regex, integer(-1))));
        int i = 0;
        for (var into : s.getIntoPhrase().getIntos()) {
            if (into.getDelimiterIn() != null || into.getCountIn() != null) {
                throw HandlerException.unsupported("UNSTRING with DELIMITER IN or COUNT IN has no JDK form");
            }
            Operand r = c.operand(into.getIntoCall());
            boolean numeric = r instanceof Operand.Field nf && nf.numeric() && !nf.edited();
            if (!(r instanceof Operand.Field rf) || !numeric && (!rf.alphanumeric() || rf.edited())) {
                throw HandlerException.unsupported("UNSTRING INTO " + r.category() + " is not supported (alphanumeric and numeric receivers)");
            }
            Expression part = new com.github.javaparser.ast.expr.ArrayAccessExpr(name(parts), integer(i));
            Statement store = numeric ? stmt(call(c.field(r), "move", create("BigDecimal", call(part, "trim"))))
                    : stmt(call(c.field(r), "set", part));
            // A receiver without a field of its own keeps its value, as in COBOL.
            out.add(new IfStmt(Ast.binary(new com.github.javaparser.ast.expr.FieldAccessExpr(name(parts), "length"),
                    BinaryExpr.Operator.GREATER, integer(i)), new BlockStmt(NodeList.nodeList(store)), null));
            i++;
        }
        return out;
    }

    private static List<Statement> inspectJdk(io.proleap.cobol.asg.metamodel.procedure.inspect.InspectStatement s, TranslationContext c)
            throws HandlerException {
        Operand item = c.operand(s.getDataItemCall());
        if (!(item instanceof Operand.Field f && f.alphanumeric() && !f.edited())) {
            throw HandlerException.unsupported("INSPECT of " + item.category() + " has no JDK form");
        }
        List<Statement> out = new ArrayList<>();
        switch (s.getInspectType()) {
            case TALLYING -> tallyingJdk(s.getTallying().getFors(), item, out, c);
            case REPLACING -> out.add(stmt(call(c.field(item), "set", replacingJdk(List.of(s.getReplacing()), item, c))));
            case TALLYING_REPLACING -> {
                tallyingJdk(s.getTallyingReplacing().getFors(), item, out, c);
                out.add(stmt(call(c.field(item), "set", replacingJdk(s.getTallyingReplacing().getReplacings(), item, c))));
            }
            case CONVERTING -> throw HandlerException.unsupported("INSPECT CONVERTING has no JDK form");
        }
        return out;
    }

    private static void noRegion(List<io.proleap.cobol.asg.metamodel.procedure.inspect.BeforeAfterPhrase> phrases) throws HandlerException {
        if (phrases != null && !phrases.isEmpty()) {
            throw HandlerException.unsupported("INSPECT ... BEFORE / AFTER INITIAL has no JDK form");
        }
    }

    private static void tallyingJdk(List<io.proleap.cobol.asg.metamodel.procedure.inspect.For> fors, Operand item, List<Statement> out,
            TranslationContext c) throws HandlerException {
        for (var f : fors) {
            Expression counter = integerItem(f.getTallyCountDataItemCall(), c);
            List<Ordered> comparands = new ArrayList<>();
            for (var ch : f.getCharacters()) {
                comparands.add(new Ordered(ch.getCtx().getStart().getTokenIndex(), ch, "CHARACTERS"));
            }
            for (var phrase : f.getAllLeadingPhrase()) {
                for (var al : phrase.getAllLeadings()) {
                    comparands.add(new Ordered(al.getCtx().getStart().getTokenIndex(), al, phrase.getAllLeadingsType().name()));
                }
            }
            comparands.sort(java.util.Comparator.comparingInt(Ordered::token));
            for (Ordered o : comparands) {
                Expression value = call(c.field(item), "get");
                Expression count;
                if (o.element() instanceof io.proleap.cobol.asg.metamodel.procedure.inspect.Characters ch) {
                    noRegion(ch.getBeforeAfterPhrases());
                    count = call(value, "length");
                } else {
                    var al = (io.proleap.cobol.asg.metamodel.procedure.inspect.AllLeading) o.element();
                    noRegion(al.getBeforeAfterPhrases());
                    Expression pattern = text(c.operand(al.getPatternDataItemValueStmt()), c);
                    if (o.kind().equals("ALL")) {
                        // Non-overlapping occurrences, left to right: the pieces of a split, less one.
                        count = Ast.binary(new com.github.javaparser.ast.expr.FieldAccessExpr(call(value, "split", quote(pattern), integer(-1)),
                                "length"), BinaryExpr.Operator.MINUS, integer(1));
                    } else {
                        // The characters a leading run of the pattern takes, over the pattern's length.
                        Expression rest = call(value.clone(), "replaceFirst",
                                Ast.binary(Ast.binary(str("^(?:"), BinaryExpr.Operator.PLUS, quote(pattern)), BinaryExpr.Operator.PLUS, str(")+")),
                                str(""));
                        count = Ast.binary(new EnclosedExpr(Ast.binary(call(value, "length"), BinaryExpr.Operator.MINUS, call(rest, "length"))),
                                BinaryExpr.Operator.DIVIDE, call(pattern.clone(), "length"));
                    }
                }
                out.add(stmt(call(counter.clone(), "store", call(call(counter.clone(), "get"), "add", staticCall("BigDecimal", "valueOf", count)),
                        bool(false), bool(false))));
            }
        }
    }

    private static Expression replacingJdk(List<io.proleap.cobol.asg.metamodel.procedure.inspect.Replacing> replacings, Operand item,
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
        Expression chain = call(c.field(item), "get");
        for (Ordered o : comparands) {
            if (o.element() instanceof io.proleap.cobol.asg.metamodel.procedure.inspect.ReplacingCharacters ch) {
                noRegion(ch.getBeforeAfterPhrases());
                if (literalLength(ch.getBy().getByValueStmt(), c) != 1 && !(c.operand(ch.getBy().getByValueStmt()) instanceof Operand.Figurative)) {
                    throw HandlerException.unsupported("REPLACING CHARACTERS BY needs a single character");
                }
                // Every character is replaced: the replacement repeated over the item's length.
                chain = call(replacement(ch.getBy().getByValueStmt(), null, 1, c), "repeat", call(c.field(item), "length"));
            } else {
                var al = (io.proleap.cobol.asg.metamodel.procedure.inspect.ReplacingAllLeading) o.element();
                noRegion(al.getBeforeAfterPhrases());
                int length = literalLength(al.getPatternDataItemValueStmt(), c);
                int byLength = literalLength(al.getBy().getByValueStmt(), c);
                if (length < 0 || (byLength >= 0 && byLength != length && !(c.operand(al.getBy().getByValueStmt()) instanceof Operand.Figurative))) {
                    throw HandlerException.unsupported("INSPECT REPLACING needs a pattern and replacement of the same, known length");
                }
                Expression pattern = text(c.operand(al.getPatternDataItemValueStmt()), c);
                Expression by = replacement(al.getBy().getByValueStmt(), null, length, c);
                chain = switch (o.kind()) {
                    case "ALL" -> call(chain, "replace", pattern, by);
                    case "FIRST" -> call(chain, "replaceFirst", quote(pattern), staticCall("java.util.regex.Matcher", "quoteReplacement", by));
                    default -> throw HandlerException.unsupported("INSPECT REPLACING LEADING has no JDK form");
                };
            }
        }
        return chain;
    }

    // ------------------------------------------------------- GO TO / ALTER

    private static List<Statement> goTo(OpUnit u, TranslationContext c) throws HandlerException {
        var g = (io.proleap.cobol.asg.metamodel.procedure.gotostmt.GoToStatement) u.statement();
        if (g.getGoToType() == io.proleap.cobol.asg.metamodel.procedure.gotostmt.GoToStatement.GoToType.SIMPLE) {
            if (g.getSimple() == null || g.getSimple().getProcedureCall() == null) {
                throw HandlerException.unsupported("GO TO without a procedure (altered GO TO.) is not supported");
            }
            return List.of(c.goTo(g.getSimple().getProcedureCall()));
        }
        // GO TO p1 p2 ... DEPENDING ON n: to the n-th procedure; outside 1..count, on to the next statement.
        var depending = g.getDependingOnPhrase();
        Operand n = c.operand(depending.getDependingOnCall());
        if (!(n instanceof Operand.Field f && f.integer())) {
            throw HandlerException.unsupported("GO TO ... DEPENDING ON needs an integer item");
        }
        NodeList<com.github.javaparser.ast.stmt.SwitchEntry> entries = new NodeList<>();
        int k = 1;
        for (Call target : depending.getProcedureCalls()) {
            entries.add(new com.github.javaparser.ast.stmt.SwitchEntry(NodeList.nodeList(integer(k++)),
                    com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP,
                    NodeList.nodeList(new com.github.javaparser.ast.stmt.ReturnStmt(integer(c.procedureNumber(target))))));
        }
        entries.add(new com.github.javaparser.ast.stmt.SwitchEntry(new NodeList<>(), com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP,
                NodeList.nodeList(new BreakStmt())));
        return List.of(new com.github.javaparser.ast.stmt.SwitchStmt(call(c.field(n), "intValue"), entries));
    }

    /** The altered paragraph's GO TO reads its target from a field of the program, so ALTER is an assignment. */
    private static List<Statement> alter(OpUnit u, TranslationContext c) throws HandlerException {
        var a = (io.proleap.cobol.asg.metamodel.procedure.alter.AlterStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        for (var to : a.getProceedTos()) {
            out.add(stmt(new AssignExpr(name(c.alteredField(to.getSourceCall())), integer(c.procedureNumber(to.getTargetCall())),
                    AssignExpr.Operator.ASSIGN)));
        }
        return out;
    }

    // ------------------------------------------------------------- files

    private static List<Statement> openFile(OpUnit u, TranslationContext c) throws HandlerException {
        var o = (io.proleap.cobol.asg.metamodel.procedure.open.OpenStatement) u.statement();
        // Phrases run in source order, as GnuCOBOL opens them.
        List<Object[]> opens = new ArrayList<>();
        for (var p : o.getInputPhrases()) {
            for (var input : p.getInputs()) {
                if (input.getInputType() != null) {
                    throw HandlerException.unsupported("OPEN INPUT ... " + input.getInputType() + " is not supported");
                }
                opens.add(new Object[] {input.getCtx().getStart().getTokenIndex(), input.getFileCall(), "INPUT"});
            }
        }
        for (var p : o.getOutputPhrases()) {
            for (var output : p.getOutputs()) {
                if (output.isNoRewind()) {
                    throw HandlerException.unsupported("OPEN OUTPUT ... NO REWIND is not supported");
                }
                opens.add(new Object[] {output.getCtx().getStart().getTokenIndex(), output.getFileCall(), "OUTPUT"});
            }
        }
        for (var p : o.getInputOutputPhrases()) {
            for (Call f : p.getFileCalls()) {
                opens.add(new Object[] {p.getCtx().getStart().getTokenIndex(), f, "IO"});
            }
        }
        for (var p : o.getExtendPhrases()) {
            for (Call f : p.getFileCalls()) {
                opens.add(new Object[] {p.getCtx().getStart().getTokenIndex(), f, "EXTEND"});
            }
        }
        opens.sort(java.util.Comparator.comparingInt(a -> (Integer) a[0]));
        List<Statement> out = new ArrayList<>();
        for (Object[] open : opens) {
            var file = c.file((Call) open[1]);
            if (file.sort()) {
                throw HandlerException.unsupported("OPEN of a sort file");
            }
            if (file.lineSequential() && open[2].equals("IO")) {
                throw HandlerException.unsupported("OPEN I-O of a LINE SEQUENTIAL file is not supported");
            }
            if (file.keyed() && open[2].equals("EXTEND") && file.access() != TranslationContext.Access.SEQUENTIAL) {
                throw HandlerException.unsupported("OPEN EXTEND of a file with ACCESS RANDOM / DYNAMIC is not supported");
            }
            out.add(stmt(call(name(file.javaName()), "open", new com.github.javaparser.ast.expr.FieldAccessExpr(name("CobolFile"), (String) open[2]))));
        }
        return out;
    }

    private static List<Statement> closeFile(OpUnit u, TranslationContext c) throws HandlerException {
        var close = (io.proleap.cobol.asg.metamodel.procedure.close.CloseStatement) u.statement();
        List<Statement> out = new ArrayList<>();
        for (var f : close.getCloseFiles()) {
            var file = c.file(f.getFileCall());
            String method = "close";
            var relative = ((io.proleap.cobol.CobolParser.CloseFileContext) f.getCtx()).closeRelativeStatement();
            if (relative != null && relative.LOCK() != null) {
                method = "closeWithLock";
            } else if (relative != null && relative.REWIND() != null && !file.keyed() && !file.sort()) {
                method = "closeNoRewind";
            } else if (f.getCloseFileType() != null) {
                throw HandlerException.unsupported("CLOSE ... " + f.getCtx().getText() + " is not supported"
                        + (relative != null ? " (NO REWIND of a RELATIVE or INDEXED file)" : ""));
            }
            out.add(stmt(call(name(file.javaName()), method)));
        }
        return out;
    }

    /** READ NEXT / PREVIOUS, or null for a READ without either word. */
    static String readDirection(io.proleap.cobol.asg.metamodel.procedure.read.ReadStatement r) throws HandlerException {
        var ctx = (io.proleap.cobol.CobolParser.ReadStatementContext) r.getCtx();
        if (ctx.NEXT() != null) {
            return "next";
        }
        if (ctx.cobolWord() == null) {
            return null;
        }
        if (!ctx.cobolWord().getText().equalsIgnoreCase("PREVIOUS")) {
            throw HandlerException.unsupported("READ ... " + ctx.cobolWord().getText() + " is not supported");
        }
        return "previous";
    }

    /** Whether a READ of {@code file} reads by key (ACCESS RANDOM, or DYNAMIC without NEXT / PREVIOUS). */
    static boolean randomRead(TranslationContext.FileRef file, String direction) {
        return file.keyed() && (file.access() == TranslationContext.Access.RANDOM
                || file.access() == TranslationContext.Access.DYNAMIC && direction == null);
    }

    private static List<Statement> readFile(OpUnit u, TranslationContext c) throws HandlerException {
        var r = (io.proleap.cobol.asg.metamodel.procedure.read.ReadStatement) u.statement();
        if (r.getWith() != null) {
            throw HandlerException.unprovable("READ ... " + r.getWith().getCtx().getText()
                    + ": record locks matter only when another process uses the file at the same time");
        }
        return read(u, c);
    }

    /** Candidate for READ ... WITH LOCK / NO LOCK / WAIT: the READ without its lock phrase. */
    private static List<Statement> readFileUnlocked(OpUnit u, TranslationContext c) throws HandlerException {
        var r = (io.proleap.cobol.asg.metamodel.procedure.read.ReadStatement) u.statement();
        if (r.getWith() == null) {
            throw HandlerException.notApplicable("READ without a lock phrase");
        }
        return read(u, c);
    }

    private static List<Statement> read(OpUnit u, TranslationContext c) throws HandlerException {
        var r = (io.proleap.cobol.asg.metamodel.procedure.read.ReadStatement) u.statement();
        var file = c.file(r.getFileCall());
        if (file.sort()) {
            throw HandlerException.unsupported("READ of a sort file (use RETURN)");
        }
        String direction = readDirection(r);
        boolean random = randomRead(file, direction);
        boolean invalidKey = r.getInvalidKeyPhrase() != null;
        boolean atEnd = r.getAtEnd() != null;
        Expression f = name(file.javaName());
        Expression read;
        if (random) {
            if (atEnd || r.getNotAtEndPhrase() != null) {
                throw HandlerException.unsupported("AT END on a READ by key");
            }
            if (file.organization() == TranslationContext.Organization.RELATIVE) {
                if (r.getKey() != null && c.dataItem(r.getKey().getKeyCall()) != file.relativeKey()) {
                    throw HandlerException.unsupported("READ ... KEY IS other than the RELATIVE KEY");
                }
                read = call(f, "read", bool(invalidKey));
            } else {
                read = call(f, "read", integer(r.getKey() == null ? 0 : keyIndex(file, c.dataItem(r.getKey().getKeyCall()), true)), bool(invalidKey));
            }
        } else {
            if (r.getKey() != null || invalidKey || r.getNotInvalidKeyPhrase() != null) {
                throw HandlerException.unsupported("KEY / INVALID KEY on a sequential READ");
            }
            if (!file.keyed()) {
                if ("previous".equals(direction)) {
                    throw HandlerException.unsupported("READ PREVIOUS of a " + file.organization() + " file");
                }
                read = call(f, "read", bool(atEnd));
            } else {
                read = call(f, "readNext", bool("previous".equals(direction)), bool(atEnd));
            }
        }
        var notPhrase = random ? r.getNotInvalidKeyPhrase() : r.getNotAtEndPhrase();
        BlockStmt success = notPhrase == null ? new BlockStmt() : c.nested(notPhrase);
        if (r.getInto() != null) {
            Operand into = c.operand(r.getInto().getIntoCall());
            // INTO moves only after a successful READ.
            success.getStatements().addFirst(storeAlphanumericOrGroup(into, call(f.clone(), "data"), c));
        }
        return conditional(read, random ? invalidKey ? c.nested(r.getInvalidKeyPhrase()) : null : atEnd ? c.nested(r.getAtEnd()) : null,
                success, f);
    }

    /**
     * {@code if (statement) {condition} else if (file.succeeded()) {success}}: the AT END / INVALID
     * KEY phrase runs for that condition, the NOT phrase only when the statement succeeded.
     */
    private static List<Statement> conditional(Expression statement, BlockStmt condition, BlockStmt success, Expression file) {
        Statement ok = success.isEmpty() ? null : new IfStmt(call(file.clone(), "succeeded"), success, null);
        if (condition == null) {
            return ok == null ? List.of(stmt(statement)) : List.of(stmt(statement), ok);
        }
        return List.of(new IfStmt(statement, condition, ok));
    }

    /**
     * The number of the key of an INDEXED file that {@code item} names (0 for the record key): the
     * key itself or, for START, an item at the key's position no longer than it.
     */
    private static int keyIndex(TranslationContext.FileRef file, DataItem item, boolean exact) throws HandlerException {
        for (int i = 0; i < file.keys().size(); i++) {
            DataItem key = file.keys().get(i).item();
            if (key == item || !exact && file.records().contains(item.record()) && item.offset() == key.offset() && item.size() <= key.size()
                    && !item.hasAccessor()) {
                return i;
            }
        }
        throw HandlerException.unsupported("KEY IS " + item.cobolName() + " is not a key of the file");
    }

    /** MOVE of alphanumeric data into an alphanumeric item or a group (READ INTO). */
    private static Statement storeAlphanumericOrGroup(Operand target, Expression value, TranslationContext c) throws HandlerException {
        if (!(target instanceof Operand.Field t) || !t.alphanumeric() || t.edited()) {
            throw HandlerException.unsupported("READ ... INTO " + target.category() + " is not supported");
        }
        return stmt(call(t.item().isGroup() && t.refmod() == null ? c.receiving(target) : c.field(target), "set", value));
    }

    /** The FD record a WRITE / REWRITE names and its file. */
    private static Operand.Field fileRecord(Call recordCall, TranslationContext c) throws HandlerException {
        Operand o = c.operand(recordCall);
        if (!(o instanceof Operand.Field f) || f.refmod() != null || !f.subscripts().isEmpty() || f.item().parent() != null) {
            throw HandlerException.unsupported(recordCall.getName() + " is not an FD record");
        }
        return f;
    }

    private static Expression recordView(Operand.Field record, TranslationContext c) throws HandlerException {
        Expression f = c.field(record);
        return record.item().isNumeric() || record.item().isEdited() ? call(f, "view") : f;
    }

    private static List<Statement> writeFile(OpUnit u, TranslationContext c) throws HandlerException {
        var w = (io.proleap.cobol.asg.metamodel.procedure.write.WriteStatement) u.statement();
        Operand.Field record = fileRecord(w.getRecordCall(), c);
        var file = c.fileOfRecord(record.item());
        if (file.sort()) {
            throw HandlerException.unsupported("WRITE of a sort record (use RELEASE)");
        }
        boolean invalidKey = w.getInvalidKeyPhrase() != null;
        if (!file.keyed() && (invalidKey || w.getNotInvalidKeyPhrase() != null)) {
            throw HandlerException.unsupported("INVALID KEY on a WRITE of a " + file.organization() + " file");
        }
        boolean endOfPage = w.getAtEndOfPagePhrase() != null || w.getNotAtEndOfPagePhrase() != null;
        if (file.keyed() && (w.getAdvancingPhrase() != null || endOfPage)) {
            throw HandlerException.unsupported("ADVANCING / END-OF-PAGE on a WRITE of a " + file.organization() + " file");
        }
        List<Statement> out = new ArrayList<>();
        if (w.getFrom() != null) {
            out.add(fromMove(record, c.operand(w.getFrom().getFromValueStmt()), c));
        }
        Expression f = name(file.javaName());
        if (!file.keyed()) {
            if (w.getAdvancingPhrase() == null && !endOfPage) {
                out.add(stmt(call(f, "write", recordView(record, c))));
                return out;
            }
            Expression options = advancing(w.getAdvancingPhrase(), file, c);
            out.addAll(conditional(call(f, "write", recordView(record, c), options, bool(w.getAtEndOfPagePhrase() != null)),
                    w.getAtEndOfPagePhrase() == null ? null : c.nested(w.getAtEndOfPagePhrase()),
                    w.getNotAtEndOfPagePhrase() == null ? new BlockStmt() : c.nested(w.getNotAtEndOfPagePhrase()), f));
            return out;
        }
        out.addAll(conditional(call(f, "write", recordView(record, c), bool(invalidKey)), invalidKey ? c.nested(w.getInvalidKeyPhrase()) : null,
                w.getNotInvalidKeyPhrase() == null ? new BlockStmt() : c.nested(w.getNotInvalidKeyPhrase()), f));
        return out;
    }

    /**
     * The ADVANCING options of a WRITE as GnuCOBOL encodes them ({@code CobolFile.AFTER | CobolFile.LINES | 2});
     * without the phrase a LINE SEQUENTIAL record advances one line after it, a SEQUENTIAL record not at all.
     */
    private static Expression advancing(io.proleap.cobol.asg.metamodel.procedure.write.AdvancingPhrase a, TranslationContext.FileRef file,
            TranslationContext c) throws HandlerException {
        Function<String, Expression> flag = n -> new com.github.javaparser.ast.expr.FieldAccessExpr(name("CobolFile"), n);
        java.util.function.BinaryOperator<Expression> or = (x, y) -> new BinaryExpr(x, y, BinaryExpr.Operator.BINARY_OR);
        if (a == null) {
            return file.lineSequential() ? or.apply(or.apply(flag.apply("BEFORE"), flag.apply("LINES")), integer(1)) : integer(0);
        }
        Expression position = flag.apply(a.getPositionType() == io.proleap.cobol.asg.metamodel.procedure.write.AdvancingPhrase.PositionType.BEFORE
                ? "BEFORE" : "AFTER");
        var type = a.getAdvancingType();
        String mnemonic = type == io.proleap.cobol.asg.metamodel.procedure.write.AdvancingPhrase.AdvancingType.MNEMONIC
                ? a.getAdvancingMnemonic().getMnemonicCall().getName() : null;
        if (type == io.proleap.cobol.asg.metamodel.procedure.write.AdvancingPhrase.AdvancingType.LINES) {
            // ProLeap parses ADVANCING mnemonic-name as ADVANCING identifier (LINES).
            String word = a.getAdvancingLines().getLinesValueStmt().getCtx().getText();
            if (!c.device(word).equalsIgnoreCase(word)) {
                type = io.proleap.cobol.asg.metamodel.procedure.write.AdvancingPhrase.AdvancingType.MNEMONIC;
                mnemonic = word;
            }
        }
        switch (type) {
            case PAGE -> {
                return or.apply(position, flag.apply("PAGE"));
            }
            case MNEMONIC -> {
                String device = c.device(mnemonic);
                if (!device.matches("C(0[1-9]|1[0-2])|S0[1-5]|CSP|TOP|FORMFEED")) {
                    throw HandlerException.unsupported("WRITE ... ADVANCING " + device + " (not a channel / form feed name)");
                }
                return or.apply(position, flag.apply("PAGE"));
            }
            default -> {
                Operand lines = c.operand(a.getAdvancingLines().getLinesValueStmt());
                Expression count;
                if (lines instanceof Operand.NumericLiteral n) {
                    if (n.value().scale() > 0 || n.value().signum() < 0 || n.value().compareTo(BigDecimal.valueOf(65535)) > 0) {
                        throw HandlerException.unsupported("WRITE ... ADVANCING " + n.text() + " LINES");
                    }
                    count = integer(n.value().intValueExact());
                } else if (lines instanceof Operand.Field f && f.numeric()) {
                    count = staticCall("CobolFile", "lines", call(c.field(lines), "intValue"));
                } else {
                    throw HandlerException.unsupported("WRITE ... ADVANCING " + lines.category() + " LINES");
                }
                return or.apply(or.apply(position, flag.apply("LINES")), count);
            }
        }
    }

    private static List<Statement> rewriteFile(OpUnit u, TranslationContext c) throws HandlerException {
        var w = (io.proleap.cobol.asg.metamodel.procedure.rewrite.RewriteStatement) u.statement();
        Operand.Field record = fileRecord(w.getRecordCall(), c);
        var file = c.fileOfRecord(record.item());
        if (file.lineSequential() || file.sort()) {
            throw HandlerException.unsupported("REWRITE of a " + file.organization() + " file is not supported");
        }
        boolean invalidKey = w.getInvalidKeyPhrase() != null;
        if (!file.keyed() && (invalidKey || w.getNotInvalidKeyPhrase() != null)) {
            throw HandlerException.unsupported("INVALID KEY on a REWRITE of a SEQUENTIAL file");
        }
        if (file.organization() == TranslationContext.Organization.INDEXED && file.access() == TranslationContext.Access.SEQUENTIAL
                && file.keys().size() > 1) {
            // GnuCOBOL 3.2 then works from internal state (which record it replaces, where alternate keys point).
            throw HandlerException.unsupported("REWRITE of an INDEXED file with ALTERNATE RECORD KEYs in ACCESS SEQUENTIAL");
        }
        List<Statement> out = new ArrayList<>();
        if (w.getFrom() != null) {
            out.add(fromMove(record, c.operand(w.getFrom().getFromCall()), c));
        }
        Expression f = name(file.javaName());
        if (!file.keyed()) {
            out.add(stmt(call(f, "rewrite", recordView(record, c))));
            return out;
        }
        out.addAll(conditional(call(f, "rewrite", recordView(record, c), bool(invalidKey)), invalidKey ? c.nested(w.getInvalidKeyPhrase()) : null,
                w.getNotInvalidKeyPhrase() == null ? new BlockStmt() : c.nested(w.getNotInvalidKeyPhrase()), f));
        return out;
    }

    private static TranslationContext.FileRef keyedFile(Call fileCall, String verb, TranslationContext c) throws HandlerException {
        var file = c.file(fileCall);
        if (!file.keyed()) {
            throw HandlerException.unsupported(verb + " of a " + file.organization() + " file");
        }
        return file;
    }

    private static List<Statement> deleteFile(OpUnit u, TranslationContext c) throws HandlerException {
        var d = (io.proleap.cobol.asg.metamodel.procedure.delete.DeleteStatement) u.statement();
        var file = keyedFile(d.getFileCall(), "DELETE", c);
        if (file.organization() == TranslationContext.Organization.RELATIVE && file.relativeKey() == null) {
            throw HandlerException.unsupported("DELETE of a RELATIVE file without a RELATIVE KEY");
        }
        boolean invalidKey = d.getInvalidKeyPhrase() != null;
        Expression f = name(file.javaName());
        return conditional(call(f, "delete", bool(invalidKey)), invalidKey ? c.nested(d.getInvalidKeyPhrase()) : null,
                d.getNotInvalidKeyPhrase() == null ? new BlockStmt() : c.nested(d.getNotInvalidKeyPhrase()), f);
    }

    /** START's relation as an IndexedFile condition name (EQ, GT, GE, LT, LE). */
    static String startCondition(io.proleap.cobol.asg.metamodel.procedure.start.StartStatement s) {
        var k = ((io.proleap.cobol.CobolParser.StartStatementContext) s.getCtx()).startKey();
        if (k == null) {
            return "EQ";
        }
        boolean not = k.NOT() != null;
        boolean greater = k.GREATER() != null || k.MORETHANCHAR() != null;
        boolean less = k.LESS() != null || k.LESSTHANCHAR() != null;
        if (k.MORETHANOREQUAL() != null || k.GREATER() != null && k.EQUAL() != null || not && less) {
            return "GE";
        }
        if (k.LESSTHANOREQUAL() != null || k.LESS() != null && k.EQUAL() != null || not && greater) {
            return "LE";
        }
        return greater ? "GT" : less ? "LT" : "EQ";
    }

    private static List<Statement> startFile(OpUnit u, TranslationContext c) throws HandlerException {
        var s = (io.proleap.cobol.asg.metamodel.procedure.start.StartStatement) u.statement();
        var file = keyedFile(s.getFileCall(), "START", c);
        Expression condition = new com.github.javaparser.ast.expr.FieldAccessExpr(name("IndexedFile"), startCondition(s));
        DataItem keyItem = s.getKey() == null ? null : c.dataItem(s.getKey().getComparisonCall());
        boolean invalidKey = s.getInvalidKeyPhrase() != null;
        Expression f = name(file.javaName());
        Expression start;
        if (file.organization() == TranslationContext.Organization.RELATIVE) {
            if (file.relativeKey() == null || keyItem != null && keyItem != file.relativeKey()) {
                throw HandlerException.unsupported("START of a RELATIVE file needs its RELATIVE KEY");
            }
            start = call(f, "start", condition, bool(invalidKey));
        } else {
            int key = keyItem == null ? 0 : keyIndex(file, keyItem, false);
            int length = keyItem == null ? file.keys().getFirst().item().size() : keyItem.size();
            start = call(f, "start", condition, integer(key), integer(length), bool(invalidKey));
        }
        return conditional(start, invalidKey ? c.nested(s.getInvalidKeyPhrase()) : null,
                s.getNotInvalidKeyPhrase() == null ? new BlockStmt() : c.nested(s.getNotInvalidKeyPhrase()), f);
    }

    /** WRITE / REWRITE ... FROM: a MOVE into the record first. */
    private static Statement fromMove(Operand.Field record, Operand from, TranslationContext c) throws HandlerException {
        if (record.item().isGroup()) {
            Expression value = switch (from) {
                case Operand.Field f when f.alphanumeric() -> call(c.field(from), "get");
                case Operand.AlphanumericLiteral a -> str(a.value());
                default -> throw HandlerException.unsupported("WRITE ... FROM " + from.category() + " into a group record is not supported");
            };
            return stmt(call(c.receiving(record), "set", value));
        }
        if (record.item().isNumeric()) {
            return storeNumeric(c.field(record), from, c);
        }
        if (record.item().isEdited()) {
            return storeEdited(c.field(record), record.item(), from, c);
        }
        return storeAlphanumeric(c.field(record), from, c);
    }

    // ------------------------------------------------------------- SORT / MERGE

    /** PERFORM of an INPUT / OUTPUT PROCEDURE. */
    private static Statement performProcedure(Call from, Call thru, TranslationContext c) throws HandlerException {
        if (c.dispatcher()) {
            return c.performRange(from, thru);
        }
        if (thru != null && !thru.getName().equalsIgnoreCase(from.getName())) {
            throw HandlerException.unsupported("INPUT / OUTPUT PROCEDURE ... THRU outside the dispatcher form");
        }
        return stmt(thisCall(c.procedureMethod(from)));
    }

    /**
     * The sequence of a SORT / MERGE COLLATING SEQUENCE phrase (one alphabet, NATIVE / STANDARD-1 /
     * ASCII or EBCDIC), or null without the phrase.
     */
    private static String collating(List<io.proleap.cobol.CobolParser.AlphabetNameContext> names, Object alphanumeric, Object national,
            String verb, TranslationContext c) throws HandlerException {
        if (names == null) {
            return null;
        }
        if (names.size() != 1 || alphanumeric != null || national != null) {
            throw HandlerException.unsupported(verb + " COLLATING SEQUENCE with several alphabets or FOR ALPHANUMERIC / NATIONAL");
        }
        String name = names.getFirst().getText();
        return c.alphabet(name).orElseThrow(() -> HandlerException.unsupported(verb + " COLLATING SEQUENCE " + name
                + ": only alphabets declared NATIVE, STANDARD-1, ASCII or EBCDIC are supported"));
    }

    private static List<Statement> sortKeys(Expression sortFile, List<?> onKeys, TranslationContext c) throws HandlerException {
        return sortKeys(sortFile, onKeys, null, c);
    }

    private static List<Statement> sortKeys(Expression sortFile, List<?> onKeys, String collating, TranslationContext c) throws HandlerException {
        List<Statement> out = new ArrayList<>();
        out.add(stmt(call(sortFile, "begin")));
        for (Object k : onKeys) {
            boolean ascending;
            List<Call> calls;
            if (k instanceof io.proleap.cobol.asg.metamodel.procedure.sort.OnKey sk) {
                ascending = sk.getOnKeyType() == io.proleap.cobol.asg.metamodel.procedure.sort.OnKey.OnKeyType.ASCENDING;
                calls = sk.getKeyCalls();
            } else {
                var mk = (io.proleap.cobol.asg.metamodel.procedure.merge.OnKey) k;
                ascending = mk.getOnKeyType() == io.proleap.cobol.asg.metamodel.procedure.merge.OnKey.OnKeyType.ASCENDING;
                calls = mk.getKeyCalls();
            }
            for (Call key : calls) {
                out.add(stmt(call(sortFile.clone(), "key", c.sortKey(c.dataItem(key), key.getCtx(), collating), bool(ascending))));
            }
        }
        return out;
    }

    private static Expression sortFile(Call call, TranslationContext c) throws HandlerException {
        var f = c.file(call);
        if (!f.sort()) {
            throw HandlerException.unsupported(call.getName() + " is not an SD file");
        }
        return name(f.javaName());
    }

    /** A USING / GIVING file: sequential, or RELATIVE / INDEXED read and written in their order (not ACCESS RANDOM). */
    private static Expression plainFile(Call call, TranslationContext c) throws HandlerException {
        var f = c.file(call);
        if (f.sort()) {
            throw HandlerException.unsupported(call.getName() + " is an SD file");
        }
        if (f.keyed() && f.access() == TranslationContext.Access.RANDOM) {
            throw HandlerException.unsupported(call.getName() + ": USING / GIVING of a " + f.organization() + " file in ACCESS RANDOM");
        }
        return name(f.javaName());
    }

    /**
     * SORT as a stable sort. GnuCOBOL 3.2 breaks ties between equal keys by release order (libcob's
     * cob_file_sort_compare compares a per-record sequence number last), so WITH DUPLICATES IN ORDER
     * does not change its result.
     */
    private static List<Statement> sort(OpUnit u, TranslationContext c) throws HandlerException {
        var s = (io.proleap.cobol.asg.metamodel.procedure.sort.SortStatement) u.statement();
        var phrase = ((io.proleap.cobol.CobolParser.SortStatementContext) s.getCtx()).sortCollatingSequencePhrase();
        String collating = phrase == null ? null : collating(phrase.alphabetName(), phrase.sortCollatingAlphanumeric(),
                phrase.sortCollatingNational(), "SORT", c);
        Expression sf = sortFile(s.getFileCall(), c);
        List<Statement> out = new ArrayList<>(sortKeys(sf, s.getOnKeys(), collating, c));
        if (s.getInputProcedure() != null) {
            var p = s.getInputProcedure();
            out.add(performProcedure(p.getProcedureCall(), p.getInputThrough() == null ? null : p.getInputThrough().getProcedureCall(), c));
        } else {
            for (var using : s.getUsingPhrases()) {
                for (Call f : using.getFileCalls()) {
                    out.add(stmt(call(sf.clone(), "using", plainFile(f, c))));
                }
            }
        }
        out.add(stmt(call(sf.clone(), "sort")));
        output(sf, s.getOutputProcedure() == null ? null : s.getOutputProcedure().getProcedureCall(),
                s.getOutputProcedure() == null || s.getOutputProcedure().getOutputThrough() == null ? null
                        : s.getOutputProcedure().getOutputThrough().getProcedureCall(),
                s.getGivingPhrases().stream().flatMap(g -> g.getGivings().stream()).map(g -> g.getFileCall()).toList(), out, c);
        return out;
    }

    private static void output(Expression sf, Call procedure, Call thru, List<Call> givings, List<Statement> out, TranslationContext c)
            throws HandlerException {
        if (procedure != null) {
            out.add(performProcedure(procedure, thru, c));
            return;
        }
        NodeList<Expression> files = new NodeList<>();
        for (Call g : givings) {
            files.add(plainFile(g, c));
        }
        out.add(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(sf.clone(), "giving", files)));
    }

    private static List<Statement> merge(OpUnit u, TranslationContext c) throws HandlerException {
        var m = (io.proleap.cobol.asg.metamodel.procedure.merge.MergeStatement) u.statement();
        var phrase = ((io.proleap.cobol.CobolParser.MergeStatementContext) m.getCtx()).mergeCollatingSequencePhrase();
        String collating = phrase == null ? null : collating(phrase.alphabetName(), phrase.mergeCollatingAlphanumeric(),
                phrase.mergeCollatingNational(), "MERGE", c);
        Expression sf = sortFile(m.getFileCall(), c);
        List<Statement> out = new ArrayList<>(sortKeys(sf, m.getOnKeys(), collating, c));
        NodeList<Expression> inputs = new NodeList<>();
        for (var using : m.getUsingPhrases()) {
            for (Call f : using.getFileCalls()) {
                inputs.add(plainFile(f, c));
            }
        }
        out.add(stmt(new com.github.javaparser.ast.expr.MethodCallExpr(sf.clone(), "merge", inputs)));
        var op = m.getOutputProcedurePhrase();
        output(sf, op == null ? null : op.getProcedureCall(), op == null || op.getOutputThrough() == null ? null : op.getOutputThrough().getProcedureCall(),
                m.getGivingPhrases().stream().flatMap(g -> g.getGivings().stream()).map(g -> g.getFileCall()).toList(), out, c);
        return out;
    }

    private static List<Statement> release(OpUnit u, TranslationContext c) throws HandlerException {
        var r = (io.proleap.cobol.asg.metamodel.procedure.release.ReleaseStatement) u.statement();
        Operand.Field record = fileRecord(r.getRecordCall(), c);
        var file = c.fileOfRecord(record.item());
        if (!file.sort()) {
            throw HandlerException.unsupported("RELEASE of a record that is not a sort record");
        }
        List<Statement> out = new ArrayList<>();
        if (r.getContentCall() != null) {
            out.add(fromMove(record, c.operand(r.getContentCall()), c));
        }
        out.add(stmt(call(name(file.javaName()), "release")));
        return out;
    }

    private static List<Statement> returnRecord(OpUnit u, TranslationContext c) throws HandlerException {
        var r = (io.proleap.cobol.asg.metamodel.procedure.returnstmt.ReturnStatement) u.statement();
        Expression sf = sortFile(r.getFileCall(), c);
        BlockStmt atEnd = r.getAtEndPhrase() == null ? new BlockStmt() : c.nested(r.getAtEndPhrase());
        BlockStmt success = r.getNotAtEndPhrase() == null ? new BlockStmt() : c.nested(r.getNotAtEndPhrase());
        if (r.getInto() != null) {
            var f = c.file(r.getFileCall());
            Operand.Field record = new Operand.Field(f.records().getFirst());
            success.getStatements().addFirst(storeAlphanumericOrGroup(c.operand(r.getInto().getIntoCall()),
                    call(record.item().isGroup() ? c.field(record) : call(c.field(record), "view"), "get"), c));
        }
        Expression ret = call(sf, "returnRecord", bool(r.getAtEndPhrase() != null));
        return List.of(success.isEmpty() ? new IfStmt(ret, atEnd, null) : new IfStmt(ret, atEnd, success));
    }
}
