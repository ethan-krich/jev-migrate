package transpiler.cobol.runtime;

/** A translated COBOL program: one instance per run unit, so WORKING-STORAGE persists across CALLs. */
public interface CobolProgram {
    /** Executes the PROCEDURE DIVISION from its first statement. */
    void procedureDivision();

    /** This program's RETURN-CODE special register. */
    NumericField returnCode();
}
