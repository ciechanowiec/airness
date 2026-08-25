package eu.ciechanowiec.airness.governance;

import java.util.List;

/**
 * One rule's verdict: what it looked for, and every place that broke it.
 *
 * <p>Every check reports through this type so a failure reads the same whichever check produced it. The
 * alternative, each check formatting its own message, is how one check comes to print a bare list of
 * paths while another prints a sentence per offence, and a developer reading the second has to work out
 * what the first was even asserting.
 *
 * <p>An empty offence list is the ordinary outcome rather than an absent result, which is why a check
 * returns a {@link Findings} per rule rather than only the ones that failed. A rule that ran and found
 * nothing and a rule that never ran are then different values, and the caller can say which it has.
 *
 * @param headline what the rule requires, stated so the offences below it read as departures from it
 * @param offences every place the rule was broken, one per entry, each naming where
 */
public record Findings(String headline, List<String> offences) {

    private static final String INDENT = "  ";

    /**
     * Makes a defensive copy so a caller cannot alter a verdict after it has been reported.
     *
     * @param headline what the rule requires
     * @param offences every place the rule was broken
     */
    public Findings {
        offences = List.copyOf(offences);
    }

    /**
     * Whether the rule found nothing to object to.
     *
     * @return whether the rule was satisfied
     */
    public boolean clean() {
        return this.offences.isEmpty();
    }

    /**
     * The verdict as a developer reads it: the requirement, the count, then the offences beneath it.
     *
     * @return the rendered verdict
     */
    public String report() {
        List<String> lines = this.offences.stream().map(offence -> INDENT + offence).toList();
        return "%s (%d):%n%s".formatted(
            this.headline, this.offences.size(), String.join(System.lineSeparator(), lines)
        );
    }
}
