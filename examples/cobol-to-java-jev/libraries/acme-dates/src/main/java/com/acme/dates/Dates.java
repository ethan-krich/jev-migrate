package com.acme.dates;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Calendar arithmetic on dates written as eight-digit numbers YYYYMMDD (proleptic Gregorian calendar). */
public final class Dates {
    private static final DateTimeFormatter NUMBER = DateTimeFormatter.BASIC_ISO_DATE;

    private Dates() {}

    /** The date {@code days} days after {@code yyyymmdd} (before it when negative), as a YYYYMMDD number. */
    public static int addDays(int yyyymmdd, int days) {
        return number(date(yyyymmdd).plusDays(days));
    }

    /** The number of days from {@code from} to {@code to}, both YYYYMMDD (negative when {@code to} is earlier). */
    public static int daysBetween(int from, int to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(date(from), date(to)));
    }

    /** Whether {@code year} is a leap year of the Gregorian calendar. */
    public static boolean isLeapYear(int year) {
        return java.time.Year.isLeap(year);
    }

    /** The ISO day of the week of a YYYYMMDD date: 1 for Monday to 7 for Sunday. */
    public static int dayOfWeek(int yyyymmdd) {
        DayOfWeek d = date(yyyymmdd).getDayOfWeek();
        return d.getValue();
    }

    /** A YYYYMMDD date as text in the ISO format yyyy-MM-dd. */
    public static String isoFormat(int yyyymmdd) {
        return date(yyyymmdd).toString();
    }

    static LocalDate date(int yyyymmdd) {
        return LocalDate.parse(String.format("%08d", yyyymmdd), NUMBER);
    }

    static int number(LocalDate d) {
        return Integer.parseInt(d.format(NUMBER));
    }
}
