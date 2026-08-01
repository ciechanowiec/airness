package eu.ciechanowiec.airness.it;

/**
 * Trips a rule from each analyzer on purpose, so a configuration that loaded is told apart from one
 * that was silently not found. A clean class would pass either way and would evidence nothing, which is
 * the failure mode this class exists to rule out.
 */
public class Offender {

    /**
     * Returns a null literal, which the rule set forbids in favour of an {@link java.util.Optional}. The
     * magic number and the unbraced branch are here for the same reason.
     *
     * @return nothing usable; the violation is the point, not the value
     */
    public String offend() {
        int magic = 8788;
        if (magic > 0) return null;
        return "unreachable";
    }
}
