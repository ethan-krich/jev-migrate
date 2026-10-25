package transpiler.cobol2java.translate;

import java.math.BigDecimal;
import java.util.List;

/** A resolved COBOL operand: a data item, a literal, a figurative constant or an arithmetic expression. */
public sealed interface Operand {
    /**
     * A data item, possibly subscripted ({@code subscripts}, outermost table first) and
     * reference-modified. A reference-modified item is alphanumeric whatever its own category.
     */
    record Field(DataItem item, List<Subscript> subscripts, RefMod refmod) implements Operand {
        public Field(DataItem item) {
            this(item, List.of(), null);
        }

        /**
         * Alphanumeric data when sent or compared: a PIC X item, a group, an edited item, or any
         * reference modification.
         */
        public boolean alphanumeric() {
            return refmod != null || item.isAlphanumeric() || item.isGroup() || item.isEdited();
        }

        /** An edited item (not reference-modified): receives through its PICTURE. */
        public boolean edited() {
            return refmod == null && item.isEdited();
        }

        public boolean numeric() {
            return refmod == null && item.isNumeric();
        }

        public boolean integer() {
            return refmod == null && item.isInteger();
        }

        /** Reference to the same storage as {@code other} (same item, no subscripts or modification). */
        public boolean sameAs(Operand other) {
            return other instanceof Field f && f.item == item && subscripts.isEmpty() && f.subscripts.isEmpty()
                    && refmod == null && f.refmod == null;
        }
    }

    /** One subscript: an integer value plus a constant offset ({@code I + 1}); {@code value} null for a literal. */
    record Subscript(Operand value, int offset) {}

    /** {@code (start : length)}; {@code length} is null when omitted (to the end of the item). */
    record RefMod(Subscript start, Subscript length) {}

    /** @param text the literal as written in the source (DISPLAY shows it this way, modulo sign) */
    record NumericLiteral(BigDecimal value, String text) implements Operand {
        public boolean isInteger() {
            return value.stripTrailingZeros().scale() <= 0 && !text.contains(".") && !text.contains(",");
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

    /**
     * A figurative constant: {@code pattern} repeated to the receiving length. ZERO, SPACE,
     * QUOTE, HIGH-VALUE and LOW-VALUE have one-character patterns; {@code ALL "lit"} has the
     * literal's characters.
     */
    record Figurative(String pattern, String name) implements Operand {
        public boolean isZero() {
            return name.equals("ZERO");
        }

        public boolean isSingle() {
            return pattern.length() == 1;
        }

        public char fill() {
            return pattern.charAt(0);
        }
    }

    /** An INDEXED BY name: its occurrence number. */
    record Index(DataItem.IndexName index) implements Operand {}

    /** A compound arithmetic expression (COMPUTE, conditions). */
    record Arithmetic(io.proleap.cobol.asg.metamodel.valuestmt.ArithmeticValueStmt stmt) implements Operand {}

    /** The RETURN-CODE special register of the current program. */
    record ReturnCode() implements Operand {}

    default boolean isNumericValued() {
        return switch (this) {
            case Field f -> f.numeric();
            case NumericLiteral n -> true;
            case Figurative f -> f.isZero();
            case Arithmetic a -> true;
            case ReturnCode r -> true;
            case Index i -> true;
            case AlphanumericLiteral a -> false;
        };
    }

    default String category() {
        return switch (this) {
            case Field f -> f.refmod() != null ? "alphanumeric" : f.item().category();
            case NumericLiteral n -> "numeric_literal";
            case AlphanumericLiteral a -> "alphanumeric_literal";
            case Figurative f -> "figurative";
            case Arithmetic a -> "arithmetic_expression";
            case ReturnCode r -> "numeric";
            case Index i -> "index";
        };
    }
}
