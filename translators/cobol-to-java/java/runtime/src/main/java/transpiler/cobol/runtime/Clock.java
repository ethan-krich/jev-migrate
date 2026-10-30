package transpiler.cobol.runtime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The current date and time for ACCEPT ... FROM DATE / DAY / DAY-OF-WEEK / TIME.
 *
 * <p>Like GnuCOBOL's runtime, the environment variable {@code COB_CURRENT_DATE} fixes the clock
 * ({@code YYYY/MM/DD hh:mm:ss} or {@code YYYYMMDDhhmmss}); hundredths of a second are then 00.
 */
public final class Clock {
    public static final String VARIABLE = "COB_CURRENT_DATE";

    private Clock() {}

    /** The fixed time from the environment, or the system clock. */
    public static LocalDateTime now() {
        String fixed = System.getenv(VARIABLE);
        if (fixed == null || fixed.isEmpty()) {
            return LocalDateTime.now();
        }
        for (String pattern : new String[] {"yyyy/MM/dd HH:mm:ss", "yyyyMMddHHmmss"}) {
            try {
                return LocalDateTime.parse(fixed, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException e) {
                // Try the next format.
            }
        }
        throw new IllegalStateException(VARIABLE + " must be 'YYYY/MM/DD hh:mm:ss' or 'YYYYMMDDhhmmss': " + fixed);
    }

    private static String digits(long value, int width) {
        String s = Long.toString(value);
        return "0".repeat(Math.max(0, width - s.length())) + s;
    }

    /** ACCEPT FROM DATE: YYMMDD. */
    public static String date() {
        LocalDateTime t = now();
        return digits(t.getYear() % 100, 2) + digits(t.getMonthValue(), 2) + digits(t.getDayOfMonth(), 2);
    }

    /** ACCEPT FROM DATE YYYYMMDD. */
    public static String dateYyyymmdd() {
        LocalDateTime t = now();
        return digits(t.getYear(), 4) + digits(t.getMonthValue(), 2) + digits(t.getDayOfMonth(), 2);
    }

    /** ACCEPT FROM DAY: YYDDD. */
    public static String day() {
        LocalDateTime t = now();
        return digits(t.getYear() % 100, 2) + digits(t.getDayOfYear(), 3);
    }

    /** ACCEPT FROM DAY YYYYDDD. */
    public static String dayYyyyddd() {
        LocalDateTime t = now();
        return digits(t.getYear(), 4) + digits(t.getDayOfYear(), 3);
    }

    /** ACCEPT FROM DAY-OF-WEEK: 1 (Monday) to 7 (Sunday). */
    public static String dayOfWeek() {
        return Integer.toString(now().getDayOfWeek().getValue());
    }

    /**
     * ACCEPT FROM MICROSECOND-TIME: hhmmssuuuuuu. As in GnuCOBOL, a fixed clock without a
     * fraction of a second keeps the system clock's microseconds.
     */
    public static String microsecondTime() {
        LocalDateTime system = LocalDateTime.now();
        String fixed = System.getenv(VARIABLE);
        LocalDateTime t = fixed == null || fixed.isEmpty() ? system : now();
        int micros = system.getNano() / 1000;
        return digits(t.getHour(), 2) + digits(t.getMinute(), 2) + digits(t.getSecond(), 2) + digits(micros, 6);
    }

    /** ACCEPT FROM TIME: hhmmssff (hundredths). */
    public static String time() {
        String fixed = System.getenv(VARIABLE);
        LocalDateTime t = now();
        int hundredths = fixed == null || fixed.isEmpty() ? t.getNano() / 10_000_000 : 0;
        return digits(t.getHour(), 2) + digits(t.getMinute(), 2) + digits(t.getSecond(), 2) + digits(hundredths, 2);
    }
}
