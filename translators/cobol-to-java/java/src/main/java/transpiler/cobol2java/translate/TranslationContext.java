package transpiler.cobol2java.translate;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import io.proleap.cobol.asg.metamodel.Scope;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.valuestmt.ConditionValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.ValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.relation.RelationalOperator.RelationalOperatorType;
import java.util.List;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import transpiler.cobol2java.session.Dialect;

/** What handlers may ask of the program being translated. */
public interface TranslationContext {
    Operand operand(ValueStmt value) throws HandlerException;

    Operand operand(Call call) throws HandlerException;

    /** A BigDecimal-valued expression for a numeric operand. */
    Expression numeric(Operand operand) throws HandlerException;

    /** {@link #numeric} with GnuCOBOL 3.2's division (38 extra digits, truncated) and integer powers. */
    Expression gnuArithmetic(Operand operand) throws HandlerException;

    /** A Java boolean expression for a COBOL condition. */
    Expression condition(ConditionValueStmt condition) throws HandlerException;

    /** A Java boolean expression for {@code left op right} under COBOL comparison rules. */
    Expression compare(Operand left, Operand right, RelationalOperatorType op, ParserRuleContext where) throws HandlerException;

    /** A Java boolean expression testing a level-88 condition-name (with its subscripts, if any). */
    Expression conditionName(Operand.Field condition, ParserRuleContext where) throws HandlerException;

    /** The Java expression naming a data item's field (or RETURN-CODE). */
    Expression field(Operand operand) throws HandlerException;

    /**
     * The Java expression for a receiving data item. Like {@link #field} except that a group
     * whose OCCURS DEPENDING ON object lies inside it receives at its maximum length.
     */
    Expression receiving(Operand operand) throws HandlerException;

    /** Translates nested statements (IF branches, inline PERFORM, SIZE ERROR phrases). */
    BlockStmt nested(Scope scope);

    /** Whether the program uses the procedure dispatcher (GO TO, PERFORM THRU, ALTER). */
    boolean dispatcher();

    /** {@code PERFORM from [THRU thru]} in the dispatcher form; overlapping and recursive ranges are refused. */
    Statement performRange(Call from, Call thru) throws HandlerException;

    /** {@code GO TO target} in the dispatcher form (to the ALTER target in an altered paragraph). */
    Statement goTo(Call target) throws HandlerException;

    /** The dispatcher's number for a procedure that GO TO may go to. */
    int procedureNumber(Call target) throws HandlerException;

    /** The field holding the GO TO target of a paragraph named by ALTER. */
    String alteredField(Call paragraph) throws HandlerException;

    enum Organization { SEQUENTIAL, LINE_SEQUENTIAL, RELATIVE, INDEXED, SORT }

    enum Access { SEQUENTIAL, RANDOM, DYNAMIC }

    /** A RECORD KEY or ALTERNATE RECORD KEY of an INDEXED file. */
    record FileKey(DataItem item, boolean duplicates) {}

    /**
     * A file of the program: its Java field, organisation, access mode and records; the keys of
     * an INDEXED file (record key first) and the RELATIVE KEY item of a RELATIVE file (or null).
     */
    record FileRef(String javaName, Organization organization, Access access, java.util.List<DataItem> records,
            java.util.List<FileKey> keys, DataItem relativeKey) {
        public boolean lineSequential() {
            return organization == Organization.LINE_SEQUENTIAL;
        }

        public boolean sort() {
            return organization == Organization.SORT;
        }

        /** RELATIVE or INDEXED: the statements take INVALID KEY and the runtime class differs. */
        public boolean keyed() {
            return organization == Organization.RELATIVE || organization == Organization.INDEXED;
        }
    }

    /** A SORT / MERGE key: a lambda reading it from a record's bytes ({@code s -> view(s).get()}). */
    Expression sortKey(DataItem key, ParserRuleContext where) throws HandlerException;

    /**
     * A SORT / MERGE key compared in {@code sequence} ({@code ascii} or {@code ebcdic}, from a
     * COLLATING SEQUENCE phrase) instead of the program's collating sequence.
     */
    Expression sortKey(DataItem key, ParserRuleContext where, String sequence) throws HandlerException;

