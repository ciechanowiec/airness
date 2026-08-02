package eu.ciechanowiec.airness.it;

/**
 * Trips the general fully-qualified-name rule without relying on a project-specific package prefix.
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
