package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reads a check's verdicts the way a test wants to ask about them.
 *
 * <p>A check answers with one {@link Findings} per rule, so a test that asserted on the list as a whole
 * would pass whenever any rule fired rather than the one it meant. Selecting by headline fragment keeps
 * each assertion pinned to a single rule, and a renamed headline fails the test that names it, which is
 * the reminder that a message a build prints has just changed.
 */
@UtilityClass
final class Verdicts {

    /**
     * Whether every rule the check answered found nothing.
     *
     * @param findings the check's verdicts
     * @return whether all of them are clean
     */
    static boolean clean(Collection<Findings> findings) {
        return findings.stream().allMatch(Findings::clean);
    }

    /**
     * The offences of the one rule whose headline contains {@code fragment}.
     *
     * @param findings the check's verdicts
     * @param fragment a distinguishing part of the headline of the rule asked about
     * @return that rule's offences
     */
    static List<String> offences(Collection<Findings> findings, CharSequence fragment) {
        return findings.stream()
            .filter(verdict -> verdict.headline().contains(fragment))
            .map(Findings::offences)
            .reduce(
                (_, _) -> {
                    throw new IllegalStateException("More than one verdict matches " + fragment);
                }
            )
            .orElseThrow(() -> new IllegalStateException("No verdict matches " + fragment));
    }
}
