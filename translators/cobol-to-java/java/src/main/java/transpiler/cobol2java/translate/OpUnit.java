package transpiler.cobol2java.translate;

import io.proleap.cobol.asg.metamodel.call.Call;
import io.proleap.cobol.asg.metamodel.procedure.Statement;
import transpiler.cobol2java.decision.SourceLocation;

/**
 * One unit of translation: a COBOL statement, or for MOVE one receiver of the statement
 * ({@code MOVE A TO B C} is translated as two units so each receiver is mapped independently).
 * MOVE / ADD / SUBTRACT CORRESPONDING become one unit per pair of corresponding items.
 */
public record OpUnit(String operation, Statement statement, Call receiver, SourceLocation location,
        Corresponding corresponding) {

    /**
     * A pair of corresponding elementary items, or (with null items) why the statement's
     * correspondence could not be worked out.
     */
    public record Corresponding(DataItem source, DataItem target, boolean rounded, String problem) {
        static Corresponding unsupported(String problem) {
            return new Corresponding(null, null, false, problem);
        }
    }

    public OpUnit(String operation, Statement statement, Call receiver, SourceLocation location) {
        this(operation, statement, receiver, location, null);
    }
}
