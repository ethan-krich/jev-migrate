package transpiler.cobol.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A numeric-edited or alphanumeric-edited item: a view of its bytes in a {@link Storage},
 * written through its PICTURE as GnuCOBOL 3.2 writes it ({@code cob_move_display_to_edited},
 * {@code cob_move_alphanum_to_edited}) and read back by de-editing.
 *
 * <p>{@code picture} is the PICTURE with repetitions expanded ({@code ZZ,ZZ9.99}); with
 * DECIMAL-POINT IS COMMA the roles of {@code .} and {@code ,} are swapped. The currency sign
 * is {@code $}.
 */
public final class EditedField {
    private static final char CURRENCY = '$';

    private final Storage storage;
    private final int offset;
    private final String picture;
    private final boolean numeric;
    private final boolean blankWhenZero;
    private final char point;
    private final char comma;
    /** Digit places before the decimal point (floating symbols included, as GnuCOBOL counts them). */
    private final int integerPlaces;
    /** Digit places after the decimal point. */
    private final int scale;

    public EditedField(Storage storage, int offset, String picture, boolean blankWhenZero, boolean decimalComma) {
        if (offset < 0 || offset + picture.replace("CR", "C").replace("DB", "D").length() + count(picture, "CR") + count(picture, "DB")
                > storage.length()) {
            throw new IllegalArgumentException("invalid range for PICTURE " + picture);
        }
        this.storage = storage;
        this.offset = offset;
        this.picture = picture;
        this.blankWhenZero = blankWhenZero;
        this.point = decimalComma ? ',' : '.';
        this.comma = decimalComma ? '.' : ',';
        this.numeric = picture.chars().noneMatch(c -> c == 'X' || c == 'A');
        int before = 0;
        int after = 0;
        boolean seenPoint = false;
        boolean countSign = true;
        boolean countCurrency = true;
        for (int i = 0; i < picture.length(); i++) {
            char c = picture.charAt(i);
            boolean place = c == '9' || c == 'Z' || c == '*';
            if (place) {
                countSign = false;
                countCurrency = false;
            } else if (c == CURRENCY && countCurrency && isFloating(i)) {
                place = true;
            } else if ((c == '+' || c == '-') && countSign && isFloating(i)) {
                place = true;
            } else if (c == 'V' || c == point) {
                seenPoint = true;
                continue;
            }
            if (seenPoint) {
                if (c == '9' || c == 'Z' || c == '*' || ((c == '+' || c == '-' || c == CURRENCY) && isFloating(i))) {
                    after++;
                }
            } else if (place) {
                before++;
            }
        }
        this.integerPlaces = before;
        this.scale = after;
    }

    private static int count(String s, String part) {
        return (s.length() - s.replace(part, "").length()) / part.length();
    }

    private boolean isZero(String digits) {
        return digits.chars().allMatch(ch -> ch == '0');
    }

    /** Whether the symbol at {@code i} belongs to a floating string (two or more of the same insertion symbol). */
    private boolean isFloating(int i) {
        char c = picture.charAt(i);
        return (i > 0 && picture.charAt(i - 1) == c) || (i + 1 < picture.length() && picture.charAt(i + 1) == c);
    }

    /** The item's size in bytes. */
    public int size() {
        return picture.length();
    }

    /** Initialises the item as if by MOVE ZERO (WORKING-STORAGE without VALUE); returns this view. */
    public EditedField initial() {
        if (numeric) {
            move(BigDecimal.ZERO);
        } else {
            storage.fill(offset, size(), (byte) ' ');
        }
        return this;
    }

    /** {@code VALUE literal}: a numeric literal is edited, a nonnumeric one is stored as is. */
    public EditedField initial(String value) {
        storage.put(offset, size(), value);
        return this;
    }

    public EditedField initialNumber(String value) {
        move(new BigDecimal(value));
        return this;
    }

    /** The item's bytes as an alphanumeric view (the argument of CALL ... BY REFERENCE). */
    public AlphanumericField view() {
        return new AlphanumericField(storage, offset, size());
    }

    public String get() {
        return storage.text(offset, size());
    }

    public String display() {
        return get();
    }

    @Override
    public String toString() {
        return get();
    }

    public void move(NumericField source) {
        move(source.get());
    }

