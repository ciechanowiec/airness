package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An application that admits a stranger to a mapping and then refuses the address its own refusals
 * are drawn at answers a redirect in place of every one of them.
 */
class SpringRefusalRulesTest {

    private static final String MAPPING = "open GET /api/orders/{id}";
    private static final String CLOSED = "error-dispatch closed";

    @Test
    void reportsAChainThatAdmitsAMappingAndRefusesThatAddress() {
        assertEquals(
            1, SpringRefusalRules.undrawn(List.of(MAPPING, CLOSED)).size(),
            "the refusal this application decided on is not the answer its caller receives"
        );
    }

    @Test
    void acceptsAChainThatDrawsItsOwnRefusals() {
        assertEquals(
            List.of(), SpringRefusalRules.undrawn(List.of(MAPPING, "error-dispatch open")),
            "an address the chain admits draws the refusal the application decided on"
        );
    }

    @Test
    void acceptsAnApplicationThatFiltersNoErrorDispatch() {
        assertEquals(
            List.of(), SpringRefusalRules.undrawn(List.of(MAPPING, "error-dispatch unfiltered")),
            "a dispatch the chain never covers needs no matcher naming it"
        );
    }

    @Test
    void passesOverAnApplicationThatAdmitsNobodyToAnything() {
        assertEquals(
            List.of(), SpringRefusalRules.undrawn(List.of(CLOSED)),
            "a caller who reaches no mapping is never answered a refusal of this application"
        );
    }

    @Test
    void reportsOnceWhereSeveralReadyContextsRecordedTheSameRefusal() {
        assertEquals(
            1, SpringRefusalRules.undrawn(List.of(MAPPING, CLOSED, CLOSED)).size(),
            "several contexts of one suite report the one thing to repair once"
        );
    }

    @Test
    void namesTheMatcherThatWouldDrawTheRefusal() {
        assertTrue(
            SpringRefusalRules.undrawn(List.of(MAPPING, CLOSED))
                .getFirst().contains("requestMatchers(\"/error\").permitAll()"),
            "the offence names the exact line a repair is written as"
        );
    }
}
