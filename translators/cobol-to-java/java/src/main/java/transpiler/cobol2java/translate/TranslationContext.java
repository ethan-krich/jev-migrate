package transpiler.cobol2java.translate;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import io.proleap.cobol.asg.metamodel.Scope;
import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.valuestmt.ConditionValueStmt;
import io.proleap.cobol.asg.metamodel.valuestmt.ValueStmt;
import java.util.Optional;

/** What handlers may ask of the program being translated. */
public interface TranslationContext {
    Operand operand(ValueStmt value) throws HandlerException;

    Operand operand(Call call) throws HandlerException;

    /** A BigDecimal-valued expression for a numeric operand. */
    Expression numeric(Operand operand) throws HandlerException;

    /** A Java boolean expression for a COBOL condition. */
    Expression condition(ConditionValueStmt condition) throws HandlerException;

    /** The Java expression naming a data item's field (or RETURN-CODE). */
    Expression field(Operand operand) throws HandlerException;

    /** Translates nested statements (IF branches, inline PERFORM, SIZE ERROR phrases). */
    BlockStmt nested(Scope scope);

    /** The method implementing a paragraph or section named by PERFORM. */
    String procedureMethod(Call call) throws HandlerException;

    /** A method-unique local variable name. */
    String fresh(String base);

    /** The fully qualified class translated from the program with this PROGRAM-ID, if in the project. */
    Optional<String> programClass(String programId);
}
