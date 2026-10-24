package transpiler.cobol2java.translate;

import static transpiler.cobol2java.translate.Ast.call;
import static transpiler.cobol2java.translate.Ast.create;
import static transpiler.cobol2java.translate.Ast.decimal;
import static transpiler.cobol2java.translate.Ast.integer;
import static transpiler.cobol2java.translate.Ast.name;
import static transpiler.cobol2java.translate.Ast.staticCall;
import static transpiler.cobol2java.translate.Ast.stmt;
import static transpiler.cobol2java.translate.Ast.str;
import static transpiler.cobol2java.translate.Ast.thisCall;
import static transpiler.cobol2java.translate.Ast.type;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.VoidType;
import io.proleap.cobol.CobolParser;
import io.proleap.cobol.asg.metamodel.Literal;
import io.proleap.cobol.asg.metamodel.ProgramUnit;
import io.proleap.cobol.asg.metamodel.Scope;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.call.DataDescriptionEntryCall;
import io.proleap.cobol.asg.metamodel.call.ProcedureCall;
import io.proleap.cobol.asg.metamodel.call.SectionCall;
import io.proleap.cobol.asg.metamodel.call.SpecialRegisterCall;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntry;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntryCondition;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntryGroup;
import io.proleap.cobol.asg.metamodel.data.datadescription.UsageClause;
import io.proleap.cobol.asg.metamodel.procedure.Paragraph;
import io.proleap.cobol.asg.metamodel.procedure.ProcedureDivision;
import io.proleap.cobol.asg.metamodel.procedure.Section;
import io.proleap.cobol.asg.metamodel.procedure.add.AddStatement;
import io.proleap.cobol.asg.metamodel.procedure.move.MoveStatement;
import io.proleap.cobol.asg.metamodel.procedure.perform.PerformProcedureStatement;
import io.proleap.cobol.asg.metamodel.procedure.subtract.SubtractStatement;
import io.proleap.cobol.asg.metamodel.valuestmt.ArithmeticValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.CallValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.ConditionValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.IntegerLiteralValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.LiteralValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.RelationConditionValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.ValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.arithmetic.Basis;
import io.proleap.cobol.asg.metamodel.valuestmt.arithmetic.MultDiv;
import io.proleap.cobol.asg.metamodel.valuestmt.arithmetic.MultDivs;
import io.proleap.cobol.asg.metamodel.valuestmt.arithmetic.PlusMinus;
import io.proleap.cobol.asg.metamodel.valuestmt.arithmetic.Powers;
import io.proleap.cobol.asg.metamodel.valuestmt.condition.AndOrCondition;
import io.proleap.cobol.asg.metamodel.valuestmt.condition.ClassCondition;
import io.proleap.cobol.asg.metamodel.valuestmt.condition.CombinableCondition;
import io.proleap.cobol.asg.metamodel.valuestmt.condition.ConditionNameReference;
import io.proleap.cobol.asg.metamodel.valuestmt.condition.SimpleCondition;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.Abbreviation;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.RelationalOperator.RelationalOperatorType;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.SignCondition;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import transpiler.cobol2java.cobol.Picture;
import transpiler.cobol2java.cobol.SourceMap;
import transpiler.cobol2java.decision.Candidate;
import transpiler.cobol2java.decision.Decision;
import transpiler.cobol2java.decision.Reason;
import transpiler.cobol2java.decision.SourceLocation;
import transpiler.cobol2java.mapping.MappingRegistry;
import transpiler.cobol2java.mapping.MappingRule;
import transpiler.cobol2java.mapping.Strategy;
import transpiler.cobol2java.session.Dialect;

/**
 * Translates one COBOL program unit into a JavaParser {@link CompilationUnit}.
 *
 * <p>Each statement is mapped through the registry. Statements that cannot be translated are
 * replaced in the AST by a placeholder and recorded as {@link Decision}s; the session refuses
 * to write the file until every placeholder has been resolved.
 */
public final class ProgramTranslator implements TranslationContext {
    /** Everything the translator needs to know about the program and its project. */
    public record Input(
            ProgramUnit unit,
            SourceMap sourceMap,
            String sourceFile,
            String programId,
            String packageName,
            String className,
            String runtimePackage,
            MappingRegistry registry,
            Builtins builtins,
            Function<String, Optional<String>> programDirectory,
            Dialect dialect) {}

    /** The translation output; {@code decisions} are not yet registered with ids. */
    public record Result(CompilationUnit compilationUnit, List<Decision> decisions, int statements, int translated) {}

    private final Input in;
    private final Map<DataDescriptionEntry, DataItem> items = new IdentityHashMap<>();
    private final List<DataItem> fieldOrder = new ArrayList<>();
    private final Set<String> fieldNames = new HashSet<>();
    private final Map<Object, String> procedureMethods = new IdentityHashMap<>();
    private final Map<String, Integer> freshCounters = new LinkedHashMap<>();
    private List<Decision> sink = new ArrayList<>();
    /** Program-level decisions (never discarded with a failed candidate), in source order with the rest. */
    private final List<Decision> root = sink;
    private final Set<String> dialectDecisions = new HashSet<>();
    /** Times an unset dialect setting was required; a rise during fact extraction blocks a unit. */
    private int dialectHits;
    // SPECIAL-NAMES
    private boolean decimalComma;
    private final Map<String, String> devices = new HashMap<>();
    private final Map<String, CobolParser.ClassClauseContext> classes = new HashMap<>();
    // Procedure structure and jumps
    private final Map<Scope, List<ParserRuleContext>> nextSentences = new IdentityHashMap<>();
    private final Set<ParserRuleContext> placedNextSentences = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<ParserRuleContext> allNextSentences = new ArrayList<>();
    private final Deque<String> sentenceLabels = new ArrayDeque<>();
    private final Deque<PerformFrame> performs = new ArrayDeque<>();
    private final Set<Paragraph> performedParagraphs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Section> exitSections = Collections.newSetFromMap(new IdentityHashMap<>());
    private Paragraph currentParagraph;
    private Section currentSection;
    private int statements;
    private int translated;

    public ProgramTranslator(Input input) {
        this.in = input;
    }

    public Result translate() {
        CompilationUnit cu = new CompilationUnit(in.packageName());
        cu.addImport(in.runtimePackage() + ".Accept");
        cu.addImport(in.runtimePackage() + ".AlphanumericField");
        cu.addImport(in.runtimePackage() + ".Arithmetic");
        cu.addImport(in.runtimePackage() + ".Clock");
        cu.addImport(in.runtimePackage() + ".CobolProgram");
        cu.addImport(in.runtimePackage() + ".Compare");
        cu.addImport(in.runtimePackage() + ".Display");
        cu.addImport(in.runtimePackage() + ".NumericField");
        cu.addImport(in.runtimePackage() + ".ProgramSupport");
        cu.addImport(in.runtimePackage() + ".Storage");
        cu.addImport(BigDecimal.class);

        ClassOrInterfaceDeclaration cls = cu.addClass(in.className(), Modifier.Keyword.PUBLIC, Modifier.Keyword.FINAL);
        cls.addImplementedType("CobolProgram");
        cls.setJavadocComment("Translated from " + in.sourceFile() + " (PROGRAM-ID " + in.programId() + ") by cobol-to-java.\n"
                + "Do not edit: regenerate from the COBOL source.");

        checkUnsupportedDivisions();
        buildDataModel();
        addFields(cls);
        addEntryPoints(cls);
        addProcedureDivision(cls);
        for (ParserRuleContext next : allNextSentences) {
            if (!placedNextSentences.contains(next)) {
                dataDecision(next, "PROCEDURE", "NEXT SENTENCE here cannot be translated (no enclosing sentence)");
            }
        }
        return new Result(cu, List.copyOf(sink), statements, translated);
    }

    // ------------------------------------------------------------------ data

    private void checkUnsupportedDivisions() {
        var env = in.unit().getEnvironmentDivision();
        if (env != null) {
            if (env.getInputOutputSection() != null) {
                dataDecision(env.getInputOutputSection().getCtx(), "ENVIRONMENT", "INPUT-OUTPUT SECTION (files) is not supported");
            }
            if (env.getSpecialNamesParagraph() != null) {
                specialNames((CobolParser.SpecialNamesParagraphContext) env.getSpecialNamesParagraph().getCtx());
            }
            var config = env.getConfigurationSection();
            if (config != null && config.getObjectComputerParagraph() != null) {
                objectComputer((CobolParser.ObjectComputerParagraphContext) config.getObjectComputerParagraph().getCtx());
            }
        }
        var data = in.unit().getDataDivision();
        if (data == null) {
            return;
        }
        if (data.getFileSection() != null) {
            dataDecision(data.getFileSection().getCtx(), "DATA", "FILE SECTION is not supported");
        }
        if (data.getLinkageSection() != null) {
            dataDecision(data.getLinkageSection().getCtx(), "DATA", "LINKAGE SECTION is not supported");
        }
        if (data.getLocalStorageSection() != null) {
            dataDecision(data.getLocalStorageSection().getCtx(), "DATA", "LOCAL-STORAGE SECTION is not supported");
        }
        if (data.getScreenSection() != null || data.getReportSection() != null || data.getCommunicationSection() != null) {
            dataDecision(data.getCtx(), "DATA", "SCREEN, REPORT and COMMUNICATION sections are not supported");
        }
    }

