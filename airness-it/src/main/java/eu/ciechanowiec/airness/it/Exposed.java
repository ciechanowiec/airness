package eu.ciechanowiec.airness.it;

/**
 * Keeps the array it is handed and gives it back, which SpotBugs reports from both ends.
 *
 * <p>It exists because SpotBugs is the analyzer whose silence says nothing. A filter file it could not
 * resolve leaves it reporting zero bugs, and so does a project with none, so the only way to tell the
 * two apart is to keep a bug here that its shipped configuration is known to report.
 */
public final class Exposed {

    private final String[] values;

    /**
     * Keeps the array as it arrives rather than copying it.
     *
     * @param values the array this instance holds
     */
    public Exposed(String[] values) {
        this.values = values;
    }

    /**
     * Hands back the array itself rather than a copy.
     *
     * @return the array this instance holds
     */
    public String[] values() {
        return this.values;
    }
}
