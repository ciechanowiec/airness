package eu.ciechanowiec.airness.it;

/**
 * Compares two strings by reference, which Error Prone reports and the compiler then refuses.
 *
 * <p>It shares a source root with {@link Unchecked} for the same reason: a finding from either is a
 * compile error rather than a report, so a fixture for one kept under the ordinary sources would end
 * every build in this module before the case under test reached anything.
 */
public final class Compared {

    /**
     * Asks whether two strings are the same object rather than whether they read the same.
     *
     * @param first  one string
     * @param second the other
     * @return whether the two are the same reference, which is the violation rather than the answer
     */
    public boolean same(String first, String second) {
        return first == second;
    }
}
