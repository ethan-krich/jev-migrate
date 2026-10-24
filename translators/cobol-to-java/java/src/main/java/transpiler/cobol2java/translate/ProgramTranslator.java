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
import com.github.javaparser.ast.stmt.EmptyStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.VoidType;
import io.proleap.cobol.asg.metamodel.Literal;
import io.proleap.cobol.asg.metamodel.ProgramUnit;
import io.proleap.cobol.asg.metamodel.Scope;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.call.DataDescriptionEntryCall;
import io.proleap.cobol.asg.metamodel.call.ProcedureCall;
import io.proleap.cobol.asg.metamodel.call.SectionCall;
import io.proleap.cobol.asg.metamodel.call.SpecialRegisterCall;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntry;
import io.proleap.cobol.asg.metamodel.data.datadescription.DataDescriptionEntryGroup;
import io.proleap.cobol.asg.metamodel.data.datadescription.UsageClause;
import io.proleap.cobol.asg.metamodel.procedure.Paragraph;
import io.proleap.cobol.asg.metamodel.procedure.ProcedureDivision;
import io.proleap.cobol.asg.metamodel.procedure.Section;
import io.proleap.cobol.asg.metamodel.procedure.move.MoveStatement;
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
import io.proleap.cobol.asg.metamodel.valuestmt.condition.CombinableCondition;
import io.proleap.cobol.asg.metamodel.valuestmt.condition.SimpleCondition;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.RelationalOperator.RelationalOperatorType;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.SignCondition;
import java.math.BigDecimal;
import java.util.ArrayList;
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
    private int statements;
    private int translated;

    public ProgramTranslator(Input input) {
        this.in = input;
    }

    public Result translate() {
        CompilationUnit cu = new CompilationUnit(in.packageName());
        cu.addImport(in.runtimePackage() + ".AlphanumericField");
        cu.addImport(in.runtimePackage() + ".Arithmetic");
        cu.addImport(in.runtimePackage() + ".CobolProgram");
        cu.addImport(in.runtimePackage() + ".Compare");
        cu.addImport(in.runtimePackage() + ".Display");
        cu.addImport(in.runtimePackage() + ".NumericField");
        cu.addImport(in.runtimePackage() + ".ProgramSupport");
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
                dataDecision(env.getSpecialNamesParagraph().getCtx(), "ENVIRONMENT",
                        "SPECIAL-NAMES is not supported (it can change literal and editing semantics)");
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

    private void buildDataModel() {
        fieldNames.add("returnCode");
        var data = in.unit().getDataDivision();
        if (data == null || data.getWorkingStorageSection() == null) {
            return;
        }
        for (DataDescriptionEntry e : data.getWorkingStorageSection().getRootDataDescriptionEntries()) {
            buildItem(e, null);
        }
    }

    private void buildItem(DataDescriptionEntry e, DataItem parent) {
        int line = in.sourceMap().originalLine(e.getCtx().getStart().getLine());
        String name = e.getName() == null ? "FILLER" : e.getName();
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
        if (item.kind() == DataItem.Kind.ALPHANUMERIC || item.kind() == DataItem.Kind.NUMERIC) {
            if (e.getName() != null) {
                item.javaName(allocateField(Names.fieldName(name)));
                fieldOrder.add(item);
            }
        }
        for (DataDescriptionEntry child : g.getDataDescriptionEntries()) {
            buildItem(child, item);
        }
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
            return "VALUE " + e.getMessage();
        }
        if (item.isAlphanumeric()) {
            switch (o) {
                case Operand.AlphanumericLiteral a -> item.initialValue(a.value());
                case Operand.Figurative f -> item.initialValue(String.valueOf(f.fill()).repeat(item.alphanumeric().length()));
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
            case Operand.Figurative f when f.fill() == '0' -> item.initialValue("0");
            default -> {
                return "VALUE " + o.category() + " for a numeric item is not supported";
            }
        }
        return null;
    }

    private String allocateField(String base) {
        String name = base;
        for (int i = 2; !fieldNames.add(name); i++) {
            name = base + "_" + i;
        }
        return name;
    }

    private void addFields(ClassOrInterfaceDeclaration cls) {
        cls.addFieldWithInitializer("NumericField", "returnCode", create("NumericField", integer(9), integer(0), Ast.bool(true)),
                Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        for (DataItem item : fieldOrder) {
            Expression init;
            if (item.isAlphanumeric()) {
                init = item.initialValue() == null
                        ? create("AlphanumericField", integer(item.alphanumeric().length()))
                        : create("AlphanumericField", integer(item.alphanumeric().length()), str(item.initialValue()));
            } else {
                Picture.Numeric n = item.numeric();
                init = item.initialValue() == null
                        ? create("NumericField", integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()))
                        : create("NumericField", integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()), str(item.initialValue()));
            }
            var field = cls.addFieldWithInitializer(item.isAlphanumeric() ? "AlphanumericField" : "NumericField",
                    item.javaName(), init, Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
            field.setLineComment(" " + item.cobolName() + " (line " + item.line() + ")");
        }
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
            addProcedureMethod(cls, p, procedureMethods.get(p), new BlockStmt());
        }
        for (Section s : pd.getSections()) {
            body.addStatement(stmt(thisCall(procedureMethods.get(s))));
            BlockStmt sectionBody = new BlockStmt();
            for (Paragraph p : s.getParagraphs()) {
                addProcedureMethod(cls, p, procedureMethods.get(p), new BlockStmt());
            }
            addProcedureMethod(cls, s, procedureMethods.get(s), sectionBody);
            for (Paragraph p : s.getParagraphs()) {
                sectionBody.addStatement(stmt(thisCall(procedureMethods.get(p))));
            }
        }
    }

    private void addProcedureMethod(ClassOrInterfaceDeclaration cls, Scope scope, String method, BlockStmt body) {
        MethodDeclaration m = cls.addMethod(method, Modifier.Keyword.PRIVATE);
        m.setType(new VoidType());
        m.setLineComment(" " + (scope instanceof Section ? "SECTION " : "PARAGRAPH ") + ((io.proleap.cobol.asg.metamodel.NamedElement) scope).getName());
        translateInto(scope, body);
        m.setBody(body);
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
        for (var s : scope.getStatements()) {
            translateStatement(s, block);
        }
    }

    private void translateStatement(io.proleap.cobol.asg.metamodel.procedure.Statement s, BlockStmt block) {
        String op = Facts.operationName(s.getStatementType());
        List<OpUnit> units = new ArrayList<>();
        if (s instanceof MoveStatement move && move.getMoveType() == MoveStatement.MoveType.MOVE_TO) {
            for (Call receiver : move.getMoveToStatement().getReceivingAreaCalls()) {
                units.add(new OpUnit(op, s, receiver, location(s.getCtx())));
            }
        } else {
            units.add(new OpUnit(op, s, null, location(s.getCtx())));
        }
        for (OpUnit unit : units) {
            translateUnit(unit, block);
        }
    }

    private void translateUnit(OpUnit unit, BlockStmt block) {
        statements++;
        Map<String, Object> facts = Facts.extract(unit, this);
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
            case NUMERIC -> {
                if (lit.getNumericLiteral().getNumericLiteralType() != io.proleap.cobol.asg.metamodel.NumericLiteral.NumericLiteralType.INTEGER
                        && lit.getCtx().getText().toUpperCase(Locale.ROOT).contains("E")) {
                    throw HandlerException.unsupported("floating-point literal " + lit.getCtx().getText() + " is not supported");
                }
                yield new Operand.NumericLiteral(lit.getNumericLiteral().getValue(), lit.getCtx().getText());
            }
            case NON_NUMERIC -> {
                String text = lit.getCtx().getText();
                char q = text.charAt(0);
                if (q != '"' && q != '\'') {
                    throw HandlerException.unsupported("literal " + text + " (hex/national/other prefix) is not supported");
                }
                String inner = text.substring(1, text.length() - 1).replace("" + q + q, "" + q);
                yield new Operand.AlphanumericLiteral(inner);
            }
            case FIGURATIVE_CONSTANT -> switch (lit.getFigurativeConstant().getFigurativeConstantType()) {
                case ZERO, ZEROS, ZEROES -> new Operand.Figurative('0', "ZERO");
                case SPACE, SPACES -> new Operand.Figurative(' ', "SPACE");
                default -> throw HandlerException.unsupported("figurative constant "
                        + lit.getFigurativeConstant().getFigurativeConstantType() + " is not supported");
            };
            default -> throw HandlerException.unsupported("literal " + lit.getCtx().getText() + " is not supported");
        };
    }

    @Override
    public Operand operand(Call call) throws HandlerException {
        return switch (call.unwrap()) {
            case DataDescriptionEntryCall d -> {
                DataItem item = items.get(d.getDataDescriptionEntry());
                if (item == null) {
                    throw HandlerException.unsupported(call.getName() + " is not a WORKING-STORAGE item");
                }
                if (item.kind() == DataItem.Kind.UNSUPPORTED) {
                    throw HandlerException.unsupported(item.cobolName() + ": " + item.unsupportedReason());
                }
                if (item.kind() == DataItem.Kind.GROUP) {
                    throw HandlerException.unsupported("reference to group item " + item.cobolName() + " is not supported");
                }
                yield new Operand.Field(item);
            }
            case SpecialRegisterCall s when s.getSpecialRegisterType() == SpecialRegisterCall.SpecialRegisterType.RETURN_CODE ->
                    new Operand.ReturnCode();
            default -> throw HandlerException.unsupported(call.getName() + " (" + call.getCallType()
                    + ") is not supported: tables, reference modification, functions and special registers other than RETURN-CODE");
        };
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
            case Operand.Figurative f when f.fill() == '0' -> decimal("0");
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

    @Override
    public Expression condition(ConditionValueStmt c) throws HandlerException {
        // AND binds tighter than OR; ProLeap gives a flat list, so group explicitly.
        List<Expression> disjuncts = new ArrayList<>();
        Expression conjunction = combinable(c.getCombinableCondition());
        for (AndOrCondition ao : c.getAndOrConditions()) {
            if (!ao.getAbbreviations().isEmpty() || ao.getCombinableCondition() == null) {
                throw HandlerException.unsupported("abbreviated combined relation conditions are not supported");
            }
            Expression next = combinable(ao.getCombinableCondition());
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

    private Expression combinable(CombinableCondition cc) throws HandlerException {
        Expression e = simple(cc.getSimpleCondition());
        return Facts.hasDirectToken(cc.getCtx(), "NOT")
                ? new UnaryExpr(new EnclosedExpr(e), UnaryExpr.Operator.LOGICAL_COMPLEMENT) : e;
    }

    private Expression simple(SimpleCondition sc) throws HandlerException {
        return switch (sc.getSimpleConditionType()) {
            case CONDITION -> new EnclosedExpr(condition(sc.getCondition()));
            case RELATION_CONDITION -> relation(sc.getRelationCondition());
            case CLASS_CONDITION -> throw HandlerException.unsupported("class conditions (NUMERIC, ALPHABETIC, ...) are not supported");
            case CONDITION_NAME_REFERENCE -> throw HandlerException.unsupported("condition-name (level-88) references are not supported");
        };
    }

    private Expression relation(RelationConditionValueStmt r) throws HandlerException {
        return switch (r.getRelationConditionType()) {
            case ARITHMETIC -> {
                var ac = r.getArithmeticComparison();
                yield comparison(operand(ac.getArithmeticExpressionLeft()), operand(ac.getArithmeticExpressionRight()),
                        ac.getOperator().getRelationalOperatorType());
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
            case COMBINED -> throw HandlerException.unsupported("combined comparisons like A = (B OR C) are not supported");
        };
    }

    private Expression comparison(Operand left, Operand right, RelationalOperatorType op) throws HandlerException {
        if (left.isNumericValued() && right.isNumericValued()) {
            return Ast.compareToZero(staticCall("Compare", "numeric", numeric(left), numeric(right)), javaOperator(op));
        }
        Expression l = alphanumericSide(left, right);
        Expression rr = alphanumericSide(right, left);
        if (l != null && rr != null) {
            return Ast.compareToZero(staticCall("Compare", "alphanumeric", l, rr), javaOperator(op));
        }
        if (l != null && right instanceof Operand.Figurative f) {
            return Ast.compareToZero(staticCall("Compare", "figurative", l, Ast.chr(f.fill())), javaOperator(op));
        }
        if (rr != null && left instanceof Operand.Figurative f) {
            return Ast.compareToZero(staticCall("Compare", "figurative", rr, Ast.chr(f.fill())), javaOperator(mirror(op)));
        }
        throw HandlerException.unsupported("comparison of " + left.category() + " with " + right.category() + " is not supported");
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
