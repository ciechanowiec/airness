package eu.ciechanowiec.airness.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The rules are found on the test classpath by the version the archive itself names, rather than by a
 * version written into this project.
 */
class AxeLibraryTest {

    // Comfortably under the size of the rules and far above any prefix of them, so the assertion
    // says the whole library was read rather than the first block of it.
    private static final int SUBSTANTIAL = 100_000;

    @Test
    void readsTheVersionOutOfTheArchiveRatherThanOutOfAConstant() {
        assertTrue(
            AxeLibrary.version().matches("\\d+\\.\\d+\\.\\d+"),
            "the archive on the classpath names its own version, and that is the one used"
        );
    }

    @Test
    void answersTheWholeOfTheRulesAsAScript() {
        String rules = AxeLibrary.rules();
        assertTrue(rules.contains("axe"), "what came back is the rules rather than some other resource");
        assertTrue(
            rules.length() > SUBSTANTIAL,
            "the whole library is read rather than a prefix of it, which a browser could not run"
        );
    }

    @Test
    void locatesTheScriptUnderTheVersionItRead() {
        String first = AxeLibrary.rules();
        assertEquals(
            first.length(), AxeLibrary.rules().length(),
            "two readings of one classpath answer the same script"
        );
    }

    @Test
    void saysWhatToDeclareWhenTheRulesAreNotOnTheClasspathAtAll() {
        IllegalStateException refused = assertThrows(
            IllegalStateException.class,
            () -> AxeLibrary.stream("META-INF/resources/webjars/axe-core/0.0.0/axe.min.js"),
            "rules nobody declared are a missing dependency rather than a clean page"
        );
        assertEquals(
            "The accessibility rules are not on the test classpath. Declare org.webjars.npm:axe-core "
                + "at test scope, which the Spring parent already supplies: "
                + "META-INF/resources/webjars/axe-core/0.0.0/axe.min.js",
            refused.getMessage(), "and the refusal names what to declare"
        );
    }

    @Test
    void refusesAnArchiveThatNamesNoVersion() {
        IllegalStateException refused = assertThrows(
            IllegalStateException.class,
            () -> AxeLibrary.named(new Properties()),
            "an archive naming no version publishes a script nothing can locate"
        );
        assertEquals(
            "The accessibility rules are on the test classpath and name no version, so the script "
                + "they publish cannot be located",
            refused.getMessage(), "and the refusal tells that apart from the rules being absent"
        );
    }

    @Test
    void refusesAnArchiveWhoseVersionSaysNothing() {
        Properties blank = new Properties();
        blank.setProperty("version", " ");
        assertThrows(
            IllegalStateException.class,
            () -> AxeLibrary.named(blank),
            "a version of whitespace names no directory either"
        );
    }
}
