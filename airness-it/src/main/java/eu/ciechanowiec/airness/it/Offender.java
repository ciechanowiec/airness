package eu.ciechanowiec.airness.it;

/**
 * Trips a rule from each analyzer on purpose, so a configuration that loaded is told apart from one
 * that was silently not found. A clean class would pass either way and would evidence nothing, which is
 * the failure mode this class exists to rule out.
 */
public class Offender {

    /**
     * Carries a magic number and an unbraced branch, each forbidden by the rule set.
     *
     * @return a value nobody reads, since the violation is the point rather than the result
     */
    public String offend() {
        int magic = 8788;
        if (magic > 0) return "offended";
        return "unreachable";
    }
}
