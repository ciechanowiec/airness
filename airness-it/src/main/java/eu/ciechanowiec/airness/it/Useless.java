package eu.ciechanowiec.airness.it;

import eu.ciechanowiec.airness.Justification;

/**
 * Suppresses a rule that does not fire here. A suppression outlives the code it was written for, and
 * one left behind reads as a rule considered and set aside rather than as a rule nothing here breaks.
 * It carries a reason, so that what it trips is the useless-suppression rule and nothing else.
 */
public final class Useless {

    /**
     * Suppresses a rule that no statement in this method can break.
     *
     * @return a value chosen to break nothing
     */
    @Justification("This fixture is about the suppressed rule not firing, which is why it is suppressed.")
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public String plain() {
        return "no address here";
    }
}
