package transpiler.cobol2java.translate;

import io.proleap.cobol.asg.metamodel.ASGElement;
import io.proleap.cobol.asg.metamodel.Literal;
import io.proleap.cobol.asg.metamodel.procedure.StatementType;
import io.proleap.cobol.asg.metamodel.procedure.StatementTypeEnum;
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
import io.proleap.cobol.asg.metamodel.procedure.set.SetStatement;
import io.proleap.cobol.asg.metamodel.procedure.stop.StopStatement;
import io.proleap.cobol.asg.metamodel.procedure.subtract.SubtractStatement;
import io.proleap.cobol.asg.metamodel.valuestmt.LiteralValueStmt;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Facts describe an operation instance; mapping rules match on them with {@code when}.
 * The keys per operation are part of the mapping API (docs/mapping-api.md).
 */
public final class Facts {
    public static final Map<String, Set<String>> KEYS = Map.ofEntries(
            Map.entry("MOVE", Set.of("source_category", "target_category", "source_figurative")),
            Map.entry("ADD", Set.of("form", "rounded", "size_error")),
            Map.entry("SUBTRACT", Set.of("form", "rounded", "size_error")),
            Map.entry("MULTIPLY", Set.of("form", "rounded", "size_error")),
            Map.entry("DIVIDE", Set.of("form", "rounded", "size_error", "remainder")),
            Map.entry("COMPUTE", Set.of("rounded", "size_error", "receivers")),
            Map.entry("DISPLAY", Set.of("upon", "no_advancing", "operands")),
            Map.entry("IF", Set.of("has_else")),
            Map.entry("PERFORM", Set.of("form", "loop", "test", "thru")),
            Map.entry("STOP", Set.of("kind")),
            Map.entry("EXIT", Set.of("kind")),
            Map.entry("CALL", Set.of("program", "program_kind", "program_in_project", "using")),
            Map.entry("SET", Set.of("form")),
            Map.entry("EVALUATE", Set.of("subjects", "has_other", "has_thru")),
            Map.entry("ACCEPT", Set.of("from")),
            Map.entry("EXHIBIT", Set.of("named", "changed")));

    private Facts() {}

    /** Mapping-API name of a ProLeap statement type: the COBOL verb, with GO_BACK spelled GOBACK. */
    public static String operationName(StatementType type) {
        return type == StatementTypeEnum.GO_BACK ? "GOBACK" : type.toString();
    }