    /**
     * SPECIAL-NAMES clauses that only name things (mnemonics, classes, alphabets) or that are
     * implemented (DECIMAL-POINT IS COMMA) are accepted; the rest change semantics this
     * translator does not model and stay decisions. Read from the parse tree because ProLeap's
     * ASG keeps only one CLASS clause and no mnemonic names.
     */
    private void specialNames(CobolParser.SpecialNamesParagraphContext ctx) {
        for (var clause : ctx.specialNameClause()) {
            if (clause.decimalPointClause() != null) {
                decimalComma = true;
            } else if (clause.classClause() != null) {
                classes.put(clause.classClause().className().getText().toUpperCase(Locale.ROOT), clause.classClause());
            } else if (clause.alphabetClause() != null) {
                // An alphabet only matters where it is used (PROGRAM COLLATING SEQUENCE, CODE-SET), which are decisions.
                continue;
            } else if (clause.environmentSwitchNameClause() != null
                    && clause.environmentSwitchNameClause().environmentSwitchNameSpecialNamesStatusPhrase() == null) {
                var sw = clause.environmentSwitchNameClause();
                devices.put(sw.mnemonicName().getText().toUpperCase(Locale.ROOT), sw.environmentName().getText().toUpperCase(Locale.ROOT));
            } else {
                dataDecision(clause, "ENVIRONMENT", "SPECIAL-NAMES clause " + clause.getText() + " is not supported");
            }
        }
    }

    private void objectComputer(CobolParser.ObjectComputerParagraphContext ctx) {
        for (var clause : ctx.objectComputerClause()) {
            if (clause.collatingSequenceClause() != null || clause.characterSetClause() != null) {
                dataDecision(clause, "ENVIRONMENT", "OBJECT-COMPUTER clause " + clause.getText() + " is not supported");
            }
        }
    }

    private void buildDataModel() {
        fieldNames.add("returnCode");
        var data = in.unit().getDataDivision();
        if (data == null || data.getWorkingStorageSection() == null) {
            return;
        }
        for (DataDescriptionEntry e : data.getWorkingStorageSection().getRootDataDescriptionEntries()) {
            buildItem(e, null);
            layout(items.get(e), 0);
        }
    }

