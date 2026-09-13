package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Whether an application can draw its own refusals for somebody who has no account.
 *
 * <p>A refusal does not leave an application the way an answer does. The container is told to draw
 * an error, which it does by dispatching the same request a second time, at the error address, and
 * the security chain covers that second dispatch as well as the first. An application that never
 * named that address therefore refuses its own refusal, and what the caller receives is whatever the
 * chain does with a stranger: on every ordinary chain, a redirect to the sign-in form.
 *
 * <p>The consequence is a lie rather than a failure. The application decided that something was not
 * found, and the caller is told to sign in, which is the wrong answer to a caller who has no account
 * and never needed one. Nothing in a test that authenticates can see it, and no reader of the source
 * can settle it either, so it arrives here as evidence of what the built chain decided.
 *
 * <p>An application that leaves that dispatch unfiltered has answered the question the other way and
 * is reported nothing, because the refusal it drew is the refusal its caller receives.
 *
 * <p>An application that admits a caller without an account to nothing at all is reported nothing
 * either. Such a caller reaches no mapping of it, so no refusal of its own is ever answered to one,
 * and the address those refusals would be drawn at decides nothing. This is why the rule reads the
 * open mappings beside the refusal: the two together are the shape that misleads somebody, and the
 * refusal alone is a property of every application that asks for an account at its front door.
 *
 * <p>What this does not reach is an application that opens a static prefix and maps nothing, where a
 * file that is not there is answered by the same redirect. That is the same defect answering a much
 * smaller question, and it is left unreported rather than reported by guessing, because a rule this
 * one cannot prove is a rule that teaches a reader to distrust the ones it can.
 */
@UtilityClass
final class SpringRefusalRules {

    private static final String CLOSED = "error-dispatch closed";

    private static final String OPENED = "open ";

    private static final String OFFENCE = "the error address: this chain admits a caller without an"
        + " account to a mapping of this application, and a refusal of that mapping leaves the"
        + " application as a second dispatch of the same request, to /error, which this chain covers"
        + " as well as the first. Every such refusal therefore becomes a redirect to the sign-in form"
        + " rather than the refusal the application decided on."
        + " Name it with requestMatchers(\"/error\").permitAll(), or leave that dispatch to the"
        + " application by naming spring.security.filter.dispatcher-types without the error one";

    /**
     * Whether this build admitted a caller without an account to a mapping and then refused them the
     * address its refusals are drawn at.
     *
     * @param recorded the evidence lines this build wrote
     * @return the one offence, and nothing where either half of that shape is absent
     */
    static List<String> undrawn(Collection<String> recorded) {
        boolean misleading = recorded.contains(CLOSED)
            && recorded.stream().anyMatch(line -> line.startsWith(OPENED));
        return misleading ? List.of(OFFENCE) : List.of();
    }
}
