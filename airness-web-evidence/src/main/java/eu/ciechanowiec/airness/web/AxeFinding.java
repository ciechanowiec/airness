package eu.ciechanowiec.airness.web;

/**
 * One thing an accessibility rule said about one element of a page.
 *
 * <p>The same shape carries a rule that failed and a rule that could not decide, because the two
 * differ in what is done about them rather than in what they are. Both name a rule, both name the
 * element it was asked about, and both carry the sentence that says what to do next.
 *
 * @param rule   the identifier of the rule, as the rules themselves publish it
 * @param target the element the rule was asked about, as a selector
 * @param detail what the rule said, which is the repair where it failed and the reason it could not
 *               decide where it could not
 */
public record AxeFinding(String rule, String target, String detail) {

    /**
     * Writes this finding as one line of a report, so a failure says which rule, which element and
     * what to do rather than only that something is wrong.
     *
     * @param verdict the word naming what the rule answered
     * @return the finding as one line
     */
    public String worded(String verdict) {
        return "%s %s at %s: %s".formatted(verdict, this.rule, this.target, this.detail);
    }

    /**
     * Answers whether this finding is the one the given exemption was written for.
     *
     * @param exemption an exemption a project declared
     * @return whether the exemption covers this finding
     */
    public boolean coveredBy(AxeExemption exemption) {
        return exemption.covers(this);
    }
}
