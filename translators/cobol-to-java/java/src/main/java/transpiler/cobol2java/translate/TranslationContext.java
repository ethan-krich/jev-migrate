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

    /** A Java boolean expression for a COBOL condition. */
    Expression condition(ConditionValueStmt condition) throws HandlerException;

    /** A Java boolean expression for {@code left op right} under COBOL comparison rules. */
    Expression compare(Operand left, Operand right, RelationalOperatorType op, ParserRuleContext where) throws HandlerException;

    /** A Java boolean expression testing a level-88 condition-name. */
    Expression conditionName(DataItem condition, ParserRuleContext where) throws HandlerException;

    /** The Java expression naming a data item's field (or RETURN-CODE). */
    Expression field(Operand operand) throws HandlerException;

    /**
     * The Java expression for a receiving data item. Like {@link #field} except that a group
     * whose OCCURS DEPENDING ON object lies inside it receives at its maximum length.
     */
    Expression receiving(Operand operand) throws HandlerException;

    /** Translates nested statements (IF branches, inline PERFORM, SIZE ERROR phrases). */
    BlockStmt nested(Scope scope);

    /** The method implementing a paragraph or section named by PERFORM. */
    String procedureMethod(Call call) throws HandlerException;

    /** A method-unique local variable name. */
    String fresh(String base);

    /** The fully qualified class translated from the program with this PROGRAM-ID, if in the project. */
    Optional<String> programClass(String programId);

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