    private void buildItem(DataDescriptionEntry e, DataItem parent) {
        int line = in.sourceMap().originalLine(e.getCtx().getStart().getLine());
        String name = e.getName() == null ? "FILLER" : e.getName();
        if (e instanceof DataDescriptionEntryCondition c) {
            items.put(e, conditionItem(c, name, parent, line));
            return;
        }
        if (!(e instanceof DataDescriptionEntryGroup g)) {
            String what = switch (e.getDataDescriptionEntryType()) {
                case CONDITION -> "level-88 condition names are not supported";
                case RENAME -> "level-66 RENAMES is not supported";
                default -> "this data description entry is not supported";
            };
            DataItem item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, what, parent, line);
            items.put(e, item);
            dataDecision(e.getCtx(), "DATA", name + ": " + what);
            return;
        }
        String unsupported = unsupportedClauses(g);
        DataItem item;
        if (unsupported != null) {
            item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, unsupported, parent, line);
            dataDecision(e.getCtx(), "DATA", name + ": " + unsupported);
        } else if (g.getDataDescriptionEntries().stream().anyMatch(c -> c.getDataDescriptionEntryType()
                != DataDescriptionEntry.DataDescriptionEntryType.CONDITION)) {
            // Level-88 entries are conditions on their parent, not subordinate data items.
            if (g.getPictureClause() != null) {
                item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, "group item with PICTURE", parent, line);
                dataDecision(e.getCtx(), "DATA", name + ": a group item cannot have a PICTURE clause");
            } else if (g.getValueClause() != null) {
                item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, "VALUE on a group item is not supported", parent, line);
                dataDecision(e.getCtx(), "DATA", name + ": VALUE on a group item is not supported");
            } else {
                item = new DataItem(name, DataItem.Kind.GROUP, null, null, parent, line);
            }
        } else if (g.getPictureClause() == null) {
            item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, "elementary item without PICTURE", parent, line);
            dataDecision(e.getCtx(), "DATA", name + ": an elementary item without PICTURE is not supported");
        } else {
            Picture.Result pic = Picture.parse(g.getPictureClause().getPictureString());
            if (pic.picture().isEmpty()) {
                item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, pic.unsupportedReason(), parent, line);
                dataDecision(e.getCtx(), "DATA", name + ": " + pic.unsupportedReason());
            } else {
                Picture p = pic.picture().get();
                item = new DataItem(name, p instanceof Picture.Numeric ? DataItem.Kind.NUMERIC : DataItem.Kind.ALPHANUMERIC, p, null, parent, line);
                String valueProblem = initialValue(item, g);
                if (valueProblem != null) {
                    dataDecision(e.getCtx(), "DATA", name + ": " + valueProblem);
                }
            }
        }
        items.put(e, item);
        // Every elementary item (FILLER too: its bytes are initialised) and every named group gets a view.
        if (item.isAlphanumeric() || item.isNumeric() || (item.isGroup() && !item.isFiller())) {
            item.javaName(allocateField(Names.fieldName(name)));
            fieldOrder.add(item);
        }
        for (DataDescriptionEntry child : g.getDataDescriptionEntries()) {
            buildItem(child, item);
        }
    }

    /** Assigns byte offsets: elementary items take their PICTURE's size, groups the sum of their children. */
    private static int layout(DataItem item, int offset) {
        int size = switch (item.kind()) {
            case ALPHANUMERIC -> item.alphanumeric().length();
            case NUMERIC -> item.numeric().digits();
            case GROUP -> {
                int at = offset;
                for (DataItem child : item.children()) {
                    at = layout(child, at);
                }
                yield at - offset;
            }
            // Unsupported items have their own decision; nothing after them is written.
            case CONDITION, UNSUPPORTED -> 0;
        };
        item.layout(offset, size);
        return offset + size;
    }

    /** A level-88 entry; its values are checked against the parent when the condition is used. */
    private DataItem conditionItem(DataDescriptionEntryCondition c, String name, DataItem parent, int line) {
        String problem = null;
        if (parent != null && parent.kind() == DataItem.Kind.UNSUPPORTED) {
            // The parent's own decision covers it.
            return new DataItem(name, DataItem.Kind.UNSUPPORTED, null, "condition on unsupported item " + parent.cobolName(), parent, line);
        }
        if (parent == null || !(parent.isAlphanumeric() || parent.isNumeric())) {
            problem = "level-88 condition on " + (parent == null ? "nothing" : parent.category() + " item " + parent.cobolName())
                    + " is not supported (only elementary PIC X / PIC 9 items)";
        }
        List<DataItem.ConditionValue> values = new ArrayList<>();
        Operand whenFalse = null;
        if (problem == null) {
            try {
                for (var interval : c.getValueClause().getValueIntervals()) {
                    values.add(new DataItem.ConditionValue(conditionValue(interval.getFromValueStmt()),
                            interval.getToValueStmt() == null ? null : conditionValue(interval.getToValueStmt())));
                }
                var clause = (CobolParser.DataValueClauseContext) c.getValueClause().getCtx();
                if (clause.literal() != null) {
                    whenFalse = literal(clause.literal());
                }
            } catch (HandlerException ex) {
                problem = ex.kind() == HandlerException.Kind.DIALECT ? null : "VALUE " + ex.getMessage();
                if (problem == null) {
                    // The dialect decision is recorded; the item stays unusable without a second decision.
                    return new DataItem(name, DataItem.Kind.UNSUPPORTED, null, "needs Dialect." + ex.getMessage(), parent, line);
                }
            }
        }
        if (problem != null) {
            dataDecision(c.getCtx(), "DATA", name + ": " + problem);
            return new DataItem(name, DataItem.Kind.UNSUPPORTED, null, problem, parent, line);
        }
        DataItem item = new DataItem(name, DataItem.Kind.CONDITION, null, null, parent, line);
        item.condition(values, whenFalse);
        return item;
    }

    private Operand conditionValue(ValueStmt v) throws HandlerException {
        Operand o = operand(v);
        if (!(o instanceof Operand.AlphanumericLiteral || o instanceof Operand.NumericLiteral || o instanceof Operand.Figurative)) {
            throw HandlerException.unsupported(o.category() + " is not a literal");
        }
        return o;
    }

    private static String unsupportedClauses(DataDescriptionEntryGroup g) {
        UsageClause usage = g.getUsageClause();
        if (usage != null && usage.getUsageClauseType() != UsageClause.UsageClauseType.DISPLAY) {
            return "USAGE " + usage.getUsageClauseType() + " is not supported";
        }
        if (!g.getOccursClauses().isEmpty()) {
            return "OCCURS (tables) is not supported";
        }
        if (g.getRedefinesClause() != null) {
            return "REDEFINES is not supported";
        }
        if (g.getSignClause() != null) {
            return "SIGN clause is not supported";
        }
        if (g.getJustifiedClause() != null) {
            return "JUSTIFIED is not supported";
        }
        if (g.getBlankWhenZeroClause() != null) {
            return "BLANK WHEN ZERO is not supported";
        }
        if (g.getExternalClause() != null || g.getGlobalClause() != null) {
            return "EXTERNAL / GLOBAL is not supported";
        }
        if (g.getSynchronizedClause() != null) {
            return "SYNCHRONIZED is not supported";
        }
        return null;
    }

    /** Validates the VALUE clause; returns a problem description when it is not supported. */
    private String initialValue(DataItem item, DataDescriptionEntryGroup g) {
        var value = g.getValueClause();
        if (value == null) {
            return null;
        }
        if (value.getValueIntervals().size() != 1 || value.getValueIntervals().getFirst().getToValueStmt() != null) {
            return "VALUE with several values or THRU is not supported";
        }
        Operand o;
        try {
            o = operand(value.getValueIntervals().getFirst().getFromValueStmt());
        } catch (HandlerException e) {
            // An unset dialect setting already has its decision.
            return e.kind() == HandlerException.Kind.DIALECT ? null : "VALUE " + e.getMessage();
        }
        if (item.isAlphanumeric()) {
            switch (o) {
                case Operand.AlphanumericLiteral a -> item.initialValue(a.value());
                case Operand.Figurative f -> item.initialValue(repeat(f.pattern(), item.alphanumeric().length()));
                default -> {
                    return "VALUE " + o.category() + " for an alphanumeric item is not supported";
                }
            }
            return null;
        }
        Picture.Numeric n = item.numeric();
        switch (o) {
            case Operand.NumericLiteral lit -> {
                BigDecimal v = lit.value();
                int intDigits = v.precision() - v.scale();
                if (Math.max(intDigits, 0) > n.digits() - n.scale() || Math.max(v.stripTrailingZeros().scale(), 0) > n.scale()
                        || (!n.signed() && v.signum() < 0)) {
                    return "VALUE " + lit.text() + " does not fit PIC " + g.getPictureClause().getPictureString();
                }
                item.initialValue(v.toPlainString());
            }
            case Operand.Figurative f when f.isZero() -> item.initialValue("0");
            default -> {
                return "VALUE " + o.category() + " for a numeric item is not supported";
            }
        }
        return null;
    }

    private static String repeat(String pattern, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(pattern.charAt(i % pattern.length()));
        }
        return sb.toString();
    }

    private String allocateField(String base) {
        String name = base;
        for (int i = 2; !fieldNames.add(name); i++) {
            name = base + "_" + i;
        }
        return name;
    }

    /**
     * One {@code Storage} per level-01 group and a view per item over it, initialised in source
     * order as GnuCOBOL initialises WORKING-STORAGE (VALUE, else spaces / zeros by category). A
     * level-01 / level-77 elementary item is a standalone field with its own storage.
     */
    private void addFields(ClassOrInterfaceDeclaration cls) {
        cls.addFieldWithInitializer("NumericField", "returnCode", create("NumericField", integer(9), integer(0), Ast.bool(true)),
                Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        Map<DataItem, String> storages = new IdentityHashMap<>();
        for (DataItem item : fieldOrder) {
            DataItem record = item.record();
            Expression init;
            if (record == item && !item.isGroup()) {
                init = standalone(item);
            } else {
                String storage = storages.get(record);
                if (storage == null) {
                    storage = allocateField(Names.fieldName(record.cobolName()) + "Storage");
                    storages.put(record, storage);
                    cls.addFieldWithInitializer("Storage", storage, create("Storage", integer(Math.max(1, record.size()))),
                            Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL)
                            .setLineComment(" " + record.cobolName() + " (" + record.size() + " bytes)");
                }
                init = view(item, name(storage));
            }
            var field = cls.addFieldWithInitializer(item.isNumeric() ? "NumericField" : "AlphanumericField",
                    item.javaName(), init, Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
            field.setLineComment(" " + item.cobolName() + " (line " + item.line() + ")");
        }
    }

    private static Expression standalone(DataItem item) {
        if (item.isAlphanumeric()) {
            return item.initialValue() == null
                    ? create("AlphanumericField", integer(item.alphanumeric().length()))
                    : create("AlphanumericField", integer(item.alphanumeric().length()), str(item.initialValue()));
        }
        Picture.Numeric n = item.numeric();
        return item.initialValue() == null
                ? create("NumericField", integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()))
                : create("NumericField", integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()), str(item.initialValue()));
    }

    private static Expression view(DataItem item, Expression storage) {
        if (item.isGroup()) {
            // Group bytes are initialised through its elementary items.
            return create("AlphanumericField", storage, integer(item.offset()), integer(Math.max(1, item.size())));
        }
        Expression v;
        if (item.isAlphanumeric()) {
            v = create("AlphanumericField", storage, integer(item.offset()), integer(item.size()));
        } else {
            Picture.Numeric n = item.numeric();
            v = create("NumericField", storage, integer(item.offset()), integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()));
        }
        return item.initialValue() == null ? call(v, "initial") : call(v, "initial", str(item.initialValue()));
    }

    // ------------------------------------------------------------ structure

    private void addEntryPoints(ClassOrInterfaceDeclaration cls) {
        cls.addField(in.className(), "instance", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);

        MethodDeclaration instance = cls.addMethod("instance", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);
        instance.setType(in.className());
        instance.setJavadocComment("The program's single instance in this run unit (WORKING-STORAGE persists across CALLs).");
        instance.setBody(new BlockStmt(NodeList.nodeList(
                new IfStmt(new BinaryExpr(name("instance"), new NullLiteralExpr(), BinaryExpr.Operator.EQUALS),
                        stmt(new AssignExpr(name("instance"), create(in.className()), AssignExpr.Operator.ASSIGN)), null),
                new ReturnStmt(name("instance")))));

        MethodDeclaration main = cls.addMethod("main", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);
        main.addParameter(new Parameter(new ArrayType(type("String")), "args"));
        main.setBody(new BlockStmt(NodeList.nodeList(
                stmt(staticCall("System", "exit", staticCall("ProgramSupport", "runMain", call(null, "instance")))))));

        MethodDeclaration rc = cls.addMethod("returnCode", Modifier.Keyword.PUBLIC);
        rc.addMarkerAnnotation("Override");
        rc.setType("NumericField");
        rc.setBody(new BlockStmt(NodeList.nodeList(new ReturnStmt(name("returnCode")))));
    }

    private void addProcedureDivision(ClassOrInterfaceDeclaration cls) {
        ProcedureDivision pd = in.unit().getProcedureDivision();
        MethodDeclaration main = cls.addMethod("procedureDivision", Modifier.Keyword.PUBLIC);
        main.addMarkerAnnotation("Override");
        main.setType(new VoidType());
        BlockStmt body = new BlockStmt();
        main.setBody(body);
        if (pd == null) {
            return;
        }
        if (pd.getUsingClause() != null || pd.getGivingClause() != null) {
            dataDecision(pd.getCtx(), "PROCEDURE", "PROCEDURE DIVISION USING / RETURNING is not supported");
        }
        if (pd.getDeclaratives() != null) {
            dataDecision(pd.getDeclaratives().getCtx(), "PROCEDURE", "DECLARATIVES are not supported");
        }
        scanProcedures(pd);
        // Allocate method names first so PERFORM can reference later paragraphs.
        Set<String> methodNames = new HashSet<>(Set.of("procedureDivision", "returnCode", "instance", "main"));
        for (Paragraph p : pd.getRootParagraphs()) {
            procedureMethods.put(p, unique(methodNames, Names.methodName("para", p.getName())));
        }
        for (Section s : pd.getSections()) {
            procedureMethods.put(s, unique(methodNames, Names.methodName("section", s.getName())));
            for (Paragraph p : s.getParagraphs()) {
                procedureMethods.put(p, unique(methodNames, Names.methodName("para", p.getName())));
            }
        }
        translateInto(pd, body);
        // Control falls through paragraphs and sections in source order.
        for (Paragraph p : pd.getRootParagraphs()) {
            body.addStatement(stmt(thisCall(procedureMethods.get(p))));
            addProcedureMethod(cls, p, null, procedureMethods.get(p), new BlockStmt());
        }
        for (Section s : pd.getSections()) {
            body.addStatement(stmt(thisCall(procedureMethods.get(s))));
            BlockStmt sectionBody = new BlockStmt();
            for (Paragraph p : s.getParagraphs()) {
                addProcedureMethod(cls, p, s, procedureMethods.get(p), new BlockStmt());
            }
            addProcedureMethod(cls, s, s, procedureMethods.get(s), sectionBody);
            for (Paragraph p : s.getParagraphs()) {
                // A paragraph of a section with EXIT SECTION reports whether the section is left.
                sectionBody.addStatement(exitSections.contains(s)
                        ? new IfStmt(thisCall(procedureMethods.get(p)), new ReturnStmt(), null)
                        : stmt(thisCall(procedureMethods.get(p))));
            }
        }
    }

    private void addProcedureMethod(ClassOrInterfaceDeclaration cls, Scope scope, Section section, String method, BlockStmt body) {
        MethodDeclaration m = cls.addMethod(method, Modifier.Keyword.PRIVATE);
        boolean reportsExit = scope instanceof Paragraph && section != null && exitSections.contains(section);
        m.setType(reportsExit ? PrimitiveType.booleanType() : new VoidType());
        m.setLineComment(" " + (scope instanceof Section ? "SECTION " : "PARAGRAPH ") + ((io.proleap.cobol.asg.metamodel.NamedElement) scope).getName());
        currentParagraph = scope instanceof Paragraph p ? p : null;
        currentSection = section;
        translateInto(scope, body);
        currentParagraph = null;
        currentSection = null;
        if (reportsExit) {
            body.addStatement(new ReturnStmt(Ast.bool(false)));
        }
        m.setBody(body);
    }

    /**
     * Finds what needs procedure-level context before translation: paragraphs that are PERFORMed
     * on their own, sections left by EXIT SECTION from a paragraph, and standalone NEXT SENTENCE
     * statements (ProLeap has no ASG element for them, so they are placed from the parse tree).
     */
    private void scanProcedures(ProcedureDivision pd) {
        var registry = in.unit().getProgram().getASGElementRegistry();
        walk(pd.getCtx(), tree -> {
            if (tree instanceof CobolParser.PerformProcedureStatementContext perform
                    && registry.getASGElement(perform) instanceof PerformProcedureStatement proc) {
                for (Call call : proc.getCalls()) {
                    if (call.unwrap() instanceof ProcedureCall pc && pc.getParagraph() != null) {
                        performedParagraphs.add(pc.getParagraph());
                    }
                }
            } else if (tree instanceof CobolParser.NextSentenceStatementContext next) {
                allNextSentences.add(next);
                for (ParserRuleContext up = next.getParent(); up != null; up = up.getParent()) {
                    if (registry.getASGElement(up) instanceof Scope owner) {
                        nextSentences.computeIfAbsent(owner, k -> new ArrayList<>()).add(next);
                        break;
                    }
                }
            }
        });
        for (Section s : pd.getSections()) {
            for (Paragraph p : s.getParagraphs()) {
                walk(p.getCtx(), tree -> {
                    if (tree instanceof CobolParser.ExitStatementContext exit && exit.SECTION() != null) {
                        exitSections.add(s);
                    }
                });
            }
        }
    }

    private static void walk(ParseTree tree, java.util.function.Consumer<ParseTree> visit) {
        visit.accept(tree);
        for (int i = 0; i < tree.getChildCount(); i++) {
            walk(tree.getChild(i), visit);
        }
    }

    private static String unique(Set<String> used, String base) {
        String name = base;
        for (int i = 2; !used.add(name); i++) {
            name = base + "_" + i;
        }
        return name;
    }

    // ------------------------------------------------------------ statements

    private void translateInto(Scope scope, BlockStmt block) {
        // Statements and standalone NEXT SENTENCEs, in source order.
        List<Object> items = new ArrayList<>(scope.getStatements());
        items.addAll(nextSentences.getOrDefault(scope, List.of()));
        items.sort(java.util.Comparator.comparingInt(o -> ctxOf(o).getStart().getTokenIndex()));
        if (!(scope instanceof Paragraph || scope instanceof Section || scope instanceof ProcedureDivision)) {
            for (Object item : items) {
                translateItem(item, block);
            }
            return;
        }
        // A sentence with NEXT SENTENCE becomes a labeled block that NEXT SENTENCE breaks out of.
        for (int i = 0; i < items.size(); ) {
            CobolParser.SentenceContext sentence = sentenceOf(ctxOf(items.get(i)));
            int j = i;
            while (j < items.size() && sentenceOf(ctxOf(items.get(j))) == sentence) {
                j++;
            }
            List<Object> group = items.subList(i, j);
            if (sentence != null && hasNextSentence(sentence)) {
                String label = fresh("sentence");
                BlockStmt inner = new BlockStmt();
                sentenceLabels.push(label);
                try {
                    group.forEach(item -> translateItem(item, inner));
                } finally {
                    sentenceLabels.pop();
                }
                block.addStatement(new LabeledStmt(label, inner));
            } else {
                group.forEach(item -> translateItem(item, block));
            }
            i = j;
        }
    }

    private static ParserRuleContext ctxOf(Object item) {
        return item instanceof io.proleap.cobol.asg.metamodel.procedure.Statement s ? s.getCtx() : (ParserRuleContext) item;
    }

    private static CobolParser.SentenceContext sentenceOf(ParserRuleContext ctx) {
        for (ParserRuleContext c = ctx; c != null; c = c.getParent()) {
            if (c instanceof CobolParser.SentenceContext s) {
                return s;
            }
        }
        return null;
    }

    private static boolean hasNextSentence(ParseTree tree) {
        if (tree instanceof CobolParser.NextSentenceStatementContext
                || (tree instanceof CobolParser.IfThenContext t && t.NEXT() != null)
                || (tree instanceof CobolParser.IfElseContext e && e.NEXT() != null)) {
            return true;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (hasNextSentence(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private void translateItem(Object item, BlockStmt block) {
        if (item instanceof io.proleap.cobol.asg.metamodel.procedure.Statement s) {
            translateStatement(s, block);
            return;
        }
        // A standalone NEXT SENTENCE: structural, like the end of an IF branch.
        ParserRuleContext next = (ParserRuleContext) item;
        statements++;
        try {
            block.addStatement(jump(Jump.NEXT_SENTENCE));
            placedNextSentences.add(next);
            translated++;
        } catch (HandlerException e) {
            placedNextSentences.add(next);
            dataDecision(next, "PROCEDURE", e.getMessage());
            statements--;
        }
    }

    private void translateStatement(io.proleap.cobol.asg.metamodel.procedure.Statement s, BlockStmt block) {
        String op = Facts.operationName(s.getStatementType());
        SourceLocation where = location(s.getCtx());
        List<OpUnit> units = new ArrayList<>();
        if (s instanceof MoveStatement move && move.getMoveType() == MoveStatement.MoveType.MOVE_TO) {
            for (Call receiver : move.getMoveToStatement().getReceivingAreaCalls()) {
                units.add(new OpUnit(op, s, receiver, where));
            }
        } else if (s instanceof MoveStatement move) {
            var corr = move.getMoveCorrespondingToStatement();
            for (Call receiver : corr.getReceivingAreaCalls()) {
                corresponding(corr.getMoveToCorrespondingSendingArea().getSendingAreaCall(), receiver, false, false)
                        .forEach(pair -> units.add(new OpUnit(op, s, receiver, where, pair)));
            }
        } else if (s instanceof AddStatement add && add.getAddType() == AddStatement.AddType.CORRESPONDING) {
            var corr = add.getAddCorrespondingStatement();
            correspondingArithmetic(add.getOnSizeErrorPhrase() != null || add.getNotOnSizeErrorPhrase() != null,
                    corr.getFromCall(), corr.getTo().getToCall(), corr.getTo().isRounded())
                    .forEach(pair -> units.add(new OpUnit(op, s, null, where, pair)));
        } else if (s instanceof SubtractStatement sub && sub.getSubtractType() == SubtractStatement.SubtractType.CORRESPONDING) {
            var corr = sub.getSubtractCorrespondingStatement();
            correspondingArithmetic(sub.getOnSizeErrorPhrase() != null || sub.getNotOnSizeErrorPhrase() != null,
                    corr.getSubtrahendCall(), corr.getMinuend().getMinuendCall(), corr.getMinuend().isRounded())
                    .forEach(pair -> units.add(new OpUnit(op, s, null, where, pair)));
        } else {
            units.add(new OpUnit(op, s, null, where));
        }
        for (OpUnit unit : units) {
            translateUnit(unit, block);
        }
    }

    private List<OpUnit.Corresponding> correspondingArithmetic(boolean sizeError, Call from, Call to, boolean rounded) {
        if (sizeError) {
            // One SIZE ERROR phrase covers every pair; separate units would run it once per pair.
            return List.of(OpUnit.Corresponding.unsupported("CORRESPONDING with ON SIZE ERROR is not supported"));
        }
        return corresponding(from, to, true, rounded);
    }

    /**
     * The pairs of corresponding items of two groups: same name and the same chain of
     * intermediate group names; for ADD / SUBTRACT both elementary numeric, for MOVE at least
     * one elementary. FILLER and condition-names never correspond. A statement whose pairs
     * cannot all be translated becomes a single unsupported unit.
     */
    private List<OpUnit.Corresponding> corresponding(Call from, Call to, boolean arithmetic, boolean rounded) {
        DataItem a = groupOf(from);
        DataItem b = groupOf(to);
        if (a == null || b == null) {
            return List.of(OpUnit.Corresponding.unsupported("CORRESPONDING operands must be group items"));
        }
        List<OpUnit.Corresponding> pairs = new ArrayList<>();
        String problem = pairUp(a, b, arithmetic, rounded, pairs);
        return problem == null ? pairs : List.of(OpUnit.Corresponding.unsupported(problem));
    }

    private DataItem groupOf(Call call) {
        if (call.unwrap() instanceof DataDescriptionEntryCall d) {
            DataItem item = items.get(d.getDataDescriptionEntry());
            return item != null && item.isGroup() ? item : null;
        }
        return null;
    }

    private static String pairUp(DataItem a, DataItem b, boolean arithmetic, boolean rounded, List<OpUnit.Corresponding> out) {
        for (DataItem ca : a.children()) {
            if (ca.isFiller() || ca.isCondition()) {
                continue;
            }
            List<DataItem> matches = b.children().stream()
                    .filter(cb -> !cb.isFiller() && !cb.isCondition() && cb.cobolName().equalsIgnoreCase(ca.cobolName())).toList();
            if (matches.isEmpty()) {
                continue;
            }
            if (matches.size() > 1) {
                return "CORRESPONDING: " + ca.cobolName() + " is not unique in " + b.cobolName();
            }
            DataItem cb = matches.getFirst();
            if (ca.kind() == DataItem.Kind.UNSUPPORTED || cb.kind() == DataItem.Kind.UNSUPPORTED) {
                return "CORRESPONDING: " + ca.cobolName() + " is not supported";
            }
            if (ca.isGroup() && cb.isGroup()) {
                String problem = pairUp(ca, cb, arithmetic, rounded, out);
                if (problem != null) {
                    return problem;
                }
            } else if (arithmetic) {
                if (ca.isNumeric() && cb.isNumeric()) {
                    out.add(new OpUnit.Corresponding(ca, cb, rounded, null));
                }
            } else if (ca.isGroup() || cb.isGroup()) {
                return "CORRESPONDING: " + ca.cobolName() + " pairs a group with an elementary item (group moves are not supported)";
            } else {
                out.add(new OpUnit.Corresponding(ca, cb, false, null));
            }
        }
        return null;
    }

    private void translateUnit(OpUnit unit, BlockStmt block) {
        statements++;
        int hits = dialectHits;
        Map<String, Object> facts = Facts.extract(unit, this);
        if (dialectHits > hits) {
            // Its facts depend on an unset dialect setting; that decision stands for the unit.
            block.addStatement(placeholder(unit, "needs Dialect"));
            return;
        }
        MappingRegistry.Selection selection = in.registry().select(unit.operation(), facts);
        if (!selection.matched()) {
            unresolved(unit, block, facts, Reason.MISSING_MAPPING, null,
                    "no mapping rule matches " + unit.operation() + " with facts " + facts, candidatesFor(unit, null));
            return;
        }
        if (selection.ambiguous()) {
            List<Handler> tied = new ArrayList<>();
            for (MappingRule r : selection.tied()) {
                if (r.strategy() == Strategy.BUILTIN) {
                    tied.add(in.builtins().handler(r.handler()));
                }
            }
            unresolved(unit, block, facts, Reason.AMBIGUOUS_MAPPING, null,
                    "rules " + selection.tied().stream().map(MappingRule::id).toList() + " tie at priority "
                            + selection.winner().priority(), candidates(unit, tied));
            return;
        }
        MappingRule rule = selection.winner();
        switch (rule.strategy()) {
            case UNRESOLVED -> unresolved(unit, block, facts, Reason.MISSING_MAPPING, rule,
                    "mapping rule '" + rule.id() + "' leaves " + unit.operation() + " unresolved", candidatesFor(unit, null));
            case CALLBACK -> {
                Decision d = newDecision(unit, facts, Reason.CALLBACK_REQUIRED, rule,
                        "mapping rule '" + rule.id() + "' delegates to callback " + rule.callback().function(),
                        List.of(), Map.of(), block);
                sink.add(d);
            }
            case BUILTIN -> {
                Handler handler = in.builtins().handler(rule.handler());
                List<Decision> nested = new ArrayList<>();
                try {
                    List<Statement> out = generate(handler, unit, nested);
                    out.forEach(block::addStatement);
                    sink.addAll(nested);
                    translated++;
                } catch (HandlerException e) {
                    switch (e.kind()) {
                        case NOT_APPLICABLE -> unresolved(unit, block, facts, Reason.INVALID_MAPPING, rule,
                                "rule '" + rule.id() + "' selects handler '" + handler.id() + "', which does not apply: " + e.getMessage(),
                                candidatesFor(unit, handler));
                        case UNSUPPORTED -> unresolved(unit, block, facts, Reason.UNSUPPORTED_CONSTRUCT, rule, e.getMessage(), List.of());
                        case MISSING_TARGET -> unresolved(unit, block, facts, Reason.MISSING_TARGET_API, rule, e.getMessage(), List.of());
                        // The dialect:<field> decision is already recorded once for the program.
                        case DIALECT -> block.addStatement(placeholder(unit, "needs Dialect." + e.getMessage()));
                    }
                }
            }
        }
    }

    /** Generates with a handler, collecting decisions raised by nested statements into {@code nested}. */
    private List<Statement> generate(Handler handler, OpUnit unit, List<Decision> nested) throws HandlerException {
        List<Decision> saved = sink;
        int savedStatements = statements;
        int savedTranslated = translated;
        sink = nested;
        try {
            return handler.generate(unit, this);
        } catch (HandlerException | RuntimeException e) {
            statements = savedStatements;
            translated = savedTranslated;
            throw e;
        } finally {
            sink = saved;
        }
    }

    private record Generated(Candidate candidate, List<Statement> statements, List<Decision> nested) {}

    /** Every builtin handler for the operation that can translate this instance (except {@code excluded}). */
    private List<Generated> candidatesFor(OpUnit unit, Handler excluded) {
        List<Handler> hs = in.builtins().handlersFor(unit.operation()).stream().filter(h -> h != excluded).toList();
        return candidates(unit, hs);
    }

    private List<Generated> candidates(OpUnit unit, List<Handler> handlers) {
        List<Generated> out = new ArrayList<>();
        for (Handler h : handlers) {
            List<Decision> nested = new ArrayList<>();
            int savedStatements = statements;
            int savedTranslated = translated;
            try {
                List<Statement> stmts = generate(h, unit, nested);
                String preview = String.join("\n", stmts.stream().map(Statement::toString).toList());
                out.add(new Generated(new Candidate("builtin:" + h.id(), h.id(), h.description(), preview), stmts, nested));
            } catch (HandlerException e) {
                // Not a viable candidate for this instance.
            } finally {
                // Candidate exploration must not change the program's statement counts.
                statements = savedStatements;
                translated = savedTranslated;
            }
        }
        return out;
    }

    private void unresolved(OpUnit unit, BlockStmt block, Map<String, Object> facts, Reason reason, MappingRule rule,
            String message, List<Generated> generated) {
        Map<String, Generated> plans = new LinkedHashMap<>();
        generated.forEach(g -> plans.put(g.candidate().id(), g));
        sink.add(newDecision(unit, facts, reason, rule, message, generated.stream().map(Generated::candidate).toList(), plans, block));
    }

    private static EmptyStmt placeholder(OpUnit unit, String note) {
        EmptyStmt placeholder = new EmptyStmt();
        placeholder.setComment(new LineComment(" UNRESOLVED " + unit.operation() + (note == null ? "" : " (" + note + ")") + ": "
                + unit.location().text().replace('\n', ' ')));
        return placeholder;
    }

    private Decision newDecision(OpUnit unit, Map<String, Object> facts, Reason reason, MappingRule rule, String message,
            List<Candidate> candidates, Map<String, Generated> plans, BlockStmt block) {
        EmptyStmt placeholder = placeholder(unit, null);
        block.addStatement(placeholder);
        Decision.Plan plan = candidateId -> {
            Generated g = plans.get(candidateId);
            if (g == null) {
                throw new IllegalArgumentException("no plan for candidate " + candidateId);
            }
            int index = block.getStatements().indexOf(placeholder);
            if (index < 0) {
                throw new IllegalStateException("placeholder for " + candidateId + " is no longer in the AST");
            }
            block.getStatements().remove(index);
            block.getStatements().addAll(index, g.statements());
            return g.nested();
        };
        return new Decision(reason, unit.operation(), message, unit.location(), in.programId(), facts,
                rule == null ? null : rule.id(), candidates, rule == null ? null : rule.callback(), plan);
    }

    /** Records a declaration-level construct that blocks translation (no candidates). */
    private void dataDecision(ParserRuleContext ctx, String operation, String message) {
        statements++;
        Decision.Plan none = id -> {
            throw new IllegalArgumentException("decision has no candidates");
        };
        sink.add(new Decision(Reason.UNSUPPORTED_CONSTRUCT, operation, message, location(ctx), in.programId(),
                Map.of(), null, List.of(), null, none));
    }

    /**
     * Records the program's single decision for an unset dialect setting. It has no candidates:
     * the project must set the field and translate again.
     */
    private void dialectDecision(String field, SourceLocation where, String usage) {
        if (!dialectDecisions.add(field)) {
            return;
        }
        Decision.Plan none = id -> {
            throw new IllegalArgumentException("decision has no candidates");
        };
        root.add(new Decision(Reason.MISSING_MAPPING, "dialect:" + field, usage + " depends on Dialect." + field
                + ", which the project leaves unset", where, in.programId(), Map.of("field", field), null, List.of(), null, none));
    }

    @Override
    public Dialect dialect() {
        return in.dialect();
    }

    @Override
    public <T> T require(String field, Optional<T> value, ParserRuleContext where, String usage) throws HandlerException {
        if (value.isPresent()) {
            return value.get();
        }
        dialectHits++;
        dialectDecision(field, location(where), usage);
        throw new HandlerException(HandlerException.Kind.DIALECT, field);
    }

    private SourceLocation location(ParserRuleContext ctx) {
        var start = ctx.getStart();
        var stop = ctx.getStop() == null ? start : ctx.getStop();
        String text = start.getInputStream().getText(Interval.of(start.getStartIndex(), stop.getStopIndex()));
        if (text.length() > 200) {
            text = text.substring(0, 200) + "...";
        }
        return new SourceLocation(in.sourceFile(), in.sourceMap().originalLine(start.getLine()),
                start.getCharPositionInLine() + 1, text.strip());
    }

    // ------------------------------------------------- TranslationContext API

    @Override
    public Operand operand(ValueStmt v) throws HandlerException {
        return switch (v) {
            case CallValueStmt c -> operand(c.getCall());
            case LiteralValueStmt l -> literal(l.getLiteral());
            case IntegerLiteralValueStmt i -> new Operand.NumericLiteral(i.getLiteral().getValue(), i.getLiteral().getCtx().getText());
            case ArithmeticValueStmt a -> {
                ValueStmt single = trivial(a);
                yield single == null ? new Operand.Arithmetic(a) : operand(single);
            }
            default -> throw HandlerException.unsupported("operand " + v.getCtx().getText() + " is not supported");
        };
    }

    /** The single basis of an arithmetic expression without operators, or null. */
    private static ValueStmt trivial(ArithmeticValueStmt a) {
        MultDivs md = a.getMultDivs();
        if (!a.getPlusMinus().isEmpty() || !md.getMultDivs().isEmpty()) {
            return null;
        }
        Powers p = md.getPowers();
        if (!p.getPowers().isEmpty() || p.getPowersType() != null) {
            return null;
        }
        return p.getBasis().getBasisValueStmt();
    }

    private Operand literal(Literal lit) throws HandlerException {
        return switch (lit.getLiteralType()) {
            case NUMERIC -> numericLiteral(lit.getCtx().getText());
            case NON_NUMERIC -> nonNumericLiteral(lit.getCtx().getText());
            case FIGURATIVE_CONSTANT -> {
                var fc = lit.getFigurativeConstant();
                if (fc.getFigurativeConstantType() == io.proleap.cobol.asg.metamodel.FigurativeConstant.FigurativeConstantType.ALL) {
                    yield all(fc.getLiteral() == null ? null : literal(fc.getLiteral()), lit.getCtx());
                }
                yield figurative(fc.getFigurativeConstantType().name(), lit.getCtx());
            }
            default -> throw HandlerException.unsupported("literal " + lit.getCtx().getText() + " is not supported");
        };
    }

    /** A literal read from the parse tree (where ProLeap builds no ASG element, e.g. WHEN SET TO FALSE). */
    private Operand literal(CobolParser.LiteralContext ctx) throws HandlerException {
        if (ctx.NONNUMERICLITERAL() != null) {
            return nonNumericLiteral(ctx.getText());
        }
        if (ctx.numericLiteral() != null) {
            return numericLiteral(ctx.getText());
        }
        if (ctx.figurativeConstant() != null) {
            var fc = ctx.figurativeConstant();
            if (fc.ALL() != null) {
                return all(literal(fc.literal()), ctx);
            }
            return figurative(fc.getText().toUpperCase(Locale.ROOT).replace('-', '_'), ctx);
        }
        throw HandlerException.unsupported("literal " + ctx.getText() + " is not supported");
    }

    /**
     * Numeric literal text; ProLeap gives no value for a decimal comma, so the text is read
     * here. With DECIMAL-POINT IS COMMA the comma is the decimal point and a period is invalid.
     */
    private Operand numericLiteral(String text) throws HandlerException {
        if (text.toUpperCase(Locale.ROOT).contains("E")) {
            throw HandlerException.unsupported("floating-point literal " + text + " is not supported");
        }
        if (decimalComma ? text.contains(".") : text.contains(",")) {
            throw HandlerException.unsupported("numeric literal " + text + " does not match the "
                    + (decimalComma ? "DECIMAL-POINT IS COMMA" : "standard decimal point") + " convention");
        }
        try {
            return new Operand.NumericLiteral(new BigDecimal(text.replace(',', '.')), text);
        } catch (NumberFormatException e) {
            throw HandlerException.unsupported("numeric literal " + text + " is not supported");
        }
    }

    private static Operand nonNumericLiteral(String text) throws HandlerException {
        char q = text.charAt(0);
        if (q != '"' && q != '\'') {
            throw HandlerException.unsupported("literal " + text + " (hex/national/other prefix) is not supported");
        }
        return new Operand.AlphanumericLiteral(text.substring(1, text.length() - 1).replace("" + q + q, "" + q));
    }

    /** ZERO, SPACE, QUOTE, HIGH-VALUE, LOW-VALUE (any spelling); HIGH/LOW-VALUE depend on the encoding. */
    private Operand figurative(String type, ParserRuleContext where) throws HandlerException {
        return switch (type) {
            case "ZERO", "ZEROS", "ZEROES" -> new Operand.Figurative("0", "ZERO");
            case "SPACE", "SPACES" -> new Operand.Figurative(" ", "SPACE");
            case "QUOTE", "QUOTES" -> new Operand.Figurative("\"", "QUOTE");
            case "HIGH_VALUE", "HIGH_VALUES" -> {
                require("encoding", in.dialect().encoding(), where, "HIGH-VALUES");
                // The highest character position, X'FF', in both ASCII and EBCDIC code pages.
                yield new Operand.Figurative("\u00ff", "HIGH-VALUE");
            }
            case "LOW_VALUE", "LOW_VALUES" -> {
                require("encoding", in.dialect().encoding(), where, "LOW-VALUES");
                yield new Operand.Figurative("\u0000", "LOW-VALUE");
            }
            default -> throw HandlerException.unsupported("figurative constant " + type + " is not supported");
        };
    }

    /** {@code ALL literal}: a nonnumeric literal repeated; {@code ALL ZERO} and the like are the figurative itself. */
    private static Operand all(Operand inner, ParserRuleContext where) throws HandlerException {
        return switch (inner) {
            case Operand.AlphanumericLiteral a when !a.value().isEmpty() -> new Operand.Figurative(a.value(), "ALL");
            case Operand.Figurative f -> f;
            case null, default -> throw HandlerException.unsupported("ALL " + where.getText() + " is not supported (only ALL with a nonnumeric literal)");
        };
    }

    @Override
    public Operand operand(Call call) throws HandlerException {
        return switch (call.unwrap()) {
            case DataDescriptionEntryCall d -> fieldOperand(items.get(d.getDataDescriptionEntry()), call);
            // ProLeap leaves names qualified through more than one level unresolved.
            case Call u when u.getCallType() == Call.CallType.UNDEFINED_CALL -> fieldOperand(resolveQualified(call), call);
            case SpecialRegisterCall s when s.getSpecialRegisterType() == SpecialRegisterCall.SpecialRegisterType.RETURN_CODE ->
                    new Operand.ReturnCode();
            default -> throw HandlerException.unsupported(call.getName() + " (" + call.getCallType()
                    + ") is not supported: tables, reference modification, functions and special registers other than RETURN-CODE");
        };
    }

    /**
     * Resolves {@code A OF B OF C} (qualifiers may skip levels) to the single data item named A
     * whose ancestors include B, then C, in that order; null when there is none or several.
     */
    private DataItem resolveQualified(Call call) throws HandlerException {
        var q = firstOf(call.getCtx(), CobolParser.QualifiedDataNameFormat1Context.class);
        if (q == null || q.inFile() != null || q.qualifiedInData().stream().anyMatch(x -> x.inData() == null)) {
            throw HandlerException.unsupported(call.getCtx().getText() + " cannot be resolved");
        }
        String name = (q.dataName() != null ? q.dataName() : q.conditionName()).getText();
        List<String> qualifiers = q.qualifiedInData().stream().map(x -> x.inData().dataName().getText()).toList();
        DataItem found = null;
        for (DataItem item : items.values()) {
            if (!item.cobolName().equalsIgnoreCase(name)) {
                continue;
            }
            int next = 0;
            for (DataItem up = item.parent(); up != null && next < qualifiers.size(); up = up.parent()) {
                if (up.cobolName().equalsIgnoreCase(qualifiers.get(next))) {
                    next++;
                }
            }
            if (next == qualifiers.size()) {
                if (found != null) {
                    throw HandlerException.unsupported(call.getCtx().getText() + " is ambiguous");
                }
                found = item;
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static <T> T firstOf(ParseTree tree, Class<T> type) {
        if (type.isInstance(tree)) {
            return (T) tree;
        }
        for (int i = 0; tree != null && i < tree.getChildCount(); i++) {
            T found = firstOf(tree.getChild(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Operand fieldOperand(DataItem item, Call call) throws HandlerException {
        if (item == null) {
            throw HandlerException.unsupported(call.getName() + " is not a WORKING-STORAGE item");
        }
        if (item.kind() == DataItem.Kind.UNSUPPORTED) {
            throw HandlerException.unsupported(item.cobolName() + ": " + item.unsupportedReason());
        }
        if (item.kind() == DataItem.Kind.GROUP) {
            throw HandlerException.unsupported("reference to group item " + item.cobolName() + " is not supported");
        }
        if (call.unwrap() instanceof io.proleap.cobol.asg.metamodel.call.TableCall || hasReferenceModification(call)) {
            throw HandlerException.unsupported(call.getName() + ": subscripts and reference modification are not supported");
        }
        return new Operand.Field(item);
    }

    private static boolean hasReferenceModification(Call call) {
        return call.getCtx() instanceof ParserRuleContext ctx && ctx.getParent() instanceof CobolParser.QualifiedDataNameContext
                ? false : containsType(call.getCtx(), CobolParser.ReferenceModifierContext.class);
    }

    private static boolean containsType(ParseTree tree, Class<?> type) {
        if (type.isInstance(tree)) {
            return true;
        }
        for (int i = 0; tree != null && i < tree.getChildCount(); i++) {
            if (containsType(tree.getChild(i), type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Expression field(Operand o) throws HandlerException {
        return switch (o) {
            case Operand.Field f -> name(f.item().javaName());
            case Operand.ReturnCode r -> name("returnCode");
            default -> throw HandlerException.unsupported(o.category() + " is not a data item");
        };
    }

    @Override
    public Expression numeric(Operand o) throws HandlerException {
        return switch (o) {
            case Operand.Field f when f.item().isNumeric() -> call(name(f.item().javaName()), "get");
            case Operand.ReturnCode r -> call(name("returnCode"), "get");
            case Operand.NumericLiteral n -> decimal(n.value().toPlainString());
            case Operand.Figurative f when f.isZero() -> decimal("0");
            case Operand.Arithmetic a -> arithmetic(a.stmt());
            default -> throw HandlerException.unsupported(o.category() + " used where a numeric value is required");
        };
    }

    private Expression arithmetic(ArithmeticValueStmt a) throws HandlerException {
        Expression e = multDivs(a.getMultDivs());
        for (PlusMinus pm : a.getPlusMinus()) {
            e = call(e, pm.getPlusMinusType() == PlusMinus.PlusMinusType.PLUS ? "add" : "subtract", multDivs(pm.getMultDivs()));
        }
        return e;
    }

    private Expression multDivs(MultDivs md) throws HandlerException {
        Expression e = powers(md.getPowers());
        for (MultDiv m : md.getMultDivs()) {
            if (m.getMultDivType() == MultDiv.MultDivType.DIV) {
                throw HandlerException.unsupported("division in an arithmetic expression is not supported "
                        + "(intermediate precision is implementation-defined); use DIVIDE");
            }
            e = call(e, "multiply", powers(m.getPowers()));
        }
        return e;
    }

    private Expression powers(Powers p) throws HandlerException {
        if (!p.getPowers().isEmpty()) {
            throw HandlerException.unsupported("exponentiation (**) is not supported");
        }
        Expression e = basis(p.getBasis());
        return p.getPowersType() == Powers.PowersType.MINUS ? call(e, "negate") : e;
    }

    private Expression basis(Basis b) throws HandlerException {
        ValueStmt v = b.getBasisValueStmt();
        if (v instanceof ArithmeticValueStmt nested) {
            return arithmetic(nested);
        }
        Operand o = operand(v);
        if (!o.isNumericValued()) {
            throw HandlerException.unsupported(o.category() + " in an arithmetic expression");
        }
        return numeric(o);
    }

    /** The subject and operator an abbreviated relation (A = B OR C) carries forward. */
    private record Relation(Operand subject, RelationalOperatorType op) {}

    @Override
    public Expression condition(ConditionValueStmt c) throws HandlerException {
        // AND binds tighter than OR; ProLeap gives a flat list, so group explicitly.
        List<Expression> disjuncts = new ArrayList<>();
        Relation last = relationOf(c.getCombinableCondition());
        Expression conjunction = combinable(c.getCombinableCondition());
        for (AndOrCondition ao : c.getAndOrConditions()) {
            Expression next;
            if (!ao.getAbbreviations().isEmpty()) {
                if (ao.getAbbreviations().size() != 1 || last == null) {
                    throw HandlerException.unsupported("abbreviated condition " + ao.getCtx().getText() + " has no subject to abbreviate");
                }
                Abbreviation ab = ao.getAbbreviations().getFirst();
                if (ab.getAbbreviation() != null) {
                    throw HandlerException.unsupported("nested parenthesised abbreviations are not supported");
                }
                RelationalOperatorType op = ab.getOperator() == null ? last.op() : ab.getOperator().getRelationalOperatorType();
                last = new Relation(last.subject(), op);
                next = comparison(last.subject(), operand(ab.getArithmeticExpression()), op, ab.getCtx());
                // NOT before an implied relation negates it (the same as negating its operator).
                if (Facts.hasDirectToken(ab.getCtx(), "NOT")) {
                    next = new UnaryExpr(new EnclosedExpr(next), UnaryExpr.Operator.LOGICAL_COMPLEMENT);
                }
            } else if (ao.getCombinableCondition() != null) {
                last = relationOf(ao.getCombinableCondition());
                next = combinable(ao.getCombinableCondition());
            } else {
                throw HandlerException.unsupported("condition " + ao.getCtx().getText() + " is not supported");
            }
            if (ao.getAndOrConditionType() == AndOrCondition.AndOrConditionType.AND) {
                conjunction = new BinaryExpr(conjunction, next, BinaryExpr.Operator.AND);
            } else {
                disjuncts.add(conjunction);
                conjunction = next;
            }
        }
        disjuncts.add(conjunction);
        Expression e = disjuncts.getFirst();
        for (int i = 1; i < disjuncts.size(); i++) {
            e = new BinaryExpr(e, disjuncts.get(i), BinaryExpr.Operator.OR);
        }
        return e;
    }

    /** The relation a following abbreviation may continue, or null. */
    private Relation relationOf(CombinableCondition cc) throws HandlerException {
        SimpleCondition sc = cc.getSimpleCondition();
        if (Facts.hasDirectToken(cc.getCtx(), "NOT") || sc.getSimpleConditionType() != SimpleCondition.SimpleConditionType.RELATION_CONDITION
                || sc.getRelationCondition().getRelationConditionType() != RelationConditionValueStmt.RelationConditionType.ARITHMETIC) {
            return null;
        }
        var ac = sc.getRelationCondition().getArithmeticComparison();
        return new Relation(operand(ac.getArithmeticExpressionLeft()), ac.getOperator().getRelationalOperatorType());
    }

    private Expression combinable(CombinableCondition cc) throws HandlerException {
        Expression e = simple(cc.getSimpleCondition());
        return Facts.hasDirectToken(cc.getCtx(), "NOT")
                ? new UnaryExpr(new EnclosedExpr(e), UnaryExpr.Operator.LOGICAL_COMPLEMENT) : e;
    }

    private Expression simple(SimpleCondition sc) throws HandlerException {
        return switch (sc.getSimpleConditionType()) {
            case CONDITION -> new EnclosedExpr(condition(sc.getCondition()));
            case RELATION_CONDITION -> relation(sc.getRelationCondition());
            case CLASS_CONDITION -> classCondition(sc.getClassCondition());
            case CONDITION_NAME_REFERENCE -> {
                ConditionNameReference ref = sc.getConditionNameReference();
                if (!ref.getConditionNameSubscriptReferences().isEmpty()) {
                    throw HandlerException.unsupported("subscripted condition-names are not supported");
                }
                Operand o = operand(ref.getConditionCall());
                if (!(o instanceof Operand.Field f) || !f.item().isCondition()) {
                    throw HandlerException.unsupported(ref.getCtx().getText() + " is not a condition-name");
                }
                yield conditionName(f.item(), ref.getCtx());
            }
        };
    }

    /** NUMERIC / ALPHABETIC[-UPPER|-LOWER] / user CLASS on alphanumeric data, character by character. */
    private Expression classCondition(ClassCondition cc) throws HandlerException {
        Operand o = operand(cc.getIdentifierCall());
        if (!(o instanceof Operand.Field f) || !f.item().isAlphanumeric()) {
            throw HandlerException.unsupported("class condition on " + o.category() + " " + cc.getIdentifierCall().getName()
                    + " is not supported (only alphanumeric items; numeric items need byte storage)");
        }
        Expression value = call(field(o), "get");
        Expression test = switch (cc.getClassConditionType()) {
            case NUMERIC -> staticCall("Compare", "isNumeric", value);
            case ALPHABETIC -> staticCall("Compare", "isAlphabetic", value);
            case ALPHABETIC_UPPER -> staticCall("Compare", "isAlphabeticUpper", value);
            case ALPHABETIC_LOWER -> staticCall("Compare", "isAlphabeticLower", value);
            case CLASS_NAME -> staticCall("Compare", "inClass", value,
                    str(userClass(cc.getClassCall().getName(), cc.getCtx())));
            default -> throw HandlerException.unsupported("class condition " + cc.getClassConditionType() + " is not supported");
        };
        return cc.getNot() ? new UnaryExpr(test, UnaryExpr.Operator.LOGICAL_COMPLEMENT) : test;
    }

    @Override
    public Expression conditionName(DataItem condition, ParserRuleContext where) throws HandlerException {
        Operand parent = new Operand.Field(condition.parent());
        Expression test = null;
        for (DataItem.ConditionValue v : condition.conditionValues()) {
            Expression one = v.thru() == null
                    ? comparison(parent, v.from(), RelationalOperatorType.EQUAL, where)
                    : new BinaryExpr(comparison(parent, v.from(), RelationalOperatorType.GREATER_OR_EQUAL, where),
                            comparison(parent, v.thru(), RelationalOperatorType.LESS_OR_EQUAL, where), BinaryExpr.Operator.AND);
            test = test == null ? one : new BinaryExpr(test, one, BinaryExpr.Operator.OR);
        }
        return condition.conditionValues().size() == 1 ? test : new EnclosedExpr(test);
    }

    private Expression relation(RelationConditionValueStmt r) throws HandlerException {
        return switch (r.getRelationConditionType()) {
            case ARITHMETIC -> {
                var ac = r.getArithmeticComparison();
                yield comparison(operand(ac.getArithmeticExpressionLeft()), operand(ac.getArithmeticExpressionRight()),
                        ac.getOperator().getRelationalOperatorType(), r.getCtx());
            }
            case SIGN -> {
                SignCondition s = r.getSignCondition();
                Operand o = operand(s.getArithmeticExpression());
                if (!o.isNumericValued()) {
                    throw HandlerException.unsupported("sign condition on a non-numeric operand");
                }
                BinaryExpr.Operator op = switch (s.getSignConditionType()) {
                    case POSITIVE -> BinaryExpr.Operator.GREATER;
                    case NEGATIVE -> BinaryExpr.Operator.LESS;
                    case ZERO -> BinaryExpr.Operator.EQUALS;
                };
                Expression e = Ast.compareToZero(call(numeric(o), "signum"), op);
                yield Facts.hasDirectToken(s.getCtx(), "NOT") ? new UnaryExpr(new EnclosedExpr(e), UnaryExpr.Operator.LOGICAL_COMPLEMENT) : e;
            }
            case COMBINED -> {
                // A = (B OR C) is A = B OR A = C; the connective is the same throughout.
                var cc = r.getCombinedComparison();
                Operand subject = operand(cc.getArithmeticExpression());
                RelationalOperatorType op = cc.getOperator().getRelationalOperatorType();
                var combined = cc.getCombinedCondition();
                boolean and = combined.getCombinedConditionType() == io.proleap.cobol.asg.metamodel.valuestmt.relation.CombinedCondition.CombinedConditionType.AND;
                if (Facts.hasDirectToken(combined.getCtx(), and ? "OR" : "AND")) {
                    throw HandlerException.unsupported("combined comparison mixing AND and OR is not supported");
                }
                Expression e = null;
                for (var operandExpr : combined.getArithmeticExpressions()) {
                    Expression one = comparison(subject, operand(operandExpr), op, r.getCtx());
                    e = e == null ? one : new BinaryExpr(e, one, and ? BinaryExpr.Operator.AND : BinaryExpr.Operator.OR);
                }
                yield new EnclosedExpr(e);
            }
        };
    }

    @Override
    public Expression compare(Operand left, Operand right, RelationalOperatorType op, ParserRuleContext where) throws HandlerException {
        return comparison(left, right, op, where);
    }

    private Expression comparison(Operand left, Operand right, RelationalOperatorType op, ParserRuleContext where)
            throws HandlerException {
        if (left.isNumericValued() && right.isNumericValued()) {
            return Ast.compareToZero(staticCall("Compare", "numeric", numeric(left), numeric(right)), javaOperator(op));
        }
        Expression l = alphanumericSide(left, right);
        Expression rr = alphanumericSide(right, left);
        if (l != null && rr != null) {
            requireCollatingSequence(op, where);
            return Ast.compareToZero(staticCall("Compare", "alphanumeric", l, rr), javaOperator(op));
        }
        if (l != null && right instanceof Operand.Figurative f) {
            requireCollatingSequence(op, where);
            return Ast.compareToZero(staticCall("Compare", "figurative", l, figurativeArg(f)), javaOperator(op));
        }
        if (rr != null && left instanceof Operand.Figurative f) {
            requireCollatingSequence(op, where);
            return Ast.compareToZero(staticCall("Compare", "figurative", rr, figurativeArg(f)), javaOperator(mirror(op)));
        }
        throw HandlerException.unsupported("comparison of " + left.category() + " with " + right.category() + " is not supported");
    }

    private static Expression figurativeArg(Operand.Figurative f) {
        return f.isSingle() ? Ast.chr(f.fill()) : str(f.pattern());
    }

    /**
     * Equality does not depend on the collating sequence; ordering does. The runtime compares in
     * ASCII (ISO-8859-1) order, which is exact for an ASCII sequence and is refused otherwise.
     */
    private void requireCollatingSequence(RelationalOperatorType op, ParserRuleContext where) throws HandlerException {
        if (op == RelationalOperatorType.EQUAL || op == RelationalOperatorType.NOT_EQUAL) {
            return;
        }
        String sequence = require("collating_sequence", in.dialect().collatingSequence(), where, "an ordered alphanumeric comparison");
        if (sequence.equals("native")) {
            sequence = require("encoding", in.dialect().encoding(), where, "an ordered alphanumeric comparison in the native sequence");
        }
        if (!sequence.equals("ascii")) {
            throw HandlerException.unsupported("ordered alphanumeric comparison in the " + sequence.toUpperCase(Locale.ROOT)
                    + " collating sequence is not supported");
        }
    }

    /**
     * The alphanumeric value of an operand in a non-numeric comparison, or null. An unsigned integer
     * numeric operand compared with an alphanumeric one is compared as its digit string.
     */
    private Expression alphanumericSide(Operand o, Operand other) throws HandlerException {
        boolean otherAlpha = other instanceof Operand.AlphanumericLiteral
                || other instanceof Operand.Field f && f.item().isAlphanumeric();
        return switch (o) {
            case Operand.Field f when f.item().isAlphanumeric() -> call(name(f.item().javaName()), "get");
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.Field f when otherAlpha && f.item().isInteger() && !f.item().numeric().signed() ->
                    call(name(f.item().javaName()), "digits");
            case Operand.NumericLiteral n when otherAlpha && n.isInteger() && !n.isSigned() -> str(n.text());
            default -> null;
        };
    }

    private static BinaryExpr.Operator javaOperator(RelationalOperatorType op) {
        return switch (op) {
            case EQUAL -> BinaryExpr.Operator.EQUALS;
            case NOT_EQUAL -> BinaryExpr.Operator.NOT_EQUALS;
            case GREATER -> BinaryExpr.Operator.GREATER;
            case GREATER_OR_EQUAL -> BinaryExpr.Operator.GREATER_EQUALS;
            case LESS -> BinaryExpr.Operator.LESS;
            case LESS_OR_EQUAL -> BinaryExpr.Operator.LESS_EQUALS;
        };
    }

    private static RelationalOperatorType mirror(RelationalOperatorType op) {
        return switch (op) {
            case GREATER -> RelationalOperatorType.LESS;
            case GREATER_OR_EQUAL -> RelationalOperatorType.LESS_OR_EQUAL;
            case LESS -> RelationalOperatorType.GREATER;
            case LESS_OR_EQUAL -> RelationalOperatorType.GREATER_OR_EQUAL;
            default -> op;
        };
    }

    @Override
    public BlockStmt nested(Scope scope) {
        BlockStmt block = new BlockStmt();
        translateInto(scope, block);
        return block;
    }

    @Override
    public BlockStmt nestedPerform(Scope scope, PerformFrame frame) {
        performs.push(frame);
        try {
            return nested(scope);
        } finally {
            performs.pop();
        }
    }

    @Override
    public Statement jump(Jump kind) throws HandlerException {
        return switch (kind) {
            case EXIT_PARAGRAPH -> {
                if (currentParagraph == null) {
                    throw HandlerException.unsupported("EXIT PARAGRAPH outside a paragraph is not supported");
                }
                yield currentSection != null && exitSections.contains(currentSection) ? new ReturnStmt(Ast.bool(false)) : new ReturnStmt();
            }
            case EXIT_SECTION -> {
                if (currentSection == null) {
                    throw HandlerException.unsupported("EXIT SECTION outside a section");
                }
                if (currentParagraph == null) {
                    yield new ReturnStmt();
                }
                if (performedParagraphs.contains(currentParagraph)) {
                    throw HandlerException.unsupported("EXIT SECTION in paragraph " + currentParagraph.getName()
                            + ", which is also PERFORMed on its own, leaves the performed range (needs the procedure dispatcher)");
                }
                yield new ReturnStmt(Ast.bool(true));
            }
            case EXIT_PERFORM, EXIT_PERFORM_CYCLE -> {
                PerformFrame frame = performs.peek();
                if (frame == null) {
                    throw HandlerException.unsupported("EXIT PERFORM outside an inline PERFORM");
                }
                boolean cycle = kind == Jump.EXIT_PERFORM_CYCLE;
                if (cycle) {
                    frame.cycleUsed = true;
                } else {
                    frame.exitUsed = true;
                }
                yield new BreakStmt(cycle ? frame.cycleLabel : frame.exitLabel);
            }
            case NEXT_SENTENCE -> {
                String label = sentenceLabels.peek();
                if (label == null) {
                    throw HandlerException.unsupported("NEXT SENTENCE outside a sentence");
                }
                yield new BreakStmt(label);
            }
        };
    }

    @Override
    public boolean decimalPointComma() {
        return decimalComma;
    }

    @Override
    public String device(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return devices.getOrDefault(upper, upper);
    }

    @Override
    public String userClass(String name, ParserRuleContext where) throws HandlerException {
        var clause = classes.get(name.toUpperCase(Locale.ROOT));
        if (clause == null) {
            throw HandlerException.unsupported("CLASS " + name + " is not declared in SPECIAL-NAMES");
        }
        StringBuilder members = new StringBuilder();
        for (var through : clause.classClauseThrough()) {
            String from = classMember(through.classClauseFrom().literal());
            if (through.classClauseTo() == null) {
                members.append(from);
                continue;
            }
            String to = classMember(through.classClauseTo().literal());
            if (from.length() != 1 || to.length() != 1) {
                throw HandlerException.unsupported("CLASS " + name + ": THRU needs single characters");
            }
            // A range is in the native character set's order.
            String encoding = require("encoding", in.dialect().encoding(), where, "CLASS " + name + " ... THRU");
            if (!encoding.equals("ascii")) {
                throw HandlerException.unsupported("CLASS " + name + ": THRU ranges in EBCDIC are not supported");
            }
            for (char c = from.charAt(0); c <= to.charAt(0); c++) {
                members.append(c);
            }
        }
        return members.toString();
    }

    private String classMember(CobolParser.LiteralContext lit) throws HandlerException {
        if (lit == null || !(literal(lit) instanceof Operand.AlphanumericLiteral a)) {
            throw HandlerException.unsupported("CLASS members other than nonnumeric literals are not supported");
        }
        return a.value();
    }

    @Override
    public String procedureMethod(Call call) throws HandlerException {
        Object target = switch (call.unwrap()) {
            case ProcedureCall p -> p.getParagraph();
            case SectionCall s -> s.getSection();
            default -> null;
        };
        String method = target == null ? null : procedureMethods.get(target);
        if (method == null) {
            throw HandlerException.unsupported("procedure " + call.getName() + " cannot be resolved");
        }
        return method;
    }

    @Override
    public String fresh(String base) {
        int n = freshCounters.merge(base, 1, Integer::sum);
        return base + n;
    }

    @Override
    public Optional<String> programClass(String programId) {
        return in.programDirectory().apply(programId.toUpperCase(Locale.ROOT));
    }

    /**
     * Completes a program once decisions are applied: a jump (EXIT PARAGRAPH, NEXT SENTENCE, ...)
     * followed by more statements of its block becomes {@code if (true) jump;}, because COBOL
     * may leave code unreachable but javac rejects it; then unused runtime imports are dropped.
     */
    public static void finish(CompilationUnit cu) {
        cu.findAll(BlockStmt.class).forEach(block -> {
            var list = block.getStatements();
            for (int i = 0; i < list.size() - 1; i++) {
                Statement st = list.get(i);
                if (st instanceof ReturnStmt || st instanceof BreakStmt || st instanceof com.github.javaparser.ast.stmt.ContinueStmt) {
                    list.set(i, new IfStmt(Ast.bool(true), st.clone(), null));
                }
            }
        });
        pruneImports(cu);
    }

    /** Drops unused runtime imports; call once the AST is complete (after decisions are applied). */
    public static void pruneImports(CompilationUnit cu) {
        cu.getImports().removeIf(i -> !usesSimpleName(cu, i.getName().getIdentifier()));
    }

    private static boolean usesSimpleName(CompilationUnit cu, String simpleName) {
        return cu.getTypes().stream().anyMatch(t -> t.findFirst(com.github.javaparser.ast.Node.class,
                n -> (n instanceof com.github.javaparser.ast.expr.NameExpr ne && ne.getNameAsString().equals(simpleName))
                        || (n instanceof com.github.javaparser.ast.type.ClassOrInterfaceType ct && ct.getNameAsString().equals(simpleName)))
                .isPresent());
    }
}
