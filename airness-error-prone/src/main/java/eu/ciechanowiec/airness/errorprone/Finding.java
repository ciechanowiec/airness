package eu.ciechanowiec.airness.errorprone;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * One form a rule may report, with whether it is present in the tree being read.
 *
 * @param present whether the form is present
 * @param message what the rule says about it
 */
record Finding(boolean present, String message) {

    /**
     * Picks the message of the first present form, in the order the rule lists them.
     *
     * @param findings the forms in order
     * @return the message of the first present form
     */
    static Optional<String> first(Finding... findings) {
        return Stream.of(findings).filter(Finding::present).map(Finding::message).findFirst();
    }
}
