package transpiler.cobol2java.translate;

import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.procedure.Statement;
import transpiler.cobol2java.decision.SourceLocation;

/**
 * One unit of translation: a COBOL statement, or for MOVE one receiver of the statement
 * ({@code MOVE A TO B C} is translated as two units so each receiver is mapped independently).
 */
public record OpUnit(String operation, Statement statement, Call receiver, SourceLocation location) {}
