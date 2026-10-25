package transpiler.cobol.runtime;

/** A translated COBOL program: one instance per run unit, so WORKING-STORAGE persists across CALLs. */
public interface CobolProgram {
    /** Executes the PROCEDURE DIVISION from its first statement. */
    void procedureDivision();

    /** This program's RETURN-CODE special register. */
    NumericField returnCode();

    /**
     * Binds the PROCEDURE DIVISION USING items to a CALL's arguments (the caller's bytes, or
     * copies for BY CONTENT); items without an argument are left unbound.
     */
    default void using(AlphanumericField[] arguments) {}

    /** The sizes of the PROCEDURE DIVISION USING items, which a dynamic CALL checks its arguments against. */
    default int[] parameterSizes() {
        return new int[0];
    }
}
