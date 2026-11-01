package com.acme.dates;

import java.time.LocalDate;

/** A calendar date of the proleptic Gregorian calendar: year, month (1 to 12) and day of the month. */
public record CalendarDate(int year, int month, int day) {
    /** The date written as the eight-digit number YYYYMMDD. */
    public static CalendarDate ofNumber(int yyyymmdd) {
        return of(Dates.date(yyyymmdd));
    }

    /** The date {@code days} days later (earlier when negative). */
    public CalendarDate plusDays(int days) {
        return of(LocalDate.of(year, month, day).plusDays(days));
    }

    /** This date as the eight-digit number YYYYMMDD. */
    public int toNumber() {
        return year * 10000 + month * 100 + day;
    }

    /** This date as text in the ISO format yyyy-MM-dd. */
    public String iso() {
        return LocalDate.of(year, month, day).toString();
    }

    private static CalendarDate of(LocalDate d) {
        return new CalendarDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }
}
