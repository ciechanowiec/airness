package eu.ciechanowiec.airness.web;

/**
 * One undecidable a project has looked at, measured by hand, and accepted.
 *
 * <p>A rule that cannot decide is a finding by default, because the cases it cannot decide are the
 * cases nobody has checked. Some of them are genuinely undecidable: a control whose ground is drawn
 * by a gradient is a contrast the rules cannot read and a person can, once, with a measuring tool.
 * This is how that measurement is written down where the next reader will find it.
 *
 * <p>An exemption that matches nothing is itself a finding. The guideline requires a check to fail on
 * a suppression that no longer suppresses anything, and the reason is the same here as everywhere
 * else: the thing being excused changes, the excuse does not, and an excuse nobody can see expiring
 * is an exception that has quietly become permanent.
 *
 * @param rule   the identifier of the rule being accepted, as the rules themselves publish it
 * @param target the element it is accepted for, matched where the reported selector contains it
 * @param reason why the rule cannot decide here and what was measured instead
 */
public record AxeExemption(String rule, String target, String reason) {

    /**
     * Holds a reason, because an exemption without one is a rule turned off.
     *
     * @param rule   the identifier of the rule being accepted
     * @param target the element it is accepted for
     * @param reason why the rule cannot decide here and what was measured instead
     * @throws IllegalArgumentException when any of the three says nothing
     */
    public AxeExemption {
        if (rule.isBlank() || target.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException(
                "An accepted undecidable names a rule, an element and the reason it was accepted"
            );
        }
    }

    /**
     * Answers whether the given finding is the one this exemption was written for.
     *
     * <p>The rule matches exactly and the element by containment, because a selector the rules report
     * carries the path it found the element by and a project names the part of it that identifies
     * what was measured.
     *
     * @param finding a finding the rules could not decide
     * @return whether this exemption covers it
     */
    public boolean covers(AxeFinding finding) {
        return this.rule.equals(finding.rule()) && finding.target().contains(this.target);
    }

    /**
     * Writes this exemption as one line of a report, for the case where it matched nothing.
     *
     * @return the exemption as one line, naming what it no longer excuses
     */
    public String stale() {
        return "accepted %s at %s no longer answers to anything, so remove it: %s"
            .formatted(this.rule, this.target, this.reason);
    }
}
