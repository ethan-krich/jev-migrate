package transpiler.cobol2java.translate;

import java.math.BigDecimal;

/** A resolved COBOL operand: a data item, a literal, a figurative constant or an arithmetic expression. */
public sealed interface Operand {
    record Field(DataItem item) implements Operand {}

    /** @param text the literal as written in the source (DISPLAY shows it this way, modulo sign) */
    record NumericLiteral(BigDecimal value, String text) implements Operand {
        public boolean isInteger() {
            return value.stripTrailingZeros().scale() <= 0 && !text.contains(".");
        }

        public boolean isSigned() {
            return text.startsWith("+") || text.startsWith("-");
        }

        /** The literal's digits as written, without sign, as moved to an alphanumeric receiver. */
        public String unsignedText() {
            return isSigned() ? text.substring(1) : text;
        }

        /** GnuCOBOL DISPLAY of a literal: an explicit sign is normalised to the value's sign. */
        public String displayText() {
            return isSigned() ? (value.signum() < 0 ? "-" : "+") + unsignedText() : text;
        }
    }

    record AlphanumericLiteral(String value) implements Operand {}

    /** ZERO(S/ES) or SPACE(S): {@code fill} is the repeated character. */
    record Figurative(char fill, String name) implements Operand {}

    /** A compound arithmetic expression (COMPUTE, conditions). */
    record Arithmetic(io.proleap.cobol.asg.metamodel.valuestmt.ArithmeticValueStmt stmt) implements Operand {}

    /** The RETURN-CODE special register of the current program. */
    record ReturnCode() implements Operand {}

    default boolean isNumericValued() {
        return switch (this) {
            case Field f -> f.item().isNumeric();
            case NumericLiteral n -> true;
            case Figurative f -> f.fill() == '0';
            case Arithmetic a -> true;
            case ReturnCode r -> true;
            case AlphanumericLiteral a -> false;
        };
    }

    default String category() {
        return switch (this) {
            case Field f -> f.item().category();
            case NumericLiteral n -> "numeric_literal";
            case AlphanumericLiteral a -> "alphanumeric_literal";
            case Figurative f -> "figurative";
            case Arithmetic a -> "arithmetic_expression";
            case ReturnCode r -> "numeric";
        };
    }
}