    /**
     * The collating sequence an ALPHABET name of SPECIAL-NAMES stands for: {@code ascii}
     * (NATIVE, STANDARD-1, ASCII) or {@code ebcdic}; empty for an alphabet of literals or an
     * unknown name.
     */
    java.util.Optional<String> alphabet(String name);

    /** The switch number (0 .. 36) of a SPECIAL-NAMES mnemonic name for SWITCH-n. */
    java.util.OptionalInt switchNumberOf(String mnemonic);

    /** The test of a switch's ON / OFF STATUS condition-name, empty for another name. */
    java.util.Optional<Expression> switchCondition(String name);

    /** The file a statement names (OPEN, CLOSE, READ). */
    FileRef file(Call fileCall) throws HandlerException;

    /** The file whose record a WRITE / REWRITE names. */
    FileRef fileOfRecord(DataItem record) throws HandlerException;

    /** The method implementing a paragraph or section named by PERFORM. */
    String procedureMethod(Call call) throws HandlerException;

    /** A method-unique local variable name. */
    String fresh(String base);

    /** The fully qualified class translated from the program with this PROGRAM-ID, if in the project. */
    Optional<String> programClass(String programId);

    /** The ENTRY number a CALL name denotes in its program (0: a PROGRAM-ID). */
    int entryIndex(String name);

    /** The PROGRAM-ID being translated, as written. */
    String programId();

    /** The simple name of the class being generated. */
    String className();

    /**
     * The static method resolving a dynamic CALL's program name among the project's programs
     * ({@code CobolProgram name(String)}, null for none); generated on first use.
     */
    String programResolver();

    /** The static method doing CANCEL of a program name ({@code void name(String)}); generated on first use. */
    String cancelResolver();

    /** The sizes of the PROCEDURE DIVISION USING items of a program in the project, if known. */
    Optional<List<Integer>> programParameters(String programId);

    /** The project's dialect settings. */
    Dialect dialect();

    /**
     * The value of an optional dialect setting. When it is unset this records the program's
     * {@code dialect:<field>} decision (once per program) and throws a {@link HandlerException.Kind#DIALECT}.
     *
     * @param usage what needs the setting, for the decision message ("HIGH-VALUES")
     */
    <T> T require(String field, Optional<T> value, ParserRuleContext where, String usage) throws HandlerException;

    /** The data item a call names, without subscripts (SEARCH tables, INITIALIZE expansion). */
    DataItem dataItem(Call call) throws HandlerException;

    /** Whether two operands may share bytes (conservatively true for subscripted or modified references). */
    boolean overlaps(Operand a, Operand b);

    /** Requires Dialect.encoding = ascii when {@code item}'s bytes include signed zoned items. */
    void requireBytes(DataItem item, String usage) throws HandlerException;

    /** Whether SPECIAL-NAMES declares DECIMAL-POINT IS COMMA. */
    boolean decimalPointComma();

    /** The device a DISPLAY UPON / ACCEPT FROM name refers to (CONSOLE, SYSERR, ...), resolving mnemonic names. */
    String device(String name);

    /** The characters of a user-defined CLASS from SPECIAL-NAMES. */
    String userClass(String name, ParserRuleContext where) throws HandlerException;

    /** An inline PERFORM being translated: EXIT PERFORM [CYCLE] inside it breaks to these labels. */
    final class PerformFrame {
        final String exitLabel;
        final String cycleLabel;
        boolean exitUsed;
        boolean cycleUsed;

        PerformFrame(String exitLabel, String cycleLabel) {
            this.exitLabel = exitLabel;
            this.cycleLabel = cycleLabel;
        }
    }

    /** Translates an inline PERFORM body with EXIT PERFORM [CYCLE] bound to {@code frame}. */
    BlockStmt nestedPerform(Scope scope, PerformFrame frame);

    /**
     * The jump for EXIT PARAGRAPH, EXIT SECTION, EXIT PERFORM, EXIT PERFORM CYCLE or NEXT SENTENCE
     * at the current position, or an exception when it cannot be expressed without the
     * procedure dispatcher.
     */
    Statement jump(Jump kind) throws HandlerException;

    enum Jump { EXIT_PARAGRAPH, EXIT_SECTION, EXIT_PERFORM, EXIT_PERFORM_CYCLE, NEXT_SENTENCE }
}
