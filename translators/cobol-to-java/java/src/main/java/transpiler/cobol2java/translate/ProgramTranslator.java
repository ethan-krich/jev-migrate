package transpiler.cobol2java.translate;

import static transpiler.cobol2java.translate.Ast.call;
import static transpiler.cobol2java.translate.Ast.create;
import static transpiler.cobol2java.translate.Ast.decimal;
import static transpiler.cobol2java.translate.Ast.declare;
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
import com.github.javaparser.ast.stmt.WhileStmt;
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
            Dialect dialect,
            Function<String, Optional<List<Integer>>> programParameters) {
        public Input(ProgramUnit unit, SourceMap sourceMap, String sourceFile, String programId, String packageName, String className,
                String runtimePackage, MappingRegistry registry, Builtins builtins, Function<String, Optional<String>> programDirectory,
                Dialect dialect) {
            this(unit, sourceMap, sourceFile, programId, packageName, className, runtimePackage, registry, builtins, programDirectory,
                    dialect, id -> Optional.empty());
        }
    }

    /**
     * The sizes of a program's PROCEDURE DIVISION USING items (BY REFERENCE), for callers to
     * check against; empty when it has none that can be called this way.
     */
    public static Optional<List<Integer>> parameters(ProgramUnit unit, SourceMap sourceMap, String sourceFile, String programId,
            Dialect dialect) {
        ProgramTranslator t = new ProgramTranslator(new Input(unit, sourceMap, sourceFile, programId, "p", "P", "r", null, null,
                id -> Optional.empty(), dialect));
        t.checkUnsupportedDivisions();
        t.buildDataModel();
        try {
            return Optional.of(t.usingItems().stream().map(DataItem::totalSize).toList());
        } catch (HandlerException e) {
            return Optional.empty();
        }
    }

    /** The PROCEDURE DIVISION USING items: level-01 / level-77 LINKAGE items passed BY REFERENCE. */
    private List<DataItem> usingItems() throws HandlerException {
        var pd = in.unit().getProcedureDivision();
        if (pd == null || pd.getUsingClause() == null) {
            return List.of();
        }
        List<DataItem> out = new ArrayList<>();
        for (var p : pd.getUsingClause().getUsingParameters()) {
            if (p.getByValuePhrase() != null || p.getByReferencePhrase() == null) {
                throw HandlerException.unsupported("PROCEDURE DIVISION USING BY VALUE is not supported");
            }
            for (var ref : p.getByReferencePhrase().getByReferences()) {
                if (ref.isAny() || ref.isOptional() || !(ref.getReferenceCall() != null
                        && ref.getReferenceCall().unwrap() instanceof DataDescriptionEntryCall d)) {
                    throw HandlerException.unsupported("PROCEDURE DIVISION USING " + (ref.getCtx() == null ? "" : ref.getCtx().getText())
                            + " is not supported");
                }
                DataItem item = items.get(d.getDataDescriptionEntry());
                if (item == null || item.parent() != null || !item.linkage() || item.kind() == DataItem.Kind.UNSUPPORTED) {
                    throw HandlerException.unsupported("USING " + d.getName() + " must be a supported level-01 / level-77 LINKAGE item");
                }
                out.add(item);
            }
        }
        return out;
    }

    /** The translation output; {@code decisions} are not yet registered with ids. */
    public record Result(CompilationUnit compilationUnit, List<Decision> decisions, int statements, int translated) {}

    private final Input in;
    private final Map<DataDescriptionEntry, DataItem> items = new IdentityHashMap<>();
    private final List<DataItem> fieldOrder = new ArrayList<>();
    /** OCCURS clauses, resolved once every item exists (DEPENDING ON and KEY may name later items). */
    private record PendingTable(DataItem item, DataDescriptionEntryGroup entry) {}

    private final List<PendingTable> tables = new ArrayList<>();
    private final Map<Object, DataItem.IndexName> indexNames = new IdentityHashMap<>();
    private final Map<String, DataItem.IndexName> indexesByName = new HashMap<>();
    /** USAGE written on a group, which its subordinate items inherit. */
    private final Map<DataItem, String> inheritedUsage = new IdentityHashMap<>();
    /** A file: SELECT and FD together. {@code problem} is set when it cannot be translated (its decision is recorded). */
    private static final class FileInfo {
        final String cobolName;
        final ParserRuleContext ctx;
        String javaName;
        String path;
        String assigned;
        boolean sort;
        boolean lineSequential = true;
        CobolParser.QualifiedDataNameContext statusName;
        DataItem status;
        final List<DataItem> records = new ArrayList<>();

        FileInfo(String cobolName, ParserRuleContext ctx) {
            this.cobolName = cobolName;
            this.ctx = ctx;
        }
    }

    private final Map<String, FileInfo> files = new LinkedHashMap<>();
    /** Java field holding each LINKAGE record's binding. */
    private final Map<DataItem, String> parameterFields = new IdentityHashMap<>();
    /** Java field of each record's Storage. */
    private final Map<DataItem, String> storageNames = new IdentityHashMap<>();
    /** The statement being translated, for decisions raised while resolving its operands. */
    private ParserRuleContext currentCtx;
    /** Level-01 / level-77 items (and constants) in source order. */
    private final List<DataItem> roots = new ArrayList<>();
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
    // Procedure dispatcher (GO TO, PERFORM THRU, ALTER): procedures numbered in source order.
    private boolean dispatcher;
    /** A procedure of the dispatcher: a paragraph, a section's own statements, or the end of a section. */
    private record Node(Scope scope, Section section, String method, boolean sectionEnd) {}
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Object, Integer> nodeIndex = new IdentityHashMap<>();
    private final Map<Section, Integer> sectionEnds = new IdentityHashMap<>();
    /** PERFORM ranges [from, thru] (node indices) and the node each PERFORM is in. */
    private final List<int[]> performRanges = new ArrayList<>();
    /** Paragraphs named by ALTER: their GO TO goes where the int field says. */
    private final Map<Paragraph, String> alterable = new IdentityHashMap<>();
    private int currentNode = -1;
    /** Building a COMPUTE expression with GnuCOBOL's division and power rules (compute_gnucobol). */
    private boolean gnuArithmetic;
    private int statements;
    private int translated;
    private final boolean binaryTruncate;

    public ProgramTranslator(Input input) {
        this.in = input;
        this.binaryTruncate = input.dialect().binaryTruncate();
    }

    public Result translate() {
        CompilationUnit cu = new CompilationUnit(in.packageName());
        cu.addImport(in.runtimePackage() + ".Accept");
        cu.addImport(in.runtimePackage() + ".AlphanumericField");
        cu.addImport(in.runtimePackage() + ".Arithmetic");
        cu.addImport(in.runtimePackage() + ".Bounds");
        cu.addImport(in.runtimePackage() + ".Clock");
        cu.addImport(in.runtimePackage() + ".CobolFile");
        cu.addImport(in.runtimePackage() + ".CobolProgram");
        cu.addImport(in.runtimePackage() + ".Compare");
        cu.addImport(in.runtimePackage() + ".Display");
        cu.addImport(in.runtimePackage() + ".EditedField");
        cu.addImport(in.runtimePackage() + ".Functions");
        cu.addImport(in.runtimePackage() + ".NumericField");
        cu.addImport(in.runtimePackage() + ".PerformStack");
        cu.addImport(in.runtimePackage() + ".ProgramSupport");
        cu.addImport(in.runtimePackage() + ".SortFile");
        cu.addImport(in.runtimePackage() + ".Storage");
        cu.addImport(in.runtimePackage() + ".StringOps");
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
                fileControl(env.getInputOutputSection());
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
        fieldNames.addAll(List.of("returnCode", "instance", "main", "procedureDivision", "using", "callParameters", "parameter"));
        var data = in.unit().getDataDivision();
        if (data == null) {
            return;
        }
        var ws = data.getWorkingStorageSection();
        for (DataDescriptionEntry e : ws == null ? List.<DataDescriptionEntry>of() : ws.getRootDataDescriptionEntries()) {
            buildItem(e, null);
            roots.add(items.get(e));
        }
        if (data.getFileSection() != null) {
            fileSection(data.getFileSection());
        }
        if (data.getLinkageSection() != null) {
            for (DataDescriptionEntry e : data.getLinkageSection().getRootDataDescriptionEntries()) {
                buildItem(e, null);
                DataItem item = items.get(e);
                item.markLinkage();
                roots.add(item);
                if (e instanceof DataDescriptionEntryGroup g && hasValue(g)) {
                    dataDecision(e.getCtx(), "DATA", item.cobolName() + ": VALUE in the LINKAGE SECTION is not supported");
                }
            }
        }
        for (PendingTable t : tables) {
            occurs(t.item(), t.entry());
        }
        for (DataItem root : roots) {
            layout(root, 0);
        }
        for (PendingTable t : tables) {
            checkTable(t.item(), t.entry());
        }
        for (FileInfo f : List.copyOf(files.values())) {
            String problem = null;
            if (!f.sort) {
                f.path = in.dialect().fileAssignments().get(f.assigned);
                if (f.path == null) {
                    // The project must say where the file is; it is never guessed.
                    Decision.Plan none = id -> {
                        throw new IllegalArgumentException("decision has no candidates");
                    };
                    root.add(new Decision(Reason.MISSING_MAPPING, "dialect:file_assignments", "ASSIGN TO '" + f.assigned + "' (file "
                            + f.cobolName + ") has no entry in Dialect.file_assignments", location(f.ctx), in.programId(),
                            Map.of("field", "file_assignments", "name", f.assigned), null, List.of(), null, id -> none.apply(id)));
                    statements++;
                    files.remove(f.cobolName);
                    continue;
                }
            }
            if (f.statusName != null) {
                f.status = resolveData(f.statusName);
                if (f.status == null || f.status.size() != 2 || !(f.status.isAlphanumeric() || f.status.isGroup()) || f.status.hasAccessor()) {
                    problem = "FILE STATUS must be a two-character alphanumeric WORKING-STORAGE item";
                }
            }
            if (problem == null && f.records.isEmpty()) {
                problem = "the file has no FD record";
            }
            if (problem == null && !f.lineSequential && f.records.stream().map(DataItem::totalSize).distinct().count() > 1) {
                problem = "SEQUENTIAL files with records of different sizes are not supported";
            }
            if (problem != null) {
                dataDecision(f.ctx, "ENVIRONMENT", f.cobolName + ": " + problem);
                files.remove(f.cobolName);
            }
        }
        checkByteImages();
    }

    /** SELECT entries: ASSIGN through Dialect.file_assignments, SEQUENTIAL / LINE SEQUENTIAL organisation, FILE STATUS. */
    private void fileControl(io.proleap.cobol.asg.metamodel.environment.inputoutput.InputOutputSection section) {
        if (section.getIoControlParagraph() != null) {
            dataDecision(section.getIoControlParagraph().getCtx(), "ENVIRONMENT", "I-O-CONTROL (SAME RECORD AREA, RERUN, APPLY) is not supported");
        }
        if (section.getFileControlParagraph() == null) {
            return;
        }
        for (var entry : section.getFileControlParagraph().getFileControlEntries()) {
            var ctx = (CobolParser.FileControlEntryContext) entry.getCtx();
            FileInfo f = new FileInfo(entry.getName().toUpperCase(Locale.ROOT), ctx);
            String problem = null;
            var org = entry.getOrganizationClause();
            if (org != null) {
                var oc = (CobolParser.OrganizationClauseContext) org.getCtx();
                if (oc.INDEXED() != null) {
                    dataDecision(ctx, "ENVIRONMENT", entry.getName() + ": INDEXED files have no target (key-value adapter needed)",
                            Reason.MISSING_TARGET_API);
                    continue;
                }
                if (oc.RELATIVE() != null) {
                    problem = "RELATIVE files are not supported";
                }
                f.lineSequential = oc.LINE() != null;
            }
            var access = entry.getAccessModeClause();
            if (problem == null && access != null && access.getMode() != io.proleap.cobol.asg.metamodel.environment.inputoutput.filecontrol.AccessModeClause.Mode.SEQUENTIAL) {
                problem = "ACCESS MODE " + access.getMode() + " is not supported";
            }
            if (problem == null && (entry.getRecordKeyClause() != null || entry.getRelativeKeyClause() != null
                    || entry.getAlternateRecordKeyClause() != null || entry.getPasswordClause() != null
                    || entry.getPaddingCharacterClause() != null || entry.getRecordDelimiterClause() != null)) {
                problem = "SELECT clause " + ctx.getText() + " is not supported";
            }
            var status = entry.getFileStatusClause();
            if (problem == null && status != null) {
                var sc = (CobolParser.FileStatusClauseContext) status.getCtx();
                if (sc.qualifiedDataName().size() > 1) {
                    problem = "FILE STATUS with a second (extended) status item is not supported";
                } else {
                    f.statusName = sc.qualifiedDataName().getFirst();
                }
            }
            String assigned = null;
            var assign = entry.getAssignClause();
            if (problem == null) {
                var ac = (CobolParser.AssignClauseContext) assign.getCtx();
                if (ac.literal() != null && ac.literal().NONNUMERICLITERAL() != null) {
                    String t = ac.literal().getText();
                    assigned = t.substring(1, t.length() - 1);
                } else if (ac.assignmentName() != null && ac.DYNAMIC() == null && ac.EXTERNAL() == null) {
                    assigned = ac.assignmentName().getText();
                } else {
                    problem = "ASSIGN " + ac.getText().substring(6) + " is not supported (only a name or literal)";
                }
            }
            if (problem != null) {
                dataDecision(ctx, "ENVIRONMENT", entry.getName() + ": " + problem);
                continue;
            }
            f.assigned = assigned;
            files.put(f.cobolName, f);
        }
    }

    /** FD entries: their records share the record area (the first record's storage) and are not initialised. */
    private void fileSection(io.proleap.cobol.asg.metamodel.data.file.FileSection section) {
        for (var fd : section.getFileDescriptionEntries()) {
            String name = fd.getName().toUpperCase(Locale.ROOT);
            FileInfo f = files.get(name);
            String problem = null;
            if (f != null) {
                f.sort = ((CobolParser.FileDescriptionEntryContext) fd.getCtx()).SD() != null;
            }
            if (fd.getLinageClause() != null) {
                problem = "LINAGE is not supported";
            } else if (fd.getExternalClause() != null || fd.getGlobalClause() != null) {
                problem = "EXTERNAL / GLOBAL files are not supported";
            } else if (fd.getCodeSetClause() != null || fd.getReportClause() != null) {
                problem = "CODE-SET / REPORT files are not supported";
            } else if (fd.getRecordContainsClause() != null
                    && ((CobolParser.RecordContainsClauseContext) fd.getRecordContainsClause().getCtx()).getText().toUpperCase(Locale.ROOT).contains("DEPENDING")) {
                problem = "RECORD VARYING ... DEPENDING ON is not supported";
            }
            DataItem base = null;
            for (DataDescriptionEntry e : fd.getRootDataDescriptionEntries()) {
                buildItem(e, null);
                DataItem item = items.get(e);
                item.markFileRecord();
                roots.add(item);
                if (base == null) {
                    base = item;
                } else if (item.redefines() == null) {
                    item.redefines(base);
                }
                if (e instanceof DataDescriptionEntryGroup g && hasValue(g)) {
                    dataDecision(e.getCtx(), "DATA", item.cobolName() + ": VALUE in the FILE SECTION is not supported");
                }
                if (f != null) {
                    f.records.add(item);
                }
            }
            if (problem != null) {
                dataDecision(fd.getCtx(), "DATA", fd.getName() + ": " + problem);
                files.remove(name);
            }
        }
    }

    /** OCCURS [min TO] max [DEPENDING ON item] [ASCENDING/DESCENDING KEY ...] [INDEXED BY ...]. */
    private void occurs(DataItem item, DataDescriptionEntryGroup g) {
        var clauses = g.getOccursClauses();
        var ctx = (CobolParser.DataOccursClauseContext) clauses.getFirst().getCtx();
        String problem = null;
        int min = 0;
        int max = 0;
        DataItem depending = null;
        if (clauses.size() > 1) {
            problem = "several OCCURS clauses";
        } else if (item.parent() == null) {
            problem = "OCCURS on a level-01 / level-77 item is not valid";
        } else if (ctx.integerLiteral() == null) {
            problem = "OCCURS with a data-name count is not supported";
        } else {
            max = Integer.parseInt(ctx.integerLiteral().getText());
            min = max;
            if (ctx.dataOccursTo() != null) {
                min = max;
                max = Integer.parseInt(ctx.dataOccursTo().integerLiteral().getText());
            }
            if (ctx.dataOccursDepending() != null) {
                depending = resolveData(ctx.dataOccursDepending().qualifiedDataName());
                if (depending == null || !depending.isInteger() || !depending.dimensions().isEmpty()) {
                    problem = "DEPENDING ON must name an integer numeric item outside any table";
                } else if (ctx.dataOccursTo() == null) {
                    min = 1;
                }
            } else if (ctx.dataOccursTo() != null) {
                problem = "OCCURS min TO max without DEPENDING ON";
            }
            if (problem == null && (max < 1 || min < 0 || min > max)) {
                problem = "OCCURS bounds " + min + " TO " + max + " are not valid";
            }
        }
        List<DataItem.IndexName> idx = new ArrayList<>();
        List<DataItem.Key> keys = new ArrayList<>();
        if (problem == null) {
            var indexed = clauses.getFirst().getOccursIndexed();
            if (indexed != null) {
                for (var index : indexed.getIndices()) {
                    String java = allocateField(Names.fieldName(index.getName()));
                    var n = new DataItem.IndexName(index.getName(), java, item);
                    idx.add(n);
                    indexNames.put(index, n);
                    indexesByName.put(index.getName().toUpperCase(Locale.ROOT), n);
                }
            }
            // Keys are resolved by name: ProLeap leaves a key naming the table item itself undefined.
            for (var sort : ctx.dataOccursSort()) {
                for (var key : sort.qualifiedDataName()) {
                    DataItem k = resolveData(key);
                    if (k == null || !isWithin(k, item)) {
                        problem = "KEY " + key.getText() + " is not an item of the table";
                        break;
                    }
                    keys.add(new DataItem.Key(k, sort.DESCENDING() == null));
                }
            }
        }
        if (problem != null) {
            dataDecision(g.getCtx(), "DATA", item.cobolName() + ": " + problem);
            return;
        }
        item.occurs(min, max, depending);
        item.tableNames(idx, keys);
    }

    private static boolean hasNestedTable(DataItem table) {
        for (DataItem c : table.children()) {
            if (c.occurs() > 0 || hasNestedTable(c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithin(DataItem item, DataItem ancestor) {
        for (DataItem i = item; i != null; i = i.parent()) {
            if (i == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * An OCCURS DEPENDING ON table must end its record (nothing follows it) and may not be
     * nested in or contain another table, so only its own length varies.
     */
    private void checkTable(DataItem item, DataDescriptionEntryGroup g) {
        if (item.dependingOn() == null) {
            return;
        }
        String problem = null;
        if (item.dimensions().size() > 1 || hasNestedTable(item)) {
            problem = "nested tables with OCCURS DEPENDING ON are not supported";
        } else if (item.offset() + item.totalSize() != item.record().size() || item.record().redefines() != null
                || roots.stream().anyMatch(r -> r.redefines() == item.record())) {
            problem = "OCCURS DEPENDING ON must be the last item of a record that is not redefined";
        } else if (isWithin(item.dependingOn(), item)) {
            problem = "DEPENDING ON item inside its own table";
        } else if (item.linkage()) {
            problem = "OCCURS DEPENDING ON in the LINKAGE SECTION is not supported";
        }
        if (problem != null) {
            dataDecision(g.getCtx(), "DATA", item.cobolName() + ": " + problem);
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
        if (Integer.valueOf(78).equals(g.getLevelNumber())) {
            items.put(e, constantItem(g, name, parent, line));
            return;
        }
        DataItem redefined = null;
        String problem = unsupportedClauses(g);
        if (problem == null && g.getRedefinesClause() != null) {
            redefined = redefinedItem(g, parent);
            if (redefined == null) {
                problem = "REDEFINES must name the preceding item at the same level";
            } else if (redefined.kind() == DataItem.Kind.UNSUPPORTED) {
                problem = "REDEFINES of unsupported item " + redefined.cobolName();
            }
        }
        if (problem == null && g.getValueClause() != null && (redefined != null || (parent != null && parent.inRedefines()))) {
            problem = "VALUE in a REDEFINES item is not supported (GnuCOBOL does not initialise it)";
        }
        boolean hasChildren = g.getDataDescriptionEntries().stream().anyMatch(ProgramTranslator::isStorageEntry);
        DataItem.Kind kind = null;
        Picture picture = null;
        String declared = effectiveUsage(g, parent);
        if (problem == null) {
            problem = usageProblem(declared);
        }
        DataItem.Usage usage = usage(declared);
        if (problem == null && g.getSynchronizedClause() != null && !hasChildren && usage != DataItem.Usage.DISPLAY) {
            problem = synchronizedProblem(g);
        }
        if (problem == null) {
            if (hasChildren) {
                if (g.getPictureClause() != null) {
                    problem = "a group item cannot have a PICTURE clause";
                } else if (g.getValueClause() != null) {
                    problem = "VALUE on a group item is not supported";
                } else if (g.getSignClause() != null || g.getJustifiedClause() != null) {
                    problem = "SIGN / JUSTIFIED on a group item is not supported";
                } else {
                    kind = DataItem.Kind.GROUP;
                }
            } else if (usage == DataItem.Usage.INDEX) {
                if (g.getPictureClause() != null || g.getValueClause() != null) {
                    problem = "USAGE INDEX takes no PICTURE or VALUE";
                } else {
                    // An index data item: a native 4-byte integer, shown as S9(9).
                    picture = new Picture.Numeric(9, 0, true);
                    kind = DataItem.Kind.NUMERIC;
                }
            } else if (g.getPictureClause() == null) {
                problem = "an elementary item without PICTURE is not supported";
            } else {
                Picture.Result pic = Picture.parse(g.getPictureClause().getPictureString());
                if (pic.picture().isEmpty()) {
                    problem = pic.unsupportedReason();
                } else {
                    picture = pic.picture().get();
                    if (g.getBlankWhenZeroClause() != null && usage == DataItem.Usage.DISPLAY
                            && picture instanceof Picture.Numeric n && !n.signed()) {
                        // BLANK WHEN ZERO makes a numeric DISPLAY item numeric-edited.
                        String digits = "9".repeat(n.digits() - n.scale()) + (n.scale() > 0 ? "V" + "9".repeat(n.scale()) : "");
                        picture = new Picture.Edited(digits, true, n.digits());
                    }
                    kind = switch (picture) {
                        case Picture.Numeric n -> DataItem.Kind.NUMERIC;
                        case Picture.Edited ed -> DataItem.Kind.EDITED;
                        case Picture.Alphanumeric a -> DataItem.Kind.ALPHANUMERIC;
                    };
                    problem = elementaryClauses(g, picture);
                    if (problem == null && g.getBlankWhenZeroClause() != null && !(picture instanceof Picture.Edited ed && ed.numeric())) {
                        problem = "BLANK WHEN ZERO on an item that is not unsigned numeric or numeric-edited";
                    }
                    if (problem == null && picture instanceof Picture.Edited && usage != DataItem.Usage.DISPLAY) {
                        problem = "an edited item must be USAGE DISPLAY";
                    }
                    if (problem == null && usage != DataItem.Usage.DISPLAY) {
                        if (!(picture instanceof Picture.Numeric n)) {
                            problem = "USAGE " + declared + " needs a numeric PICTURE";
                        } else if (g.getSignClause() != null) {
                            problem = "SIGN clause on a USAGE " + declared + " item";
                        } else if (usage != DataItem.Usage.PACKED && n.digits() > 18) {
                            problem = "binary item with more than 18 digits";
                        }
                    }
                }
            }
        }
        DataItem item;
        if (problem != null) {
            item = new DataItem(name, DataItem.Kind.UNSUPPORTED, null, problem, parent, line);
            dataDecision(e.getCtx(), "DATA", name + ": " + problem);
        } else {
            item = new DataItem(name, kind, picture, null, parent, line);
            item.redefines(redefined);
            if (kind == DataItem.Kind.NUMERIC) {
                item.usage(usage);
            }
            if (kind == DataItem.Kind.GROUP && declaredUsage(g) != null) {
                inheritedUsage.put(item, declared);
            } else if (kind == DataItem.Kind.GROUP && parent != null && inheritedUsage.containsKey(parent)) {
                inheritedUsage.put(item, inheritedUsage.get(parent));
            }
            if (!g.getOccursClauses().isEmpty()) {
                tables.add(new PendingTable(item, g));
            }
            if (g.getSignClause() != null) {
                item.sign(g.getSignClause().getSignClauseType() == io.proleap.cobol.asg.metamodel.data.datadescription.SignClause.SignClauseType.LEADING,
                        g.getSignClause().isSeparate());
            }
            item.justified(g.getJustifiedClause() != null);
            item.blankWhenZero(g.getBlankWhenZeroClause() != null);
            if (kind != DataItem.Kind.GROUP) {
                String valueProblem = initialValue(item, g);
                if (valueProblem != null) {
                    dataDecision(e.getCtx(), "DATA", name + ": " + valueProblem);
                }
            }
        }
        items.put(e, item);
        // Every elementary item (FILLER too: its bytes are initialised) and every named group gets a view.
        if (item.isAlphanumeric() || item.isNumeric() || item.isEdited() || (item.isGroup() && !item.isFiller())) {
            item.javaName(allocateField(Names.fieldName(name)));
            fieldOrder.add(item);
        }
        for (DataDescriptionEntry child : g.getDataDescriptionEntries()) {
            buildItem(child, item);
        }
    }

    private static boolean hasValue(DataDescriptionEntryGroup g) {
        if (g.getValueClause() != null) {
            return true;
        }
        return g.getDataDescriptionEntries().stream().anyMatch(c -> c instanceof DataDescriptionEntryGroup cg
                && c.getDataDescriptionEntryType() != DataDescriptionEntry.DataDescriptionEntryType.CONDITION && hasValue(cg));
    }

    /** Entries that occupy storage in their parent: not level-88 condition-names or level-78 constants. */
    private static boolean isStorageEntry(DataDescriptionEntry e) {
        return e.getDataDescriptionEntryType() != DataDescriptionEntry.DataDescriptionEntryType.CONDITION
                && !Integer.valueOf(78).equals(e.getLevelNumber());
    }

    /** The item a REDEFINES names: the preceding sibling at the same level (skipping other redefinitions of it). */
    private DataItem redefinedItem(DataDescriptionEntryGroup g, DataItem parent) {
        if (!(g.getRedefinesClause().getRedefinesCall().unwrap() instanceof DataDescriptionEntryCall call)) {
            return null;
        }
        DataItem target = items.get(call.getDataDescriptionEntry());
        if (target == null || target.parent() != parent) {
            return null;
        }
        List<DataItem> siblings = parent == null ? roots : parent.children();
        for (int i = siblings.size() - 1; i >= 0; i--) {
            DataItem s = siblings.get(i);
            if (s == target) {
                return target;
            }
            if (s.redefines() != target && !s.isCondition() && s.kind() != DataItem.Kind.CONSTANT) {
                return null;
            }
        }
        return null;
    }

    /**
     * SYNCHRONIZED on a binary item: no slack bytes (GnuCOBOL's default) when the project says
     * so; aligned layouts are not implemented.
     */
    private String synchronizedProblem(DataDescriptionEntryGroup g) {
        try {
            boolean align = require("sync_alignment", in.dialect().syncAlignment(), g.getCtx(), "SYNCHRONIZED");
            return align ? "SYNCHRONIZED with slack bytes (Dialect.sync_alignment = true) is not supported" : null;
        } catch (HandlerException e) {
            return null;
        }
    }

    /** SIGN only on signed numeric items, JUSTIFIED only on alphanumeric ones (as GnuCOBOL requires). */
    private static String elementaryClauses(DataDescriptionEntryGroup g, Picture picture) {
        if (g.getSignClause() != null && !(picture instanceof Picture.Numeric n && n.signed())) {
            return "SIGN clause on an item whose PICTURE has no S";
        }
        if (g.getJustifiedClause() != null && !(picture instanceof Picture.Alphanumeric)) {
            return "JUSTIFIED on a non-alphanumeric item";
        }
        return null;
    }

    /** A level-78 constant (GnuCOBOL extension): a name for its VALUE literal, without storage. */
    private DataItem constantItem(DataDescriptionEntryGroup g, String name, DataItem parent, int line) {
        String problem = null;
        Operand value = null;
        var clause = g.getValueClause();
        if (clause == null || clause.getValueIntervals().size() != 1 || clause.getValueIntervals().getFirst().getToValueStmt() != null) {
            problem = "level 78 needs a single VALUE";
        } else {
            try {
                value = operand(clause.getValueIntervals().getFirst().getFromValueStmt());
                if (!(value instanceof Operand.AlphanumericLiteral || value instanceof Operand.NumericLiteral)) {
                    problem = "level 78 VALUE " + value.category() + " is not supported (only literals)";
                }
            } catch (HandlerException ex) {
                if (ex.kind() == HandlerException.Kind.DIALECT) {
                    return new DataItem(name, DataItem.Kind.UNSUPPORTED, null, "needs Dialect." + ex.getMessage(), parent, line);
                }
                problem = "VALUE " + ex.getMessage();
            }
        }
        if (problem != null) {
            dataDecision(g.getCtx(), "DATA", name + ": " + problem);
            return new DataItem(name, DataItem.Kind.UNSUPPORTED, null, problem, parent, line);
        }
        DataItem item = new DataItem(name, DataItem.Kind.CONSTANT, null, null, parent, line);
        item.constant(value);
        return item;
    }

    /**
     * Assigns byte offsets: elementary items take their PICTURE's size (plus a separate sign),
     * groups the sum of their children. A REDEFINES item starts where the item it redefines
     * starts; the group is as long as the longest of them.
     */
    private int layout(DataItem item, int offset) {
        int size = switch (item.kind()) {
            case ALPHANUMERIC -> item.alphanumeric().length();
            case EDITED -> item.edited().length();
            case NUMERIC -> switch (item.usage()) {
                case DISPLAY -> item.numeric().digits() + (item.signSeparate() ? 1 : 0);
                case PACKED -> item.numeric().digits() / 2 + 1;
                case BINARY, NATIVE -> item.numeric().digits() <= 2 ? 1 : item.numeric().digits() <= 4 ? 2 : item.numeric().digits() <= 9 ? 4 : 8;
                case INDEX -> 4;
            };
            case GROUP -> {
                int at = offset;
                int end = offset;
                for (DataItem child : item.children()) {
                    if (child.redefines() != null) {
                        int childEnd = layout(child, child.redefines().offset());
                        if (childEnd - child.offset() > child.redefines().totalSize()) {
                            dataDecision(child, "REDEFINES larger than the redefined item " + child.redefines().cobolName() + " is not supported");
                        }
                        end = Math.max(end, childEnd);
                    } else {
                        at = layout(child, Math.max(at, end));
                        end = at;
                    }
                }
                yield end - offset;
            }
            // Unsupported items have their own decision; nothing after them is written.
            case CONDITION, CONSTANT, UNSUPPORTED -> 0;
        };
        item.layout(offset, size);
        return offset + item.totalSize();
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
        if (g.getExternalClause() != null || g.getGlobalClause() != null) {
            return "EXTERNAL / GLOBAL is not supported";
        }
        return null;
    }

    /** The usage written on an entry, or null; COMP-X is read from the parse tree (a grammar extension). */
    private static String declaredUsage(DataDescriptionEntryGroup g) {
        UsageClause usage = g.getUsageClause();
        if (usage == null) {
            return null;
        }
        var ctx = (CobolParser.DataUsageClauseContext) usage.getCtx();
        if (ctx.COMP_X() != null || ctx.COMPUTATIONAL_X() != null) {
            return "COMP_X";
        }
        if (ctx.BINARY() != null && (ctx.TRUNCATED() != null || ctx.EXTENDED() != null)) {
            return "BINARY_" + (ctx.TRUNCATED() != null ? "TRUNCATED" : "EXTENDED");
        }
        return usage.getUsageClauseType() == null ? ctx.getText() : usage.getUsageClauseType().name();
    }

    private static DataItem.Usage usage(String declared) {
        return switch (declared) {
            case "DISPLAY" -> DataItem.Usage.DISPLAY;
            case "BINARY", "COMP", "COMP_4", "COMP_X" -> DataItem.Usage.BINARY;
            case "COMP_3", "PACKED_DECIMAL" -> DataItem.Usage.PACKED;
            case "COMP_5" -> DataItem.Usage.NATIVE;
            case "INDEX" -> DataItem.Usage.INDEX;
            default -> null;
        };
    }

    /** Why a usage is not translated, or null. COMP-1/COMP-2 are binary floating point (saved for Jev). */
    private static String usageProblem(String declared) {
        return switch (declared) {
            case "COMP_1", "COMP_2" -> "USAGE " + declared.replace('_', '-') + " (binary floating point) is not supported: results are not bit-exact";
            case "POINTER", "PROCEDURE_POINTER", "FUNCTION_POINTER" -> "USAGE " + declared + " (addresses) is not supported";
            default -> usage(declared) == null ? "USAGE " + declared + " is not supported" : null;
        };
    }

    /** The usage an entry has: its own, else its group's. */
    private String effectiveUsage(DataDescriptionEntryGroup g, DataItem parent) {
        String own = declaredUsage(g);
        if (own != null) {
            return own;
        }
        return parent == null ? "DISPLAY" : inheritedUsage.getOrDefault(parent, "DISPLAY");
    }

    /**
     * REDEFINES lets one item's bytes be read as another's; for signed zoned items those bytes
     * depend on the original sign convention, so the program needs Dialect.encoding (and only
     * ASCII, GnuCOBOL's convention, is implemented).
     */
    private void checkByteImages() {
        for (DataItem item : items.values()) {
            if (item.redefines() == null) {
                continue;
            }
            boolean sensitive = item.elementaryItems().stream().anyMatch(DataItem::encodingSensitive)
                    || item.redefines().elementaryItems().stream().anyMatch(DataItem::encodingSensitive);
            if (sensitive) {
                try {
                    requireAsciiBytes(entryCtx(item), "REDEFINES of " + item.redefines().cobolName());
                } catch (HandlerException e) {
                    if (e.kind() != HandlerException.Kind.DIALECT) {
                        dataDecision(item, e.getMessage());
                    }
                }
            }
        }
    }

    private void requireAsciiBytes(ParserRuleContext where, String usage) throws HandlerException {
        String encoding = require("encoding", in.dialect().encoding(), where, usage + " (byte image of signed zoned items)");
        if (!encoding.equals("ascii")) {
            throw HandlerException.unsupported(usage + ": the EBCDIC byte image of signed zoned items is not supported");
        }
    }

    private ParserRuleContext entryCtx(DataItem item) {
        for (var e : items.entrySet()) {
            if (e.getValue() == item) {
                return e.getKey().getCtx();
            }
        }
        throw new IllegalStateException("no entry for " + item.cobolName());
    }

    private void dataDecision(DataItem item, String message) {
        dataDecision(entryCtx(item), "DATA", item.cobolName() + ": " + message);
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
        if (item.isEdited()) {
            switch (o) {
                case Operand.AlphanumericLiteral a -> item.initialValue(a.value());
                case Operand.NumericLiteral n when item.edited().numeric() -> item.initialNumber(n.value().toPlainString());
                case Operand.Figurative f when f.isSingle() && !f.isZero() -> item.initialValue(repeat(f.pattern(), item.edited().length()));
                case Operand.Figurative f when f.isZero() && item.edited().numeric() -> item.initialNumber("0");
                default -> {
                    return "VALUE " + o.category() + " for an edited item is not supported";
                }
            }
            return null;
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
     * order as GnuCOBOL initialises WORKING-STORAGE (VALUE, else spaces / zeros by category;
     * REDEFINES items are not initialised). A level-01 / level-77 REDEFINES shares the storage of
     * the record it redefines. A plain level-01 / level-77 elementary item is a standalone field.
     */
    private void addFields(ClassOrInterfaceDeclaration cls) {
        cls.addFieldWithInitializer("NumericField", "returnCode", create("NumericField", integer(9), integer(0), Ast.bool(true)),
                Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        Map<DataItem, String> storages = storageNames;
        Map<DataItem, Integer> sizes = new IdentityHashMap<>();
        for (DataItem r : roots) {
            DataItem base = storageRoot(r);
            sizes.merge(base, r.totalSize(), Math::max);
            if (r.linkage() && r == base && r.kind() != DataItem.Kind.UNSUPPORTED && r.kind() != DataItem.Kind.CONSTANT
                    && !parameterFields.containsKey(r)) {
                String param = allocateField(Names.fieldName(r.cobolName()) + "Parameter");
                parameterFields.put(r, param);
                cls.addField("AlphanumericField", param, Modifier.Keyword.PRIVATE)
                        .setLineComment(" LINKAGE " + r.cobolName() + ": the caller's bytes (null until passed)");
            }
        }
        List<DataItem> accessors = new ArrayList<>();
        for (DataItem item : fieldOrder) {
            DataItem record = storageRoot(item.record());
            if (item.linkage()) {
                accessors.add(item);
                continue;
            }
            if (item.hasAccessor()) {
                String storage = storageField(cls, record, storages, sizes);
                accessors.add(item);
                if (!item.isGroup() && !item.inRedefines() && !item.fileRecord()) {
                    cls.addMember(tableInitializer(item, storage));
                }
                for (DataItem.IndexName idx : item.indexes()) {
                    // GnuCOBOL starts every index at occurrence 1.
                    cls.addFieldWithInitializer(PrimitiveType.intType(), idx.javaName(), integer(1), Modifier.Keyword.PRIVATE)
                            .setLineComment(" INDEX " + idx.cobolName() + " of " + item.cobolName());
                }
                continue;
            }
            for (DataItem.IndexName idx : item.indexes()) {
                cls.addFieldWithInitializer(PrimitiveType.intType(), idx.javaName(), integer(1), Modifier.Keyword.PRIVATE)
                        .setLineComment(" INDEX " + idx.cobolName() + " of " + item.cobolName());
            }
            Expression init;
            if (item == record && !item.isGroup() && plain(item) && !item.fileRecord() && roots.stream().noneMatch(r -> r.redefines() == item)) {
                init = standalone(item);
            } else {
                init = view(item, name(storageField(cls, record, storages, sizes)));
            }
            var field = cls.addFieldWithInitializer(javaType(item), item.javaName(), init, Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
            field.setLineComment(" " + item.cobolName() + " (line " + item.line() + ")");
        }
        for (FileInfo f : files.values()) {
            DataItem base = storageRoot(f.records.getFirst());
            String storage = storageField(cls, base, storages, sizes);
            int size = sizes.get(base);
            f.javaName = allocateField(Names.fieldName(f.cobolName));
            if (f.sort) {
                cls.addFieldWithInitializer("SortFile", f.javaName, create("SortFile", str(f.cobolName),
                        create("AlphanumericField", name(storage), integer(0), integer(size))), Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL)
                        .setLineComment(" SD " + f.cobolName);
                continue;
            }
            Expression status = f.status == null ? new NullLiteralExpr() : name(f.status.javaName());
            cls.addFieldWithInitializer("CobolFile", f.javaName, create("CobolFile", str(f.cobolName), str(f.path), Ast.bool(f.lineSequential),
                    create("AlphanumericField", name(storage), integer(0), integer(size)), status), Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL)
                    .setLineComment(" FD " + f.cobolName + (f.lineSequential ? " (LINE SEQUENTIAL)" : " (SEQUENTIAL)"));
        }
        for (DataItem item : accessors) {
            addAccessor(cls, item, storages.get(storageRoot(item.record())));
        }
        if (in.unit().getProcedureDivision() != null && in.unit().getProcedureDivision().getUsingClause() != null) {
            addUsing(cls);
        }
    }

    private static String javaType(DataItem item) {
        return item.isNumeric() ? "NumericField" : item.isEdited() ? "EditedField" : "AlphanumericField";
    }

    private String storageField(ClassOrInterfaceDeclaration cls, DataItem record, Map<DataItem, String> storages, Map<DataItem, Integer> sizes) {
        String storage = storages.get(record);
        if (storage == null) {
            storage = allocateField(Names.fieldName(record.cobolName()) + "Storage");
            storages.put(record, storage);
            int size = sizes.get(record);
            cls.addFieldWithInitializer("Storage", storage, create("Storage", integer(Math.max(1, size))),
                    Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL)
                    .setLineComment(" " + record.cobolName() + " (" + size + " bytes)");
        }
        return storage;
    }

    /**
     * The accessor of a table item ({@code amt(i, j)}: a view of that occurrence, subscripts
     * checked) or of a group whose length depends on an OCCURS DEPENDING ON item.
     */
    private void addAccessor(ClassOrInterfaceDeclaration cls, DataItem item, String storage) {
        MethodDeclaration m = cls.addMethod(item.javaName(), Modifier.Keyword.PRIVATE);
        m.setType(javaType(item));
        m.setLineComment(" " + item.cobolName() + " (line " + item.line() + ")");
        List<DataItem> dims = item.dimensions();
        Expression offset = integer(item.offset());
        BlockStmt body = new BlockStmt();
        Expression storageExpr = storage == null ? null : name(storage);
        if (item.linkage()) {
            DataItem record = storageRoot(item.record());
            body.addStatement(declare(type("AlphanumericField"), "parameter", staticCall("ProgramSupport", "parameter",
                    name(parameterFields.get(record)), str(record.cobolName()))));
            storageExpr = call(name("parameter"), "storage");
            offset = new BinaryExpr(call(name("parameter"), "offset"), integer(item.offset()), BinaryExpr.Operator.PLUS);
        }
        for (int d = 0; d < dims.size(); d++) {
            DataItem t = dims.get(d);
            String param = "s" + (d + 1);
            m.addParameter(PrimitiveType.intType(), param);
            Expression checked = t.dependingOn() == null
                    ? staticCall("Bounds", "subscript", name(param), integer(t.occurs()), str(item.cobolName()))
                    : staticCall("Bounds", "subscript", name(param), call(name(t.dependingOn().javaName()), "intValue"),
                            integer(t.occursMin()), integer(t.occurs()), str(item.cobolName()), str(t.dependingOn().cobolName()));
            offset = new BinaryExpr(offset, new BinaryExpr(new EnclosedExpr(new BinaryExpr(checked, integer(1), BinaryExpr.Operator.MINUS)),
                    integer(t.size()), BinaryExpr.Operator.MULTIPLY), BinaryExpr.Operator.PLUS);
        }
        Expression view;
        if (item.isGroup()) {
            DataItem table = item.variableTable();
            Expression length = integer(item.size());
            if (table != null && !dims.contains(table)) {
                // Up to the end of the occurrences in use.
                int fixed = table.offset() - item.offset();
                length = new BinaryExpr(integer(fixed), new BinaryExpr(
                        staticCall("Bounds", "depending", call(name(table.dependingOn().javaName()), "intValue"),
                                integer(table.occursMin()), integer(table.occurs()), str(table.dependingOn().cobolName())),
                        integer(table.size()), BinaryExpr.Operator.MULTIPLY), BinaryExpr.Operator.PLUS);
            }
            view = create("AlphanumericField", storageExpr, offset, length);
        } else {
            view = viewConstructor(item, storageExpr, offset);
        }
        body.addStatement(new ReturnStmt(view));
        m.setBody(body);
    }

    /** {@code using(arguments)}: binds the LINKAGE records named by PROCEDURE DIVISION USING to the caller's arguments. */
    private void addUsing(ClassOrInterfaceDeclaration cls) {
        MethodDeclaration m = cls.addMethod("using", Modifier.Keyword.PUBLIC);
        m.addMarkerAnnotation("Override");
        m.setType(new VoidType());
        m.addParameter(new Parameter(new ArrayType(type("AlphanumericField")), "arguments"));
        BlockStmt body = new BlockStmt();
        List<DataItem> using;
        try {
            using = usingItems();
        } catch (HandlerException e) {
            dataDecision(in.unit().getProcedureDivision().getCtx(), "PROCEDURE", e.getMessage());
            using = List.of();
        }
        for (int i = 0; i < using.size(); i++) {
            String field = parameterFields.get(storageRoot(using.get(i)));
            body.addStatement(stmt(new AssignExpr(name(field), new com.github.javaparser.ast.expr.ConditionalExpr(
                    new BinaryExpr(new com.github.javaparser.ast.expr.FieldAccessExpr(name("arguments"), "length"), integer(i),
                            BinaryExpr.Operator.GREATER),
                    new com.github.javaparser.ast.expr.ArrayAccessExpr(name("arguments"), integer(i)), new NullLiteralExpr()),
                    AssignExpr.Operator.ASSIGN)));
        }
        body.addStatement(stmt(new AssignExpr(name("callParameters"),
                new com.github.javaparser.ast.expr.FieldAccessExpr(name("arguments"), "length"), AssignExpr.Operator.ASSIGN)));
        m.setBody(body);
        cls.addField(PrimitiveType.intType(), "callParameters", Modifier.Keyword.PRIVATE)
                .setLineComment(" NUMBER-OF-CALL-PARAMETERS");
    }

    /** {@code { for (int i1 = 0; i1 < n1; i1++) ... view(i1, ...).initial(...); }} over every occurrence. */
    private com.github.javaparser.ast.body.InitializerDeclaration tableInitializer(DataItem item, String storage) {
        List<DataItem> dims = item.dimensions();
        Expression offset = integer(item.offset());
        for (int d = 0; d < dims.size(); d++) {
            offset = new BinaryExpr(offset, new BinaryExpr(name("i" + (d + 1)), integer(dims.get(d).size()), BinaryExpr.Operator.MULTIPLY),
                    BinaryExpr.Operator.PLUS);
        }
        Expression v = viewConstructor(item, name(storage), offset);
        Statement body = stmt(initialCall(item, v));
        for (int d = dims.size() - 1; d >= 0; d--) {
            String i = "i" + (d + 1);
            body = new com.github.javaparser.ast.stmt.ForStmt(
                    NodeList.nodeList(new com.github.javaparser.ast.expr.VariableDeclarationExpr(
                            new com.github.javaparser.ast.body.VariableDeclarator(PrimitiveType.intType(), i, integer(0)))),
                    new BinaryExpr(name(i), integer(dims.get(d).occurs()), BinaryExpr.Operator.LESS),
                    NodeList.nodeList(new UnaryExpr(name(i), UnaryExpr.Operator.POSTFIX_INCREMENT)),
                    new BlockStmt(NodeList.nodeList(body)));
        }
        var init = new com.github.javaparser.ast.body.InitializerDeclaration(false, new BlockStmt(NodeList.nodeList(body)));
        init.setLineComment(" " + item.cobolName() + " (line " + item.line() + "), every occurrence");
        return init;
    }

    private Expression viewConstructor(DataItem item, Expression storage, Expression offset) {
        if (item.isEdited()) {
            return create("EditedField", storage, offset, str(item.edited().picture()), Ast.bool(item.blankWhenZero()), Ast.bool(decimalComma));
        }
        if (item.isAlphanumeric()) {
            return item.justified()
                    ? create("AlphanumericField", storage, offset, integer(item.size()), Ast.bool(true))
                    : create("AlphanumericField", storage, offset, integer(item.size()));
        }
        Picture.Numeric n = item.numeric();
        return switch (item.usage()) {
            case PACKED -> staticCall("NumericField", "packed", storage, offset, integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()));
            case BINARY -> staticCall("NumericField", "binary", storage, offset, integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()),
                    Ast.bool(binaryTruncate));
            case NATIVE -> staticCall("NumericField", "nativeBinary", storage, offset, integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()));
            case INDEX -> staticCall("NumericField", "index", storage, offset);
            case DISPLAY -> !item.signLeading() && !item.signSeparate()
                    ? create("NumericField", storage, offset, integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()))
                    : create("NumericField", storage, offset, integer(n.digits()), integer(n.scale()), Ast.bool(n.signed()),
                            Ast.bool(item.signLeading()), Ast.bool(item.signSeparate()));
        };
    }

    /** The record whose storage a level-01 item uses (following REDEFINES). */
    private static DataItem storageRoot(DataItem record) {
        DataItem r = record;
        while (r.redefines() != null && r.parent() == null) {
            r = r.redefines();
        }
        return r;
    }

    /** No clause or usage that needs the full view constructor. */
    private static boolean plain(DataItem item) {
        return !item.justified() && !item.signLeading() && !item.signSeparate() && !item.isEdited()
                && (!item.isNumeric() || item.usage() == DataItem.Usage.DISPLAY);
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

    private Expression view(DataItem item, Expression storage) {
        if (item.isGroup()) {
            // Group bytes are initialised through its elementary items.
            return create("AlphanumericField", storage, integer(item.offset()), integer(Math.max(1, item.size())));
        }
        Expression v = viewConstructor(item, storage, integer(item.offset()));
        if (item.inRedefines() || item.fileRecord()) {
            return v;
        }
        return initialCall(item, v);
    }

    private static Expression initialCall(DataItem item, Expression view) {
        if (item.initialValue() == null) {
            return call(view, "initial");
        }
        return call(view, item.initialIsNumber() ? "initialNumber" : "initial", str(item.initialValue()));
    }

    // ------------------------------------------------------------ structure

    private void addEntryPoints(ClassOrInterfaceDeclaration cls) {
        cls.addField(in.className(), "instance", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);

        MethodDeclaration instance = cls.addMethod("instance", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);
        instance.setType(in.className());
        var programId = in.unit().getIdentificationDivision() == null ? null : in.unit().getIdentificationDivision().getProgramIdParagraph();
        var attribute = programId == null ? null : programId.getAttribute();
        if (attribute == io.proleap.cobol.asg.metamodel.identification.ProgramIdParagraph.Attribute.INITIAL) {
            instance.setJavadocComment("IS INITIAL: a fresh instance (WORKING-STORAGE in its initial state) for every CALL.");
            instance.setBody(new BlockStmt(NodeList.nodeList(new ReturnStmt(create(in.className())))));
        } else {
            if (attribute != null) {
                dataDecision(programId.getCtx(), "IDENTIFICATION", "PROGRAM-ID ... IS " + attribute + " is not supported");
            }
            instance.setJavadocComment("The program's single instance in this run unit (WORKING-STORAGE persists across CALLs).");
            instance.setBody(new BlockStmt(NodeList.nodeList(
                    new IfStmt(new BinaryExpr(name("instance"), new NullLiteralExpr(), BinaryExpr.Operator.EQUALS),
                            stmt(new AssignExpr(name("instance"), create(in.className()), AssignExpr.Operator.ASSIGN)), null),
                    new ReturnStmt(name("instance")))));
        }

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
        if (pd.getGivingClause() != null) {
            dataDecision(pd.getCtx(), "PROCEDURE", "PROCEDURE DIVISION RETURNING is not supported");
        }
        if (pd.getDeclaratives() != null) {
            dataDecision(pd.getDeclaratives().getCtx(), "PROCEDURE", "DECLARATIVES are not supported");
        }
        scanProcedures(pd);
        if (dispatcher) {
            addDispatcher(cls, pd, body);
            return;
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

    /**
     * The dispatcher form, for programs with GO TO, PERFORM THRU or ALTER: every procedure is a
     * method returning the procedure to jump to (or -1 when it ends normally), {@code run} steps
     * through them in order, and {@code perform(from, thru)} runs a range until its end procedure
     * ends while it is the innermost PERFORM, as GnuCOBOL does.
     */
    private void addDispatcher(ClassOrInterfaceDeclaration cls, ProcedureDivision pd, BlockStmt main) {
        Set<String> methodNames = new HashSet<>(Set.of("procedureDivision", "returnCode", "instance", "main", "using", "perform", "run",
                "procedure"));
        if (!pd.getStatements().isEmpty() || nextSentences.containsKey(pd)) {
            addNode(pd, null, unique(methodNames, "start"), false);
        }
        for (Paragraph p : pd.getRootParagraphs()) {
            addNode(p, null, unique(methodNames, Names.methodName("para", p.getName())), false);
        }
        for (Section s : pd.getSections()) {
            addNode(s, s, unique(methodNames, Names.methodName("section", s.getName())), false);
            for (Paragraph p : s.getParagraphs()) {
                addNode(p, s, unique(methodNames, Names.methodName("para", p.getName())), false);
            }
            sectionEnds.put(s, nodes.size());
            nodes.add(new Node(null, s, null, true));
        }
        collectPerformRanges(pd);
        for (var e : alterTargets(pd).entrySet()) {
            String field = allocateField("altered" + Names.className(e.getKey().getName()));
            alterable.put(e.getKey(), field);
            cls.addFieldWithInitializer(PrimitiveType.intType(), field, integer(e.getValue()), Modifier.Keyword.PRIVATE)
                    .setLineComment(" GO TO target of " + e.getKey().getName() + " (changed by ALTER)");
        }
        cls.addFieldWithInitializer("PerformStack", "performs", create("PerformStack"), Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (n.sectionEnd()) {
                continue;
            }
            MethodDeclaration m = cls.addMethod(n.method(), Modifier.Keyword.PRIVATE);
            m.setType(PrimitiveType.intType());
            m.setLineComment(" " + (n.scope() instanceof Section ? "SECTION " : n.scope() instanceof Paragraph ? "PARAGRAPH " : "")
                    + (n.scope() instanceof io.proleap.cobol.asg.metamodel.NamedElement named ? named.getName() : "PROCEDURE DIVISION")
                    + " (procedure " + i + ")");
            BlockStmt body = new BlockStmt();
            currentNode = i;
            currentParagraph = n.scope() instanceof Paragraph p ? p : null;
            currentSection = n.section();
            translateInto(n.scope(), body);
            currentNode = -1;
            currentParagraph = null;
            currentSection = null;
            body.addStatement(new ReturnStmt(integer(-1)));
            m.setBody(body);
        }
        // procedure(n): runs procedure n.
        MethodDeclaration proc = cls.addMethod("procedure", Modifier.Keyword.PRIVATE);
        proc.setType(PrimitiveType.intType());
        proc.addParameter(PrimitiveType.intType(), "n");
        NodeList<com.github.javaparser.ast.stmt.SwitchEntry> entries = new NodeList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (!nodes.get(i).sectionEnd()) {
                entries.add(new com.github.javaparser.ast.stmt.SwitchEntry(NodeList.nodeList(integer(i)),
                        com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP,
                        NodeList.nodeList(new ReturnStmt(thisCall(nodes.get(i).method())))));
            }
        }
        entries.add(new com.github.javaparser.ast.stmt.SwitchEntry(new NodeList<>(), com.github.javaparser.ast.stmt.SwitchEntry.Type.STATEMENT_GROUP,
                NodeList.nodeList(new ReturnStmt(integer(-1)))));
        proc.setBody(new BlockStmt(NodeList.nodeList(new com.github.javaparser.ast.stmt.SwitchStmt(name("n"), entries))));
        proc.setJavadocComment("Runs procedure n; returns the procedure a GO TO goes to, or -1 when n ends normally (a section's end does nothing).");
        // run(from): procedures in order from `from`, following GO TOs; returns when the innermost PERFORM ends.
        MethodDeclaration run = cls.addMethod("run", Modifier.Keyword.PRIVATE);
        run.setType(new VoidType());
        run.addParameter(PrimitiveType.intType(), "from");
        BlockStmt loop = new BlockStmt(NodeList.nodeList(
                declare(Ast.intType(), "next", thisCall("procedure", name("p"))),
                new IfStmt(new BinaryExpr(name("next"), integer(0), BinaryExpr.Operator.GREATER_EQUALS),
                        new BlockStmt(NodeList.nodeList(stmt(new AssignExpr(name("p"), name("next"), AssignExpr.Operator.ASSIGN)))),
                        new IfStmt(call(name("performs"), "returnsAt", name("p")), new ReturnStmt(),
                                stmt(new UnaryExpr(name("p"), UnaryExpr.Operator.POSTFIX_INCREMENT))))));
        run.setBody(new BlockStmt(NodeList.nodeList(
                declare(Ast.intType(), "depth", call(name("performs"), "depth")),
                declare(Ast.intType(), "p", name("from")),
                new WhileStmt(new BinaryExpr(name("p"), integer(nodes.size()), BinaryExpr.Operator.LESS), loop),
                // Falling off the end of the PROCEDURE DIVISION inside a PERFORM ends the program.
                new IfStmt(new BinaryExpr(name("depth"), integer(0), BinaryExpr.Operator.GREATER),
                        stmt(staticCall("ProgramSupport", "goback", new com.github.javaparser.ast.expr.ThisExpr())), null))));
        MethodDeclaration perform = cls.addMethod("perform", Modifier.Keyword.PRIVATE);
        perform.setType(new VoidType());
        perform.addParameter(PrimitiveType.intType(), "from");
        perform.addParameter(PrimitiveType.intType(), "thru");
        perform.setBody(new BlockStmt(NodeList.nodeList(stmt(call(name("performs"), "push", name("thru"))),
                stmt(thisCall("run", name("from"))))));
        main.addStatement(stmt(thisCall("run", integer(0))));
    }

    private void addNode(Scope scope, Section section, String method, boolean sectionEnd) {
        nodeIndex.put(scope, nodes.size());
        nodes.add(new Node(scope, section, method, sectionEnd));
    }

    /** The first and last procedure of {@code PERFORM a [THRU b]} (a section ends at its end node). */
    private int[] range(Call from, Call thru) throws HandlerException {
        int a = procedureIndex(from, false);
        int b = procedureIndex(thru == null ? from : thru, true);
        if (b < a) {
            throw HandlerException.unsupported("PERFORM ... THRU a procedure before the first one");
        }
        return new int[] {a, b};
    }

    private int procedureIndex(Call call, boolean end) throws HandlerException {
        Object target = switch (call.unwrap()) {
            case ProcedureCall p -> p.getParagraph();
            case SectionCall s -> s.getSection();
            default -> null;
        };
        if (target instanceof Section s && end) {
            return sectionEnds.get(s);
        }
        Integer i = target == null ? null : nodeIndex.get(target);
        if (i == null) {
            throw HandlerException.unsupported("procedure " + call.getName() + " cannot be resolved");
        }
        return i;
    }

    private void collectPerformRanges(ProcedureDivision pd) {
        var registry = in.unit().getProgram().getASGElementRegistry();
        walk(pd.getCtx(), tree -> {
            if (tree instanceof CobolParser.PerformProcedureStatementContext perform
                    && registry.getASGElement(perform) instanceof PerformProcedureStatement proc) {
                try {
                    var calls = proc.getCalls();
                    int[] r = range(calls.getFirst(), calls.size() > 1 ? calls.getLast() : null);
                    performRanges.add(new int[] {r[0], r[1], nodeOf(perform)});
                } catch (HandlerException e) {
                    // The PERFORM itself reports it.
                }
            }
        });
    }

    /** The node a statement is in. */
    private int nodeOf(ParserRuleContext ctx) {
        var registry = in.unit().getProgram().getASGElementRegistry();
        for (ParserRuleContext up = ctx; up != null; up = up.getParent()) {
            if (registry.getASGElement(up) instanceof Scope scope && nodeIndex.containsKey(scope)) {
                return nodeIndex.get(scope);
            }
        }
        return -1;
    }

    /** Paragraphs named by ALTER (each must hold only a GO TO), with their original target. */
    private Map<Paragraph, Integer> alterTargets(ProcedureDivision pd) {
        Map<Paragraph, Integer> out = new IdentityHashMap<>();
        var registry = in.unit().getProgram().getASGElementRegistry();
        walk(pd.getCtx(), tree -> {
            if (tree instanceof CobolParser.AlterStatementContext a
                    && registry.getASGElement(a) instanceof io.proleap.cobol.asg.metamodel.procedure.alter.AlterStatement alter) {
                for (var to : alter.getProceedTos()) {
                    if (to.getSourceCall().unwrap() instanceof ProcedureCall pc && pc.getParagraph() != null
                            && pc.getParagraph().getStatements().size() == 1
                            && pc.getParagraph().getStatements().getFirst() instanceof io.proleap.cobol.asg.metamodel.procedure.gotostmt.GoToStatement g
                            && g.getGoToType() == io.proleap.cobol.asg.metamodel.procedure.gotostmt.GoToStatement.GoToType.SIMPLE) {
                        try {
                            out.put(pc.getParagraph(), procedureIndex(g.getSimple().getProcedureCall(), false));
                        } catch (HandlerException e) {
                            // The GO TO reports it.
                        }
                    }
                }
            }
        });
        return out;
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
                if (proc.getCalls().size() > 1) {
                    dispatcher = true;
                }
                for (Call call : proc.getCalls()) {
                    if (call.unwrap() instanceof ProcedureCall pc && pc.getParagraph() != null) {
                        performedParagraphs.add(pc.getParagraph());
                    }
                }
            } else if (tree instanceof CobolParser.GoToStatementContext || tree instanceof CobolParser.AlterStatementContext) {
                dispatcher = true;
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
        currentCtx = unit.statement().getCtx();
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
                        case UNSUPPORTED -> unresolved(unit, block, facts, Reason.UNSUPPORTED_CONSTRUCT, rule, e.getMessage(),
                                e.offerCandidates() ? candidatesFor(unit, handler) : List.of());
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
        dataDecision(ctx, operation, message, Reason.UNSUPPORTED_CONSTRUCT);
    }

    private void dataDecision(ParserRuleContext ctx, String operation, String message, Reason reason) {
        statements++;
        Decision.Plan none = id -> {
            throw new IllegalArgumentException("decision has no candidates");
        };
        sink.add(new Decision(reason, operation, message, location(ctx), in.programId(),
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
        Call u = call.unwrap();
        if (u instanceof io.proleap.cobol.asg.metamodel.call.IndexCall ic && indexNames.containsKey(ic.getIndex())) {
            return new Operand.Index(indexNames.get(ic.getIndex()));
        }
        if (u instanceof SpecialRegisterCall s) {
            return switch (s.getSpecialRegisterType()) {
                case RETURN_CODE -> new Operand.ReturnCode();
                case LENGTH_OF -> lengthOf(s);
                case WHEN_COMPILED -> {
                    String when = require("when_compiled", in.dialect().whenCompiled(), call.getCtx(), "WHEN-COMPILED");
                    // GnuCOBOL's form: DD/MM/YYhh.mm.ss
                    yield new Operand.AlphanumericLiteral(when.substring(8, 10) + "/" + when.substring(5, 7) + "/" + when.substring(2, 4)
                            + when.substring(11, 13) + "." + when.substring(14, 16) + "." + when.substring(17, 19));
                }
                default -> throw HandlerException.unsupported("special register " + s.getSpecialRegisterType() + " is not supported");
            };
        }
        if (call.getName() != null && call.getName().equalsIgnoreCase("NUMBER-OF-CALL-PARAMETERS")
                && u.getCallType() == Call.CallType.UNDEFINED_CALL) {
            if (in.unit().getProcedureDivision() == null || in.unit().getProcedureDivision().getUsingClause() == null) {
                throw HandlerException.unsupported("NUMBER-OF-CALL-PARAMETERS in a program without PROCEDURE DIVISION USING");
            }
            return new Operand.Register("callParameters");
        }
        if (u instanceof io.proleap.cobol.asg.metamodel.call.FunctionCall) {
            var f = firstOf(call.getCtx(), CobolParser.FunctionCallContext.class);
            if (f == null) {
                throw HandlerException.unsupported("function " + call.getName() + " cannot be read");
            }
            return functionOperand(f);
        }
        if (u instanceof DataDescriptionEntryCall || u.getCallType() == Call.CallType.UNDEFINED_CALL) {
            return dataOperand(call);
        }
        throw HandlerException.unsupported(call.getName() + " (" + call.getCallType()
                + ") is not supported: functions and special registers other than RETURN-CODE");
    }

    /** Float-valued intrinsics: their results are not bit-exact, so they stay decisions (saved for Jev). */
    private static final Set<String> FLOAT_FUNCTIONS = Set.of("SQRT", "LOG", "LOG10", "EXP", "EXP10", "SIN", "COS", "TAN", "ASIN", "ACOS",
            "ATAN", "RANDOM", "VARIANCE", "STANDARD-DEVIATION", "PRESENT-VALUE", "ANNUITY", "MEAN", "MEDIAN", "MIDRANGE", "RANGE",
            "PI", "E", "FACTORIAL");

    /** An intrinsic function, read from the parse tree (ProLeap keeps no arguments). */
    private Operand functionOperand(CobolParser.FunctionCallContext f) throws HandlerException {
        String fn = f.functionName().getText().toUpperCase(Locale.ROOT);
        if (FLOAT_FUNCTIONS.contains(fn)) {
            throw HandlerException.unsupported("FUNCTION " + fn + " is computed in floating point; its result is not bit-exact");
        }
        List<Operand> args = new ArrayList<>();
        var raw = f.argument();
        for (int i = 0; i < raw.size(); i++) {
            var a = raw.get(i);
            DataItem table = tableArgument(a);
            if (table != null) {
                // ProLeap's grammar splits "V(1)" into the arguments "V" and "(1)"; a table item
                // cannot be an argument without its subscripts, so the groups that follow are its subscripts.
                List<Operand.Subscript> subs = new ArrayList<>();
                while (subs.size() < table.dimensions().size() && i + 1 < raw.size() && parenthesized(raw.get(i + 1)) != null) {
                    subs.add(position(parenthesized(raw.get(++i))));
                }
                if (subs.size() != table.dimensions().size()) {
                    throw HandlerException.unsupported(table.cobolName() + " needs " + table.dimensions().size() + " subscript(s)");
                }
                for (Operand.Subscript sub : subs) {
                    if (sub.value() != null) {
                        requireRangeCheck(table.cobolName() + " subscript");
                    }
                }
                args.add(new Operand.Field(table, List.copyOf(subs), null));
                continue;
            }
            args.add(argument(a));
        }
        Operand result = switch (fn) {
            case "UPPER-CASE", "LOWER-CASE", "REVERSE" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("Functions", switch (fn) {
                    case "UPPER-CASE" -> "upperCase";
                    case "LOWER-CASE" -> "lowerCase";
                    default -> "reverse";
                }, textOf(args.getFirst(), fn)), false, "text");
            }
            case "TRIM" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("Functions", "trim", textOf(args.getFirst(), fn), integer(0)), false, "text");
            }
            case "LENGTH" -> {
                arity(fn, args, 1);
                yield switch (args.getFirst()) {
                    case Operand.Field fd when fd.refmod() == null && !(fd.item().isGroup() && fd.item().variableTable() != null) ->
                            new Operand.NumericLiteral(BigDecimal.valueOf(fd.item().size()), Integer.toString(fd.item().size()));
                    case Operand.AlphanumericLiteral a -> new Operand.NumericLiteral(BigDecimal.valueOf(a.value().length()),
                            Integer.toString(a.value().length()));
                    case Operand.Computed c when !c.numeric() -> new Operand.Computed(staticCall("BigDecimal", "valueOf",
                            call(c.value(), "length")), true, "integer");
                    default -> throw HandlerException.unsupported("FUNCTION LENGTH of " + args.getFirst().category() + " is not supported");
                };
            }
            case "MOD", "REM" -> {
                arity(fn, args, 2);
                boolean integral = args.stream().allMatch(ProgramTranslator::isIntegral);
                if (fn.equals("MOD") && !integral) {
                    throw HandlerException.unsupported("FUNCTION MOD needs integer arguments");
                }
                yield new Operand.Computed(staticCall("Functions", fn.equals("MOD") ? "mod" : "rem", numberOf(args.get(0), fn),
                        numberOf(args.get(1), fn)), true, integral ? "integer" : null);
            }
            case "INTEGER", "INTEGER-PART" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("Functions", fn.equals("INTEGER") ? "integer" : "integerPart",
                        numberOf(args.getFirst(), fn)), true, "integer");
            }
            case "ABS" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("Functions", "abs", numberOf(args.getFirst(), fn)), true, null);
            }
            case "MAX", "MIN", "SUM", "ORD-MAX", "ORD-MIN" -> {
                if (args.isEmpty()) {
                    throw HandlerException.unsupported("FUNCTION " + fn + " needs arguments");
                }
                NodeList<Expression> values = new NodeList<>();
                for (Operand a : args) {
                    values.add(numberOf(a, fn));
                }
                String method = switch (fn) {
                    case "MAX" -> "max";
                    case "MIN" -> "min";
                    case "SUM" -> "sum";
                    case "ORD-MAX" -> "ordMax";
                    default -> "ordMin";
                };
                Expression e = new com.github.javaparser.ast.expr.MethodCallExpr(name("Functions"), method, values);
                yield fn.startsWith("ORD") ? new Operand.Computed(staticCall("BigDecimal", "valueOf", e), true, "integer")
                        : new Operand.Computed(e, true, null);
            }
            case "NUMVAL", "NUMVAL-C" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("Functions", fn.equals("NUMVAL") ? "numval" : "numvalC", textOf(args.getFirst(), fn)),
                        true, null);
            }
            case "ORD" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("BigDecimal", "valueOf", staticCall("Functions", "ord", textOf(args.getFirst(), fn))),
                        true, "integer");
            }
            case "CHAR" -> {
                arity(fn, args, 1);
                yield new Operand.Computed(staticCall("Functions", "charOf", call(numberOf(args.getFirst(), fn), "intValue")), false, "text");
            }
            case "INTEGER-OF-DATE", "INTEGER-OF-DAY", "DATE-OF-INTEGER", "DAY-OF-INTEGER" -> {
                arity(fn, args, 1);
                String method = switch (fn) {
                    case "INTEGER-OF-DATE" -> "integerOfDate";
                    case "INTEGER-OF-DAY" -> "integerOfDay";
                    case "DATE-OF-INTEGER" -> "dateOfInteger";
                    default -> "dayOfInteger";
                };
                yield new Operand.Computed(staticCall("Functions", method, numberOf(args.getFirst(), fn)), true,
                        fn.equals("DATE-OF-INTEGER") ? "digits8" : fn.equals("DAY-OF-INTEGER") ? "digits7" : "integer");
            }
            case "CURRENT-DATE" -> {
                arity(fn, args, 0);
                yield new Operand.Computed(staticCall("Functions", "currentDate"), false, "text");
            }
            default -> throw HandlerException.unsupported("FUNCTION " + fn + " is not supported");
        };
        if (f.referenceModifier() != null) {
            if (!(result instanceof Operand.Computed c) || c.numeric()) {
                throw HandlerException.unsupported("reference modification of FUNCTION " + fn + " is not supported");
            }
            var rm = f.referenceModifier();
            Operand.Subscript start = position(rm.characterPosition().arithmeticExpression());
            Operand.Subscript length = rm.length() == null ? null : position(rm.length().arithmeticExpression());
            if (start.value() != null || length == null || length.value() != null) {
                throw HandlerException.unsupported("reference modification of a function result needs literal (start:length)");
            }
            result = new Operand.Computed(staticCall("Functions", "substring", c.value(), integer(start.offset()), integer(length.offset())),
                    false, "text");
        }
        return result;
    }

    /** A plain (unsubscripted) name of a table item as a function argument, or null. */
    private DataItem tableArgument(CobolParser.ArgumentContext a) {
        var id = a.identifier();
        if (id == null || id.qualifiedDataName() == null) {
            return null;
        }
        DataItem item = resolveData(id.qualifiedDataName());
        return item != null && !item.dimensions().isEmpty() ? item : null;
    }

    /** The expression inside an argument written {@code (expression)}, or null. */
    private static CobolParser.ArithmeticExpressionContext parenthesized(CobolParser.ArgumentContext a) {
        var e = a.arithmeticExpression();
        if (e == null || !e.plusMinus().isEmpty() || !e.multDivs().multDiv().isEmpty() || !e.multDivs().powers().power().isEmpty()
                || e.multDivs().powers().PLUSCHAR() != null || e.multDivs().powers().MINUSCHAR() != null) {
            return null;
        }
        return e.multDivs().powers().basis().arithmeticExpression();
    }

    private static void arity(String fn, List<Operand> args, int n) throws HandlerException {
        if (args.size() != n) {
            throw HandlerException.unsupported("FUNCTION " + fn + " with " + args.size() + " arguments is not supported");
        }
    }

    private static boolean isIntegral(Operand o) {
        return switch (o) {
            case Operand.NumericLiteral n -> n.isInteger();
            case Operand.Field f -> f.integer();
            case Operand.Computed c -> "integer".equals(c.display()) || (c.display() != null && c.display().startsWith("digits"));
            default -> false;
        };
    }

    private Expression textOf(Operand o, String fn) throws HandlerException {
        Expression e = alphanumericSide(o, new Operand.AlphanumericLiteral(""));
        if (e == null) {
            throw HandlerException.unsupported("FUNCTION " + fn + " of " + o.category() + " is not supported");
        }
        return e;
    }

    private Expression numberOf(Operand o, String fn) throws HandlerException {
        if (!o.isNumericValued()) {
            throw HandlerException.unsupported("FUNCTION " + fn + " of " + o.category() + " is not supported (numeric arguments)");
        }
        return numeric(o);
    }

    /** One function argument: a literal, a data reference, a nested function, or {@code -n}. */
    private Operand argument(CobolParser.ArgumentContext a) throws HandlerException {
        if (a.literal() != null) {
            return literal(a.literal());
        }
        if (a.identifier() != null) {
            var id = a.identifier();
            if (id.functionCall() != null) {
                return functionOperand(id.functionCall());
            }
            if (id.specialRegister() != null) {
                throw HandlerException.unsupported("special register as a function argument is not supported");
            }
            return dataOperand(id, null);
        }
        if (a.qualifiedDataName() != null && a.integerLiteral() == null) {
            return dataOperand(a.qualifiedDataName(), null);
        }
        if (a.arithmeticExpression() != null) {
            var e = a.arithmeticExpression();
            var md = e.multDivs();
            if (e.plusMinus().isEmpty() && md.multDiv().isEmpty() && md.powers().power().isEmpty()) {
                var basis = md.powers().basis();
                Operand inner = basis.literal() != null ? literal(basis.literal())
                        : basis.identifier() != null && basis.identifier().functionCall() != null ? functionOperand(basis.identifier().functionCall())
                        : basis.identifier() != null ? dataOperand(basis.identifier(), null) : null;
                if (inner != null && md.powers().MINUSCHAR() == null) {
                    return inner;
                }
                if (inner instanceof Operand.NumericLiteral n) {
                    return new Operand.NumericLiteral(n.value().negate(), "-" + n.text());
                }
            }
        }
        throw HandlerException.unsupported("function argument " + a.getText() + " is not supported");
    }

    /** LENGTH OF item: its size in bytes, known at translation time unless it varies with OCCURS DEPENDING ON. */
    private Operand lengthOf(SpecialRegisterCall s) throws HandlerException {
        Operand o = operand(s.getIdentifierCall());
        if (!(o instanceof Operand.Field f) || f.refmod() != null || (f.item().isGroup() && f.item().variableTable() != null)) {
            throw HandlerException.unsupported("LENGTH OF " + s.getCtx().getText() + " is not supported");
        }
        int size = f.item().size();
        return new Operand.NumericLiteral(BigDecimal.valueOf(size), Integer.toString(size));
    }

    /**
     * A data reference, read from the parse tree: ProLeap resolves a subscripted name to the
     * first item with that name whatever its qualifiers, and drops reference modification.
     */
    private Operand dataOperand(Call call) throws HandlerException {
        return dataOperand(call.getCtx(), call);
    }

    private Operand dataOperand(ParserRuleContext ctx, Call call) throws HandlerException {
        var table = firstOf(ctx, CobolParser.TableCallContext.class);
        var q = table != null ? table.qualifiedDataName() : firstOf(ctx, CobolParser.QualifiedDataNameContext.class);
        DataItem item;
        if (q == null) {
            item = call != null && call.unwrap() instanceof DataDescriptionEntryCall d ? items.get(d.getDataDescriptionEntry()) : null;
        } else {
            Operand named = resolveName(q);
            if (named instanceof Operand.Index idx) {
                return idx;
            }
            item = named == null ? null : ((Operand.Field) named).item();
        }
        if (item == null) {
            throw HandlerException.unsupported((call != null ? call.getName() : ctx.getText()) + " is not a data item");
        }
        if (item.kind() == DataItem.Kind.UNSUPPORTED) {
            throw HandlerException.unsupported(item.cobolName() + ": " + item.unsupportedReason());
        }
        if (item.kind() == DataItem.Kind.CONSTANT) {
            if (table != null) {
                throw HandlerException.unsupported(item.cobolName() + " is a constant");
            }
            return item.constant();
        }
        List<Operand.Subscript> subscripts = new ArrayList<>();
        Operand.RefMod refmod = null;
        if (table != null) {
            for (var sc : table.subscript()) {
                subscripts.add(subscript(sc));
            }
            if (table.referenceModifier() != null) {
                var rm = table.referenceModifier();
                refmod = new Operand.RefMod(position(rm.characterPosition().arithmeticExpression()),
                        rm.length() == null ? null : position(rm.length().arithmeticExpression()));
            }
        }
        List<DataItem> dims = item.dimensions();
        if (subscripts.size() != dims.size()) {
            throw HandlerException.unsupported(item.cobolName() + " needs " + dims.size() + " subscript(s), not " + subscripts.size());
        }
        if (item.isCondition() && !subscripts.isEmpty()) {
            throw HandlerException.unsupported("subscripted condition-names are not supported");
        }
        for (int i = 0; i < dims.size(); i++) {
            DataItem t = dims.get(i);
            Operand.Subscript sub = subscripts.get(i);
            if (sub.value() == null && (sub.offset() < 1 || sub.offset() > t.occurs())) {
                throw HandlerException.unsupported("subscript " + sub.offset() + " of " + item.cobolName()
                        + " is outside 1.." + t.occurs() + " (GnuCOBOL rejects it)");
            }
            if (sub.value() != null || t.dependingOn() != null) {
                requireRangeCheck(item.cobolName() + " subscript");
            }
        }
        Operand.Field f = new Operand.Field(item, List.copyOf(subscripts), refmod);
        if (refmod != null) {
            checkRefMod(f);
        }
        if ((refmod != null || item.isGroup()) && item.elementaryItems().stream().anyMatch(DataItem::encodingSensitive)) {
            requireAsciiBytes(currentCtx != null ? currentCtx : ctx, "the bytes of " + item.cobolName());
        }
        return f;
    }

    /** Statically valid reference modification; positions that are not literals need the run-time check. */
    private void checkRefMod(Operand.Field f) throws HandlerException {
        DataItem item = f.item();
        if (item.isNumeric() && item.numeric().scale() > 0) {
            throw HandlerException.unsupported("reference modification of non-integer " + item.cobolName() + " is invalid COBOL");
        }
        Operand.Subscript start = f.refmod().start();
        Operand.Subscript length = f.refmod().length();
        boolean variable = item.isGroup() && item.variableTable() != null;
        int size = item.size() + (item.isNumeric() && item.signSeparate() ? 0 : 0);
        if (start.value() == null && (start.offset() < 1 || (!variable && start.offset() > size))) {
            throw HandlerException.unsupported("offset " + start.offset() + " of " + item.cobolName() + " is out of bounds (GnuCOBOL rejects it)");
        }
        if (length != null && length.value() == null && (length.offset() < 1
                || (!variable && start.value() == null && start.offset() + length.offset() - 1 > size))) {
            throw HandlerException.unsupported("length " + length.offset() + " of " + item.cobolName() + " is out of bounds (GnuCOBOL rejects it)");
        }
        if (start.value() != null || (length != null && length.value() != null) || variable) {
            requireRangeCheck(item.cobolName() + " reference modification");
        }
    }

    /**
     * A reference whose range cannot be proven at translation time: with out_of_range "fail" the
     * generated check stops the run unit as GnuCOBOL's run-time checks do; with "decision" the
     * statement is refused.
     */
    private void requireRangeCheck(String what) throws HandlerException {
        String policy = require("out_of_range", in.dialect().outOfRange(), currentCtx, what + " that is not a literal in range");
        if (policy.equals("decision")) {
            throw HandlerException.unsupported(what + " cannot be proven in range (Dialect.out_of_range is 'decision')");
        }
    }

    private Operand.Subscript subscript(CobolParser.SubscriptContext sc) throws HandlerException {
        if (sc.ALL() != null) {
            throw HandlerException.unsupported("subscript ALL is not supported here");
        }
        int offset = sc.integerLiteral() == null ? 0 : Integer.parseInt(sc.integerLiteral().getText().replace("+", ""));
        if (sc.qualifiedDataName() != null) {
            return new Operand.Subscript(integerValue(resolveName(sc.qualifiedDataName()), sc.getText()), offset);
        }
        if (sc.indexName() != null) {
            var idx = indexesByName.get(sc.indexName().getText().toUpperCase(Locale.ROOT));
            if (idx == null) {
                throw HandlerException.unsupported("index " + sc.indexName().getText() + " is not declared");
            }
            return new Operand.Subscript(new Operand.Index(idx), offset);
        }
        if (sc.integerLiteral() != null) {
            return new Operand.Subscript(null, offset);
        }
        return position(sc.arithmeticExpression());
    }

    /** {@code n}, {@code name} or {@code name +/- n}: the subscript and reference-modification forms supported. */
    private Operand.Subscript position(CobolParser.ArithmeticExpressionContext e) throws HandlerException {
        Operand.Subscript base = null;
        var first = e.multDivs();
        if (first.multDiv().isEmpty() && first.powers().power().isEmpty() && first.powers().PLUSCHAR() == null
                && first.powers().MINUSCHAR() == null) {
            var basis = first.powers().basis();
            if (basis.literal() != null && basis.literal().numericLiteral() != null && basis.literal().numericLiteral().integerLiteral() != null) {
                base = new Operand.Subscript(null, Integer.parseInt(basis.getText()));
            } else if (basis.identifier() != null && basis.identifier().qualifiedDataName() != null) {
                base = new Operand.Subscript(integerValue(resolveName(basis.identifier().qualifiedDataName()), basis.getText()), 0);
            }
        }
        if (base == null) {
            throw HandlerException.unsupported("position " + e.getText() + " is not supported (only n, name and name +/- n)");
        }
        int offset = base.offset();
        for (var pm : e.plusMinus()) {
            var md = pm.multDivs();
            var basis = md.powers().basis();
            if (!md.multDiv().isEmpty() || !md.powers().power().isEmpty() || md.powers().PLUSCHAR() != null || md.powers().MINUSCHAR() != null
                    || basis.literal() == null || basis.literal().numericLiteral() == null || basis.literal().numericLiteral().integerLiteral() == null) {
                throw HandlerException.unsupported("position " + e.getText() + " is not supported (only n, name and name +/- n)");
            }
            int n = Integer.parseInt(basis.getText());
            offset += pm.PLUSCHAR() != null ? n : -n;
        }
        return new Operand.Subscript(base.value(), offset);
    }

    private static Operand integerValue(Operand o, String text) throws HandlerException {
        if (o instanceof Operand.Index || (o instanceof Operand.Field f && f.integer() && f.item().dimensions().isEmpty())) {
            return o;
        }
        throw HandlerException.unsupported(text + " must be an integer item or index outside a table");
    }

    /** A data item or index name from {@code A OF B ...}, or null when it does not resolve. */
    private Operand resolveName(CobolParser.QualifiedDataNameContext ctx) throws HandlerException {
        var q = ctx.qualifiedDataNameFormat1();
        if (q == null || q.inFile() != null || q.qualifiedInData().stream().anyMatch(x -> x.inData() == null)) {
            throw HandlerException.unsupported(ctx.getText() + " cannot be resolved");
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
                    throw HandlerException.unsupported(ctx.getText() + " is ambiguous");
                }
                found = item;
            }
        }
        if (found == null && qualifiers.isEmpty() && indexesByName.containsKey(name.toUpperCase(Locale.ROOT))) {
            return new Operand.Index(indexesByName.get(name.toUpperCase(Locale.ROOT)));
        }
        return found == null ? null : new Operand.Field(found);
    }

    /** {@link #resolveName} for a data item, null when it is not one. */
    private DataItem resolveData(CobolParser.QualifiedDataNameContext ctx) {
        try {
            return resolveName(ctx) instanceof Operand.Field f ? f.item() : null;
        } catch (HandlerException e) {
            return null;
        }
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

    @Override
    public Expression field(Operand o) throws HandlerException {
        return switch (o) {
            case Operand.Field f -> {
                Expression base = name(f.item().javaName());
                if (f.item().hasAccessor()) {
                    NodeList<Expression> args = new NodeList<>();
                    for (Operand.Subscript sub : f.subscripts()) {
                        args.add(position(sub));
                    }
                    base = new com.github.javaparser.ast.expr.MethodCallExpr(null, f.item().javaName(), args);
                }
                if (f.refmod() != null) {
                    Expression start = position(f.refmod().start());
                    base = f.refmod().length() == null
                            ? call(base, "ref", start, str(f.item().cobolName()))
                            : call(base, "ref", start, position(f.refmod().length()), str(f.item().cobolName()));
                }
                yield base;
            }
            case Operand.ReturnCode r -> name("returnCode");
            default -> throw HandlerException.unsupported(o.category() + " is not a data item");
        };
    }

    @Override
    public Expression receiving(Operand o) throws HandlerException {
        if (o instanceof Operand.Field f && f.refmod() == null && f.item().isGroup() && f.item().dimensions().isEmpty()) {
            DataItem table = f.item().variableTable();
            if (table != null && isWithin(table.dependingOn(), f.item())) {
                return create("AlphanumericField", name(storageNames.get(storageRoot(f.item().record()))),
                        integer(f.item().offset()), integer(table.offset() - f.item().offset() + table.totalSize()));
            }
        }
        return field(o);
    }

    /** A subscript or position as a Java int expression. */
    private Expression position(Operand.Subscript sub) throws HandlerException {
        if (sub.value() == null) {
            return integer(sub.offset());
        }
        Expression v = switch (sub.value()) {
            case Operand.Index i -> name(i.index().javaName());
            case Operand.Field f -> call(field(f), "intValue");
            default -> throw HandlerException.unsupported(sub.value().category() + " as a subscript");
        };
        if (sub.offset() == 0) {
            return v;
        }
        return new BinaryExpr(v, integer(Math.abs(sub.offset())), sub.offset() > 0 ? BinaryExpr.Operator.PLUS : BinaryExpr.Operator.MINUS);
    }

    @Override
    public Expression numeric(Operand o) throws HandlerException {
        return switch (o) {
            case Operand.Field f when f.numeric() -> call(field(f), "get");
            case Operand.ReturnCode r -> call(name("returnCode"), "get");
            case Operand.Index i -> staticCall("BigDecimal", "valueOf", name(i.index().javaName()));
            case Operand.Register r -> staticCall("BigDecimal", "valueOf", name(r.javaName()));
            case Operand.Computed c when c.numeric() -> c.value().clone();
            case Operand.NumericLiteral n -> decimal(n.value().toPlainString());
            case Operand.Figurative f when f.isZero() -> decimal("0");
            case Operand.Arithmetic a -> arithmetic(a.stmt());
            default -> throw HandlerException.unsupported(o.category() + " used where a numeric value is required");
        };
    }

    private Expression arithmetic(ArithmeticValueStmt a) throws HandlerException {
        Expression e = multDivs(a.getMultDivs());
        for (PlusMinus pm : a.getPlusMinus()) {
            boolean plus = pm.getPlusMinusType() == PlusMinus.PlusMinusType.PLUS;
            e = gnuArithmetic ? staticCall("Arithmetic", plus ? "plus" : "minus", e, multDivs(pm.getMultDivs()))
                    : call(e, plus ? "add" : "subtract", multDivs(pm.getMultDivs()));
        }
        return e;
    }

    private Expression multDivs(MultDivs md) throws HandlerException {
        Expression e = powers(md.getPowers());
        for (MultDiv m : md.getMultDivs()) {
            if (m.getMultDivType() == MultDiv.MultDivType.DIV) {
                if (!gnuArithmetic) {
                    throw HandlerException.unsupported("division in an arithmetic expression is not supported "
                            + "(intermediate precision is implementation-defined); use DIVIDE");
                }
                e = staticCall("Arithmetic", "over", e, powers(m.getPowers()));
            } else {
                e = gnuArithmetic ? staticCall("Arithmetic", "times", e, powers(m.getPowers())) : call(e, "multiply", powers(m.getPowers()));
            }
        }
        return e;
    }

    /**
     * {@code [-] basis [** basis]...}: the sign applies to the first basis; GnuCOBOL evaluates
     * {@code a ** b ** c} as {@code a ** (b ** c)}. Exponents must be non-negative integer literals
     * or unsigned integer items: GnuCOBOL 3.2 computes other powers in floating point, and a
     * negative exponent changes the result of later evaluations of the same expression.
     */
    private Expression powers(Powers p) throws HandlerException {
        if (!p.getPowers().isEmpty() && !gnuArithmetic) {
            throw HandlerException.unsupported("exponentiation (**) is not supported");
        }
        Expression e = basis(p.getBasis());
        if (p.getPowersType() == Powers.PowersType.MINUS) {
            e = gnuArithmetic ? staticCall("Arithmetic", "negate", e) : call(e, "negate");
        }
        List<Expression> exponents = new ArrayList<>();
        for (var power : p.getPowers()) {
            ValueStmt exp = power.getBasis().getBasisValueStmt();
            Operand o = exp instanceof ArithmeticValueStmt av && trivial(av) == null ? null : operand(exp);
            boolean ok = o instanceof Operand.NumericLiteral n && n.isInteger() && n.value().signum() >= 0
                    || o instanceof Operand.Field f && f.integer() && !f.item().numeric().signed();
            if (!ok) {
                throw HandlerException.unsupported("exponent " + power.getCtx().getText()
                        + " is not a non-negative integer literal or unsigned integer item");
            }
            exponents.add(numeric(o));
        }
        if (exponents.isEmpty()) {
            return e;
        }
        Expression exponent = exponents.getLast();
        for (int i = exponents.size() - 2; i >= 0; i--) {
            exponent = staticCall("Arithmetic", "power", exponents.get(i), exponent);
        }
        return staticCall("Arithmetic", "power", e, exponent);
    }

    @Override
    public Expression gnuArithmetic(Operand o) throws HandlerException {
        boolean saved = gnuArithmetic;
        gnuArithmetic = true;
        try {
            return numeric(o);
        } finally {
            gnuArithmetic = saved;
        }
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

    /**
     * NUMERIC / ALPHABETIC[-UPPER|-LOWER] / user CLASS, read from the bytes: character by
     * character for alphanumeric data and groups, digit and sign positions for numeric items.
     */
    private Expression classCondition(ClassCondition cc) throws HandlerException {
        Operand o = operand(cc.getIdentifierCall());
        if (!(o instanceof Operand.Field f) || !(f.alphanumeric() || f.numeric())) {
            throw HandlerException.unsupported("class condition on " + o.category() + " " + cc.getIdentifierCall().getName()
                    + " is not supported");
        }
        Expression test;
        if (f.numeric()) {
            if (cc.getClassConditionType() != ClassCondition.ClassConditionType.NUMERIC) {
                throw HandlerException.unsupported(cc.getClassConditionType() + " test of numeric item " + f.item().cobolName()
                        + " is invalid COBOL");
            }
            test = call(field(o), "isNumeric");
        } else {
            Expression value = call(field(o), "get");
            test = switch (cc.getClassConditionType()) {
                case NUMERIC -> staticCall("Compare", "isNumeric", value);
                case ALPHABETIC -> staticCall("Compare", "isAlphabetic", value);
                case ALPHABETIC_UPPER -> staticCall("Compare", "isAlphabeticUpper", value);
                case ALPHABETIC_LOWER -> staticCall("Compare", "isAlphabeticLower", value);
                case CLASS_NAME -> staticCall("Compare", "inClass", value,
                        str(userClass(cc.getClassCall().getName(), cc.getCtx())));
                default -> throw HandlerException.unsupported("class condition " + cc.getClassConditionType() + " is not supported");
            };
        }
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
                || other instanceof Operand.Field f && f.alphanumeric() || other instanceof Operand.Computed c && !c.numeric();
        return switch (o) {
            case Operand.Field f when f.alphanumeric() -> call(field(f), "get");
            case Operand.Computed c when !c.numeric() -> c.value().clone();
            case Operand.AlphanumericLiteral a -> str(a.value());
            case Operand.Field f when otherAlpha && f.integer() && !f.item().numeric().signed() ->
                    call(field(f), "digits");
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
        if (dispatcher && kind == Jump.EXIT_PARAGRAPH) {
            if (currentParagraph == null) {
                throw HandlerException.unsupported("EXIT PARAGRAPH outside a paragraph is not supported");
            }
            return new ReturnStmt(integer(-1));
        }
        if (dispatcher && kind == Jump.EXIT_SECTION) {
            if (currentSection == null) {
                throw HandlerException.unsupported("EXIT SECTION outside a section");
            }
            return new ReturnStmt(integer(sectionEnds.get(currentSection)));
        }
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
    public DataItem dataItem(Call call) throws HandlerException {
        var q = firstOf(call.getCtx(), CobolParser.QualifiedDataNameContext.class);
        DataItem item = q != null ? resolveData(q)
                : call.unwrap() instanceof DataDescriptionEntryCall d ? items.get(d.getDataDescriptionEntry()) : null;
        if (item == null || item.kind() == DataItem.Kind.UNSUPPORTED) {
            throw HandlerException.unsupported(call.getName() + " is not a supported WORKING-STORAGE item");
        }
        return item;
    }

    @Override
    public Optional<List<Integer>> programParameters(String programId) {
        return in.programParameters().apply(programId.toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean overlaps(Operand a, Operand b) {
        if (!(a instanceof Operand.Field fa) || !(b instanceof Operand.Field fb)) {
            return false;
        }
        if (storageRoot(fa.item().record()) != storageRoot(fb.item().record())) {
            return false;
        }
        if (!fa.subscripts().isEmpty() || !fb.subscripts().isEmpty() || fa.refmod() != null || fb.refmod() != null
                || fa.item().hasAccessor() || fb.item().hasAccessor()) {
            return true;
        }
        int a0 = fa.item().offset();
        int b0 = fb.item().offset();
        return a0 < b0 + fb.item().totalSize() && b0 < a0 + fa.item().totalSize();
    }

    @Override
    public void requireBytes(DataItem item, String usage) throws HandlerException {
        if (item.elementaryItems().stream().anyMatch(DataItem::encodingSensitive)) {
            requireAsciiBytes(currentCtx, usage);
        }
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
    public FileRef file(Call fileCall) throws HandlerException {
        FileInfo f = files.get(fileCall.getName().toUpperCase(Locale.ROOT));
        if (f == null || f.javaName == null) {
            throw HandlerException.unsupported("file " + fileCall.getName() + " is not a supported file");
        }
        return new FileRef(f.javaName, f.lineSequential, f.sort, List.copyOf(f.records));
    }

    @Override
    public FileRef fileOfRecord(DataItem record) throws HandlerException {
        for (FileInfo f : files.values()) {
            if (f.records.contains(record) && f.javaName != null) {
                return new FileRef(f.javaName, f.lineSequential, f.sort, List.copyOf(f.records));
            }
        }
        throw HandlerException.unsupported(record.cobolName() + " is not a record of a supported file");
    }

    @Override
    public Expression sortKey(DataItem key, ParserRuleContext where) throws HandlerException {
        if (!key.fileRecord() || key.hasAccessor() || key.kind() == DataItem.Kind.UNSUPPORTED || key.isCondition()) {
            throw HandlerException.unsupported("KEY " + key.cobolName() + " must be an item of the sort record outside any table");
        }
        boolean numeric = key.isNumeric();
        if (!numeric) {
            requireCollatingSequence(RelationalOperatorType.GREATER, where);
        }
        Expression view = key.isGroup() ? create("AlphanumericField", name("s"), integer(key.offset()), integer(key.size()))
                : viewConstructor(key, name("s"), integer(key.offset()));
        return new com.github.javaparser.ast.expr.LambdaExpr(new Parameter(new com.github.javaparser.ast.type.UnknownType(), "s"),
                call(view, "get"));
    }

    @Override
    public boolean dispatcher() {
        return dispatcher;
    }

    @Override
    public Statement performRange(Call from, Call thru) throws HandlerException {
        int[] r = range(from, thru);
        for (int[] other : performRanges) {
            boolean partial = (other[0] < r[0] && r[0] <= other[1] && other[1] < r[1]) || (r[0] < other[0] && other[0] <= r[1] && r[1] < other[1]);
            if (partial) {
                throw HandlerException.unsupported("PERFORM range overlaps another PERFORM range (procedures " + r[0] + ".." + r[1]
                        + " and " + other[0] + ".." + other[1] + ")");
            }
        }
        if (currentNode >= r[0] && currentNode <= r[1]) {
            throw HandlerException.unsupported("recursive PERFORM: the range contains this PERFORM");
        }
        return stmt(thisCall("perform", integer(r[0]), integer(r[1])));
    }

    @Override
    public Statement goTo(Call target) throws HandlerException {
        if (!dispatcher) {
            throw HandlerException.unsupported("GO TO outside the dispatcher form");
        }
        int t = procedureIndex(target, false);
        checkGoTo(t);
        if (currentParagraph != null && alterable.containsKey(currentParagraph)) {
            return new ReturnStmt(name(alterable.get(currentParagraph)));
        }
        return new ReturnStmt(integer(t));
    }

    @Override
    public int procedureNumber(Call target) throws HandlerException {
        int t = procedureIndex(target, false);
        checkGoTo(t);
        return t;
    }

    @Override
    public String alteredField(Call paragraph) throws HandlerException {
        if (paragraph.unwrap() instanceof ProcedureCall pc && alterable.containsKey(pc.getParagraph())) {
            return alterable.get(pc.getParagraph());
        }
        throw HandlerException.unsupported("ALTER " + paragraph.getName() + ": the paragraph must hold only a GO TO");
    }

    /** A GO TO from inside a PERFORMed range to outside it never returns to the PERFORM. */
    private void checkGoTo(int target) throws HandlerException {
        for (int[] r : performRanges) {
            if (currentNode >= r[0] && currentNode <= r[1] && (target < r[0] || target > r[1])) {
                throw HandlerException.unsupported("GO TO out of a PERFORMed range (procedures " + r[0] + ".." + r[1]
                        + "): the PERFORM would not return");
            }
        }
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
