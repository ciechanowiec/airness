package eu.ciechanowiec.airness.it;

/**
 * Trips only the fully-qualified-name rule, and only when the group root that rule reads is the group
 * this class actually sits under. It tells a configured group root apart from one left pointing at
 * another project, which is the case that would otherwise pass by never firing.
 */
public final class Qualified {

    /**
     * Qualifies a type under the consumer's own group inline instead of importing it.
     *
     * @return the qualified reference, whose spelling is the violation
     */
    public String qualify() {
        eu.ciechanowiec.airness.it.Offender offender = new eu.ciechanowiec.airness.it.Offender();
        return offender.toString();
    }
}
