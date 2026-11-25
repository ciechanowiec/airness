package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Map;

/**
 * The one severity scale the report reads every advisory on, whatever database rated it.
 *
 * <p>Databases name the same level differently: the GitHub advisory database writes {@code moderate} where a CVSS
 * rating writes {@code medium}. An advisory no database rated, or rated with a word outside the scale, is
 * {@link #UNRATED}, which is read first, since nothing says it is harmless.
 */
public enum ReportSeverity {

    /**
     * No database rated the advisory on this scale.
     */
    UNRATED("Unrated"),

    /**
     * A critical rating.
     */
    CRITICAL("Critical"),

    /**
     * A high rating.
     */
    HIGH("High"),

    /**
     * A medium rating, which the GitHub advisory database calls moderate.
     */
    MEDIUM("Medium"),

    /**
     * A low rating, or one that rates the advisory as informational only.
     */
    LOW("Low");

    private static final Map<String, ReportSeverity> SPELLINGS = Map.of(
        "critical", CRITICAL, "high", HIGH, "important", HIGH, "medium", MEDIUM, "moderate", MEDIUM, "low", LOW,
        "negligible", LOW, "info", LOW, "none", LOW
    );

    private final String label;

    ReportSeverity(String label) {
        this.label = label;
    }

    /**
     * Reads a database's rating.
     *
     * @param rating the rating as the database wrote it, in any case
     * @return the level on the report's scale
     */
    public static ReportSeverity of(String rating) {
        return SPELLINGS.getOrDefault(rating.strip().toLowerCase(Locale.ROOT), UNRATED);
    }

    /**
     * The level as the report prints it.
     *
     * @return such as {@code Medium}
     */
    public String label() {
        return this.label;
    }

    /**
     * The token the report styles the level by.
     *
     * @return such as {@code medium}
     */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