    public static Set<String> operations() {
        return Arrays.stream(StatementTypeEnum.values()).map(Facts::operationName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static Set<String> keys(String operation) {
        return KEYS.getOrDefault(operation, Set.of());
    }

    public static Map<String, Object> extract(OpUnit unit, TranslationContext ctx) {
        Map<String, Object> f = new LinkedHashMap<>();
        var s = unit.statement();
        switch (unit.operation()) {
            case "MOVE" -> {
                MoveStatement move = (MoveStatement) s;
                if (move.getMoveType() == MoveStatement.MoveType.MOVE_TO) {
                    Operand src = tryOperand(() -> Builtins.moveAll(move,
                            ctx.operand(move.getMoveToStatement().getSendingArea().getSendingAreaValueStmt())));
                    f.put("source_category", src == null ? "unsupported" : src.category());
                    f.put("source_figurative", src instanceof Operand.Figurative fig
                            ? fig.name().toLowerCase(Locale.ROOT).replace('-', '_') : null);
                    Operand dst = tryOperand(() -> ctx.operand(unit.receiver()));
                    f.put("target_category", dst == null ? "unsupported" : dst.category());
                } else if (unit.corresponding() != null && unit.corresponding().problem() == null) {
                    // One pair of MOVE CORRESPONDING: matched like a plain MOVE of the pair.
                    f.put("source_category", unit.corresponding().source().category());
                    f.put("target_category", unit.corresponding().target().category());
                    f.put("source_figurative", null);
                } else {
                    f.put("source_category", "corresponding");
                    f.put("target_category", "corresponding");
                    f.put("source_figurative", null);
                }
            }
            case "ADD" -> {
                AddStatement add = (AddStatement) s;
                f.put("form", switch (add.getAddType()) {
                    case TO -> "to";
                    case TO_GIVING -> "giving";
                    case CORRESPONDING -> "corresponding";
                });
                f.put("rounded", roundedOutsidePhrases(s.getCtx()));
                f.put("size_error", add.getOnSizeErrorPhrase() != null || add.getNotOnSizeErrorPhrase() != null);
            }
            case "SUBTRACT" -> {
                SubtractStatement sub = (SubtractStatement) s;
                f.put("form", switch (sub.getSubtractType()) {
                    case FROM -> "from";
                    case FROM_GIVING -> "giving";
                    case CORRESPONDING -> "corresponding";
                });
                f.put("rounded", roundedOutsidePhrases(s.getCtx()));
                f.put("size_error", sub.getOnSizeErrorPhrase() != null || sub.getNotOnSizeErrorPhrase() != null);
            }
            case "MULTIPLY" -> {
                MultiplyStatement mul = (MultiplyStatement) s;
                f.put("form", mul.getMultiplyType() == MultiplyStatement.MultiplyType.BY ? "by" : "giving");
                f.put("rounded", roundedOutsidePhrases(s.getCtx()));
                f.put("size_error", mul.getOnSizeErrorPhrase() != null || mul.getNotOnSizeErrorPhrase() != null);
            }
            case "DIVIDE" -> {
                DivideStatement div = (DivideStatement) s;
                f.put("form", switch (div.getDivideType()) {
                    case INTO -> "into";
                    case INTO_GIVING -> "into_giving";
                    case BY_GIVING -> "by_giving";
                });
                f.put("rounded", roundedOutsidePhrases(s.getCtx()));
                f.put("size_error", div.getOnSizeErrorPhrase() != null || div.getNotOnSizeErrorPhrase() != null);
                f.put("remainder", div.getRemainder() != null);
            }
            case "COMPUTE" -> {
                ComputeStatement c = (ComputeStatement) s;
                f.put("rounded", roundedOutsidePhrases(s.getCtx()));
                f.put("size_error", c.getOnSizeErrorPhrase() != null || c.getNotOnSizeErrorPhrase() != null);
                f.put("receivers", c.getStores().size());
            }
            case "DISPLAY" -> {
                DisplayStatement d = (DisplayStatement) s;
                f.put("upon", d.getUpon() == null ? null : ctx.device(Builtins.deviceName(d.getUpon())));
                f.put("no_advancing", d.getWith() != null);
                f.put("operands", d.getOperands().size());
            }
            case "IF" -> f.put("has_else", ((IfStatement) s).getElse() != null);
            case "PERFORM" -> {
                PerformStatement p = (PerformStatement) s;
                boolean inline = p.getPerformStatementType() == PerformStatement.PerformStatementType.INLINE;
                PerformType type = inline ? p.getPerformInlineStatement().getPerformType()
                        : p.getPerformProcedureStatement().getPerformType();
                f.put("form", inline ? "inline" : "procedure");
                f.put("loop", type == null ? "once" : type.getPerformTypeType().name().toLowerCase(Locale.ROOT));
                TestClause test = type == null ? null : switch (type.getPerformTypeType()) {
                    case UNTIL -> type.getUntil().getTestClause();
                    case VARYING -> type.getVarying().getTestClause();
                    case TIMES -> null;
                };
                f.put("test", test != null && test.getTestClauseType() == TestClause.TestClauseType.AFTER ? "after" : "before");
                f.put("thru", !inline && p.getPerformProcedureStatement().getCalls().size() > 1);
            }
            case "STOP" -> f.put("kind", switch (((StopStatement) s).getStopType()) {
                case STOP_RUN -> "run";
                case STOP_RUN_AND_DISPLAY -> "literal";
                case STOP_RUN_GIVING -> "giving";
            });
            case "EXIT" -> f.put("kind", exitKind(s));
            case "SET" -> {
                SetStatement set = (SetStatement) s;
                String form;
                if (set.getSetType() == SetStatement.SetType.BY) {
                    form = set.getSetBy().getSetByType() == io.proleap.cobol.asg.metamodel.procedure.set.SetBy.SetByType.UP ? "up_by" : "down_by";
                } else {
                    form = "to";
                    if (set.getSetTos().size() == 1 && set.getSetTos().getFirst().getValues().size() == 1) {
                        var value = set.getSetTos().getFirst().getValues().getFirst();
                        Boolean b = Builtins.booleanValue(value.getValueStmt());
                        if (b != null) {
                            form = b ? "true" : "false";
                        } else if (value.getValueType() == io.proleap.cobol.asg.metamodel.procedure.set.Value.ValueType.ON
                                || value.getValueType() == io.proleap.cobol.asg.metamodel.procedure.set.Value.ValueType.OFF) {
                            form = "on_off";
                        }
                    }
                }
                f.put("form", form);
            }
            case "EVALUATE" -> {
                EvaluateStatement e = (EvaluateStatement) s;
                f.put("subjects", 1 + e.getAlsoSelects().size());
                f.put("has_other", e.getWhenOther() != null);
                f.put("has_thru", e.getWhenPhrases().stream().flatMap(p -> p.getWhens().stream())
                        .anyMatch(w -> w.getCondition().getThrough() != null
                                || w.getAlsoConditions().stream().anyMatch(a -> a.getCondition().getThrough() != null)));
            }
            case "ACCEPT" -> {
                AcceptStatement a = (AcceptStatement) s;
                String from = acceptFrom(a);
                if (a.getAcceptType() == AcceptStatement.AcceptType.MNEMONIC) {
                    String device = ctx.device(Builtins.deviceName(a.getAcceptFromMnemonicStatement()));
                    from = device.equals("SYSIN") || device.equals("CONSOLE") ? "stdin" : "device";
                }
                f.put("from", from);
            }
            case "EXHIBIT" -> {
                var e = (io.proleap.cobol.CobolParser.ExhibitStatementContext) s.getCtx();
                f.put("named", e.NAMED() != null);
                f.put("changed", e.CHANGED() != null);
            }
            case "CALL" -> {
                CallStatement call = (CallStatement) s;
                var program = call.getProgramValueStmt();
                String name = null;
                if (program instanceof LiteralValueStmt lit && lit.getLiteral().getLiteralType() == Literal.LiteralType.NON_NUMERIC) {
                    name = lit.getLiteral().getNonNumericLiteral();
                }
                f.put("program", name);
                f.put("program_kind", name != null ? "literal" : "identifier");
                f.put("program_in_project", name != null && ctx.programClass(name).isPresent());
                f.put("using", call.getUsingPhrase() != null);
            }
            default -> {
                // No facts for other operations.
            }
        }
        return f;
    }

    /** EXIT kind: plain, program, paragraph, section, perform, perform_cycle (or invalid). */
    static String exitKind(io.proleap.cobol.asg.metamodel.procedure.Statement s) {
        var e = (io.proleap.cobol.CobolParser.ExitStatementContext) s.getCtx();
        if (e.PROGRAM() != null) {
            return "program";
        }
        if (e.SECTION() != null) {
            return "section";
        }
        String word = e.cobolWord() == null ? null : e.cobolWord().getText().toUpperCase(Locale.ROOT);
        if (e.PERFORM() != null) {
            return word == null ? "perform" : word.equals("CYCLE") ? "perform_cycle" : "invalid";
        }
        if (word != null) {
            return word.equals("PARAGRAPH") ? "paragraph" : "invalid";
        }
        return "plain";
    }

    /**
     * ACCEPT source: stdin (no FROM; FROM SYSIN / CONSOLE is resolved by the caller), date,
     * date_yyyymmdd, day, day_yyyyddd, day_of_week, time, other_date, escape_key, message_count.
     */
    static String acceptFrom(AcceptStatement a) {
        return switch (a.getAcceptType()) {
            case NO_FROM -> "stdin";
            case MNEMONIC -> "device";
            case FROM_ESCAPE_KEY -> "escape_key";
            case MESSAGE_COUNT -> "message_count";
            case DATE -> {
                var d = (io.proleap.cobol.CobolParser.AcceptFromDateStatementContext) a.getAcceptFromDateStatement().getCtx();
                if (d.DATE() != null && d.TODAYS_DATE() == null) {
                    yield d.YYYYMMDD() != null ? "date_yyyymmdd" : "date";
                }
                if (d.DAY() != null) {
                    yield d.YYYYDDD() != null ? "day_yyyyddd" : "day";
                }
                if (d.DAY_OF_WEEK() != null) {
                    yield "day_of_week";
                }
                yield d.TIME() != null ? "time" : "other_date";
            }
        };
    }

    private interface OperandSupplier {
        Operand get() throws HandlerException;
    }

    private static Operand tryOperand(OperandSupplier s) {
        try {
            return s.get();
        } catch (HandlerException | RuntimeException e) {
            return null;
        }
    }

    /** ROUNDED on any receiver of this statement, ignoring statements nested in SIZE ERROR phrases. */
    static boolean roundedOutsidePhrases(ParseTree tree) {
        if (tree instanceof TerminalNode t) {
            return t.getText().equalsIgnoreCase("ROUNDED");
        }
        if (tree instanceof ParserRuleContext rule && rule.children != null
                && !rule.getClass().getSimpleName().contains("SizeError")) {
            for (ParseTree child : rule.children) {
                if (roundedOutsidePhrases(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a keyword token appears anywhere in the element's parse tree. */
    public static boolean containsToken(ASGElement element, String keyword) {
        return containsToken(element.getCtx(), keyword);
    }

    public static boolean containsToken(ParseTree tree, String keyword) {
        if (tree instanceof TerminalNode t) {
            return t.getText().equalsIgnoreCase(keyword);
        }
        if (tree instanceof ParserRuleContext rule && rule.children != null) {
            for (ParseTree child : rule.children) {
                if (containsToken(child, keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a keyword is an immediate terminal child (ROUNDED on one receiver, PROGRAM on EXIT). */
    public static boolean hasDirectToken(ParserRuleContext ctx, String keyword) {
        if (ctx.children == null) {
            return false;
        }
        for (ParseTree child : ctx.children) {
            if (child instanceof TerminalNode t && t.getText().equalsIgnoreCase(keyword)) {
                return true;
            }
        }
        return false;
    }
}
