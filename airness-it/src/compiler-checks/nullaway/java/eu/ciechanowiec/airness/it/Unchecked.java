package eu.ciechanowiec.airness.it;

/**
 * Returns a null literal from a method the null-checker reads as non-null.
 *
 * <p>It sits outside the ordinary source roots, and one case adds the directory back. A NullAway
 * finding is a compile error rather than a report, so a fixture for it kept under src/main/java would
 * end every build in this module before the case under test reached anything.
 */
public final class Unchecked {

    /**
     * Hands back nothing at all, from a signature that promises something.
     *
     * @return a null literal, which is the violation rather than the value
     */
    public String value() {
        return null;
    }
}
