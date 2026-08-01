package eu.ciechanowiec.airness.it;

/**
 * Suppresses a rule without stating why, which the rule set forbids. This is the case worth pinning: a
 * rule that stopped firing would leave every unexplained suppression in every project that inherits the
 * harness passing in silence, and an unexplained suppression is indistinguishable from a defect someone
 * quietened.
 */
public final class Unjustified {

    /**
     * Suppresses the same rule {@link Justified} does, and omits the reason.
     *
     * @return the loopback address the suppressed rule objects to
     */
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public String address() {
        return "127.0.0.1";
    }
}
