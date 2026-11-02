package com.acme.dates;

import java.time.format.DateTimeFormatter;

/** Writes dates as text in one fixed pattern. */
public final class DateFormatter {
    private final DateTimeFormatter pattern;

    /** A formatter that writes dates as dd/MM/yyyy. */
    public DateFormatter() {
        this.pattern = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    }

    /** A YYYYMMDD date as text in this formatter's pattern. */
    public String format(int yyyymmdd) {
        return Dates.date(yyyymmdd).format(pattern);
    }
}
