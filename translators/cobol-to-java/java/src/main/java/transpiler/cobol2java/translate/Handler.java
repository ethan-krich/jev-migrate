package transpiler.cobol2java.translate;

import java.util.List;

/** A deterministic builtin transformation, addressed by its stable handler id from mapping rules. */
public interface Handler {
    String id();

    /** The COBOL operation (statement verb) this handler implements. */
    String operation();

    String description();

    List<com.github.javaparser.ast.stmt.Statement> generate(OpUnit unit, TranslationContext ctx) throws HandlerException;

    @FunctionalInterface
    interface Body {
        List<com.github.javaparser.ast.stmt.Statement> generate(OpUnit unit, TranslationContext ctx) throws HandlerException;
    }

    static Handler of(String id, String operation, String description, Body body) {
        return new Handler() {
            public String id() {
                return id;
            }

            public String operation() {
                return operation;
            }

            public String description() {
                return description;
            }

            public List<com.github.javaparser.ast.stmt.Statement> generate(OpUnit unit, TranslationContext ctx)
                    throws HandlerException {
                return body.generate(unit, ctx);
            }
        };
    }
}
