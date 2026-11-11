package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which declarations an error answers to.
 */
class ServerErrorTest {

    private static final String NAME = ServerErrorTest.class.getName();

    private static ServerError logged(String source) {
        return new ServerError(source, "failed", true);
    }

    @Test
    void answersToTheClassItsLoggerIsNamedFor() {
        assertTrue(logged(NAME).loggedBy(ServerErrorTest.class));
    }

    @Test
    void answersToTheClassALoggerIsNamedBeneathAfterADot() {
        assertTrue(logged(NAME + ".[component]").loggedBy(ServerErrorTest.class));
    }

    @Test
    void answersToTheClassANestedClassIsNamedBeneath() {
        assertTrue(logged(NAME + "$Nested").loggedBy(ServerErrorTest.class));
    }

    @Test
    void answersToNoClassWhoseNameMerelyPrefixesItsLogger() {
        assertFalse(logged(NAME + "s").loggedBy(ServerErrorTest.class));
        assertFalse(logged("eu.ciechanowiec").loggedBy(ServerErrorTest.class));
    }

    @Test
    void answersToNoClassWhenItEndedAThread() {
        assertFalse(new ServerError(NAME, "ended", false).loggedBy(ServerErrorTest.class));
    }

    @Test
    void describesItselfByItsSourceAndMessage() {
        assertEquals(NAME + ": failed", logged(NAME).described());
    }
}
