package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * The counted phrases of the report's prose, so a number and the words beside it always agree.
 */
@UtilityClass
final class ReportCounts {

    private static final int PERCENT = 100;

    static String plural(long count, String singular, String several) {
        return count + " " + (count == 1 ? singular : several);
    }

    static String plural(long count, String singular) {
        return plural(count, singular, singular + "s");
    }

    // The verb that agrees with a counted subject: "1 artifact version has", "2 artifact versions have".
    static String agreeing(long count, String one, String several) {
        return count == 1 ? one : several;
    }

    static String percentOf(long count, long total) {
        return total == 0 ? "0" : String.format(Locale.ROOT, "%.1f", (double) count * PERCENT / total);
    }
}
