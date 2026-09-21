package eu.ciechanowiec.airness.web;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * What one reading of one page came to, as the test that asked for it asserts.
 *
 * <p>Three things are a problem, and a test asserts that there are none of them. A rule that failed,
 * which is the one every project already read. A rule that could not decide and that no exemption
 * accounts for, which is the one no project read and which has hidden a contrast of one to one, a
 * component family, and a pill whose text sat outside its own background. And an exemption that
 * matched nothing, which is an excuse for something that is no longer there.
 *
 * <p>The three are answered as one list rather than three, because a test that had to assert three
 * lists would be a test that could assert two and pass.
 *
 * @param failed    the rules that decided the page was wrong
 * @param undecided the rules that could not tell
 * @param accepted  the undecidables the project has measured by hand and written down
 */
public record AxeVerdict(
    List<AxeFinding> failed, List<AxeFinding> undecided, List<AxeExemption> accepted
) {

    private static final String FAILED = "failed";

    private static final String UNDECIDED = "undecided";

    /**
     * Holds each list as one of its own, so the caller cannot change what this verdict says after it
     * has been handed over.
     *
     * @param failed    the rules that decided the page was wrong
     * @param undecided the rules that could not tell
     * @param accepted  the undecidables the project has measured by hand and written down
     */
    public AxeVerdict {
        failed = List.copyOf(failed);
        undecided = List.copyOf(undecided);
        accepted = List.copyOf(accepted);
    }

    /**
     * Everything wrong with the page, and everything wrong with what was written about the page.
     *
     * @return one line per problem, naming the rule, the element and what to do, and empty when the
     *         page breaks nothing and every exemption still answers to something
     */
    public List<String> problems() {
        return Stream.of(
            this.failed.stream().map(finding -> finding.worded(FAILED)),
            this.unaccounted().map(finding -> finding.worded(UNDECIDED)),
            this.stale().map(AxeExemption::stale)
        ).flatMap(lines -> lines).toList();
    }

    private Stream<AxeFinding> unaccounted() {
        return this.undecided.stream().filter(this::noneAccepts);
    }

    private Stream<AxeExemption> stale() {
        return this.accepted.stream().filter(this::nothingAnswers);
    }

    private boolean noneAccepts(AxeFinding finding) {
        return this.accepted.stream().noneMatch(finding::coveredBy);
    }

    private boolean nothingAnswers(AxeExemption exemption) {
        return this.undecided.stream().noneMatch(exemption::covers);
    }

    static AxeVerdict of(
        Collection<AxeFinding> failed, Collection<AxeFinding> undecided,
        Collection<AxeExemption> accepted
    ) {
        return new AxeVerdict(List.copyOf(failed), List.copyOf(undecided), List.copyOf(accepted));
    }
}
