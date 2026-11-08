package eu.ciechanowiec.airness.governance;

/**
 * The class the license policy puts a resolved artifact in, in the order the compliance report presents them.
 */
public enum LicenseClass {

    /**
     * An admitted license with no copyleft obligation.
     */
    PERMISSIVE("Permissive", "permissive"),

    /**
     * An admitted license whose copyleft binds only a component used unmodified.
     */
    WEAK_COPYLEFT("Weak copyleft", "weak-copyleft"),

    /**
     * No declared license the policy admits.
     */
    OUTSIDE_POLICY("Outside policy", "refused");

    private final String label;
    private final String key;

    LicenseClass(String label, String key) {
        this.label = label;
        this.key = key;
    }

    /**
     * The readable name the report and its CSV export show.
     *
     * @return the label
     */
    public String label() {
        return this.label;
    }

    /**
     * The token the report styles and filters this class by.
     *
     * @return the key
     */
    public String key() {
        return this.key;
    }
}