    /** MOVE of a numeric value: edited through the PICTURE. */
    public void move(BigDecimal value) {
        if (!numeric) {
            set(value.toPlainString());
            return;
        }
        boolean neg = value.signum() < 0;
        BigDecimal truncated = value.abs().setScale(scale, RoundingMode.DOWN);
        String digits = truncated.unscaledValue().toString();
        // Source digits aligned so that integerPlaces digits precede the point.
        int need = integerPlaces + scale;
        if (digits.length() < need) {
            digits = "0".repeat(need - digits.length()) + digits;
        }
        int src = digits.length() - need;
        char[] out = new char[size()];
        int end = out.length;
        int decimalPoint = -1;
        boolean isZero = true;
        boolean suppressZero = true;
        boolean trailingSign = false;
        boolean trailingCurrency = false;
        char pad = ' ';
        int signPosition = -1;
        char signSymbol = 0;
        int currencyPosition = -1;
        boolean signFirst = false;
        int dst = 0;
        for (int i = 0; i < picture.length(); i++) {
            char c = picture.charAt(i);
            switch (c) {
                case '0', '/' -> out[dst++] = c;
                case 'B' -> out[dst++] = suppressZero ? pad : 'B';
                case '9' -> {
                    char d = digits.charAt(src++);
                    out[dst++] = d;
                    if (d != '0') {
                        isZero = false;
                    }
                    suppressZero = false;
                    trailingSign = true;
                    trailingCurrency = true;
                }
                case 'V' -> decimalPoint = dst - 1;
                case 'C', 'D' -> {
                    end = dst;
                    if (neg && !isZero) {
                        out[dst] = c;
                        out[dst + 1] = c == 'C' ? 'R' : 'B';
                    } else {
                        out[dst] = ' ';
                        out[dst + 1] = ' ';
                    }
                    dst += 2;
                    i++;
                }
                case 'Z', '*' -> {
                    char d = digits.charAt(src++);
                    if (d != '0') {
                        isZero = false;
                        suppressZero = false;
                    }
                    pad = c == '*' ? '*' : ' ';
                    out[dst++] = suppressZero ? pad : d;
                    trailingSign = true;
                    trailingCurrency = true;
                }
                case '+', '-' -> {
                    if (!isFloating(i)) {
                        // Fixed insertion: the sign of the value, in place.
                        out[dst++] = neg && !isZero(digits) ? '-' : c == '+' ? '+' : ' ';
                        if (trailingSign && end == out.length) {
                            end = dst - 1;
                        }
                        continue;
                    }
                    char d = src < digits.length() ? digits.charAt(src) : '0';
                    src++;
                    if (d != '0') {
                        isZero = false;
                        suppressZero = false;
                    }
                    if (trailingSign) {
                        out[dst] = neg && !isZero(digits) ? '-' : c == '+' ? '+' : ' ';
                        src--;
                        if (end == out.length) {
                            end = dst;
                        }
                    } else if (dst == 0 || suppressZero) {
                        out[dst] = pad;
                        signPosition = dst;
                        signSymbol = c;
                        if (currencyPosition < 0) {
                            signFirst = true;
                        }
                    } else {
                        out[dst] = d;
                    }
                    dst++;
                }
                default -> {
                    if (c == point) {
                        out[dst] = point;
                        decimalPoint = dst;
                        dst++;
                    } else if (c == comma) {
                        out[dst++] = suppressZero ? pad : c;
                    } else if (c == CURRENCY && !isFloating(i)) {
                        out[dst++] = CURRENCY;
                    } else if (c == CURRENCY) {
                        char d = src < digits.length() ? digits.charAt(src) : '0';
                        src++;
                        if (d != '0') {
                            isZero = false;
                            suppressZero = false;
                        }
                        if (trailingCurrency) {
                            out[dst] = CURRENCY;
                            src--;
                        } else if (dst == 0 || suppressZero) {
                            out[dst] = pad;
                            currencyPosition = dst;
                        } else {
                            out[dst] = d;
                        }
                        dst++;
                    } else {
                        throw new IllegalStateException("invalid PICTURE symbol " + c + " in " + picture);
                    }
                }
            }
        }
        if (suppressZero || (isZero && blankWhenZero)) {
            // All digit places are zero (or BLANK WHEN ZERO): spaces, or '*' except the point.
            if (pad == ' ' || blankWhenZero) {
                java.util.Arrays.fill(out, ' ');
            } else {
                for (int k = 0; k < out.length; k++) {
                    if (out[k] != point) {
                        out[k] = pad;
                    }
                }
            }
        } else {
            if (decimalPoint >= 0) {
                for (int k = decimalPoint + 1; k < end; k++) {
                    if (out[k] == ' ' || out[k] == '*') {
                        out[k] = '0';
                    }
                }
            }
            if (signPosition >= 0 || currencyPosition >= 0) {
                if (signPosition >= 0) {
                    if (neg && !isZero) {
                        signSymbol = '-';
                    } else if (signSymbol != '+') {
                        signSymbol = ' ';
                    }
                }
                // Floating symbols go just before the first non-space character.
                int k = end - 1;
                while (k > 0 && out[k] != ' ') {
                    k--;
                }
                if (signPosition >= 0 && currencyPosition >= 0) {
                    if (signFirst) {
                        out[k] = CURRENCY;
                        if (k > 0) {
                            k--;
                        }
                        out[signPosition] = signSymbol;
                    } else {
                        out[k] = signSymbol;
                        if (k > 0) {
                            k--;
                        }
                        out[currencyPosition] = CURRENCY;
                    }
                } else if (signPosition >= 0) {
                    out[k] = signSymbol;
                } else {
                    out[k] = CURRENCY;
                }
            }
            int count = 0;
            for (int k = 0; k < end; k++) {
                if (out[k] == 'B') {
                    out[k] = count == 0 ? pad : ' ';
                } else {
                    count++;
                }
            }
        }
        for (int k = 0; k < out.length; k++) {
            storage.bytes[offset + k] = (byte) out[k];
        }
    }

