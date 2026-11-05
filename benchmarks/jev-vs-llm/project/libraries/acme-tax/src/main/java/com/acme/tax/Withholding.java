package com.acme.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Income tax withheld from a weekly gross pay (amounts in currency units with two decimals). */
public final class Withholding {
    private static final BigDecimal ALLOWANCE = new BigDecimal("500.00");
    private static final BigDecimal RATE = new BigDecimal("0.20");

    private Withholding() {}

    /** The tax withheld from {@code gross}: 20% of the part above the 500.00 allowance, rounded half up to the cent; zero at or below it. */
    public static BigDecimal tax(BigDecimal gross) {
        if (gross.compareTo(ALLOWANCE) <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return gross.subtract(ALLOWANCE).multiply(RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /** The pay left after tax: {@code gross} minus {@link #tax(BigDecimal)}. */
    public static BigDecimal net(BigDecimal gross) {
        return gross.subtract(tax(gross));
    }

    /** The tax-free allowance of one pay period, 500.00. */
    public static BigDecimal allowance() {
        return ALLOWANCE;
    }
}
