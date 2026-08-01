package eu.ciechanowiec.airness.it;

import eu.ciechanowiec.airness.Justification;

/**
 * Pairs a suppression with its reason, the way the rule set expects, and imports the annotation from
 * the parent rather than declaring one of its own. It is the control for {@link Unjustified}: the two
 * files differ in one annotation and in nothing else, so a finding reported here as well would mean the
 * rule matches the suppression rather than the missing reason.
 */
public final class Justified {

    /**
     * Suppresses a rule that genuinely fires here, and states why.
     *
     * @return the loopback address the suppressed rule objects to
     */
    @Justification("A fixture's subject is the literal itself, so there is nothing here to configure.")
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public String address() {
        return "127.0.0.1";
    }
}