    /**
     * MOVE of alphanumeric data: into an alphanumeric-edited item, characters fill the X, A and
     * 9 positions left to right around the inserted {@code B} (space), {@code 0} and {@code /}.
     */
    public void set(String value) {
        if (numeric) {
            storage.put(offset, size(), value);
            return;
        }
        int src = 0;
        for (int i = 0; i < picture.length(); i++) {
            char c = picture.charAt(i);
            char out = switch (c) {
                case 'X', 'A', '9' -> src < value.length() ? value.charAt(src++) : ' ';
                case 'B' -> ' ';
                default -> c;
            };
            storage.bytes[offset + i] = (byte) out;
        }
    }

    /** MOVE of a figurative constant: ZERO is edited as the value zero, others fill the item. */
    public void fill(char c) {
        if (numeric && c == '0') {
            move(BigDecimal.ZERO);
        } else {
            storage.fill(offset, size(), (byte) c);
        }
    }

    /**
     * De-editing (MOVE numeric-edited to numeric): the digits found at the PICTURE's digit
     * places, the point where the PICTURE has it, negative if a sign place shows {@code -} or
     * {@code CR} / {@code DB} is present.
     */
    public BigDecimal value() {
        String text = get();
        StringBuilder digits = new StringBuilder();
        boolean neg = false;
        int fraction = 0;
        boolean afterPoint = false;
        for (int i = 0, k = 0; i < picture.length() && k < text.length(); i++, k++) {
            char c = picture.charAt(i);
            char t = text.charAt(k);
            if (c == 'C' || c == 'D') {
                if (t == 'C' || t == 'D') {
                    neg = true;
                }
                i++;
                k++;
                continue;
            }
            if (c == 'V') {
                afterPoint = true;
                k--;
                continue;
            }
            if (c == point) {
                afterPoint = true;
                continue;
            }
            if (t == '-') {
                neg = true;
            }
            boolean place = c == '9' || c == 'Z' || c == '*' || c == '+' || c == '-' || c == CURRENCY;
            if (place && t >= '0' && t <= '9') {
                digits.append(t);
                if (afterPoint) {
                    fraction++;
                }
            } else if (place && afterPoint && c != '+' && c != '-') {
                digits.append('0');
                fraction++;
            }
        }
        BigDecimal v = digits.isEmpty() ? BigDecimal.ZERO : new BigDecimal(new java.math.BigInteger(digits.toString()), fraction);
        return neg ? v.negate() : v;
    }
}
