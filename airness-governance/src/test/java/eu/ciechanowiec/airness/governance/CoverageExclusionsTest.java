package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A coverage exclusion is a class pattern with the reason its role gives, and JaCoCo is handed the pattern alone.
 */
class CoverageExclusionsTest {

    private static final String DECLARED = """
        com.example.Application -- started only by the launcher, which the browser suite drives;
        com.example.cli.*Command -- each delegates to a tested service in one line
        """;

    @Test
    void handsJacocoThePatternsAlone() {
        assertEquals(
            "com.example.Application,com.example.cli.*Command", CoverageExclusions.jacocoPatterns(DECLARED),
            "the reason is prose for the reader, and a comma inside it separates nothing"
        );
    }

    @Test
    void acceptsEveryEntryThatGivesAReason() {
        assertEquals(List.of(), CoverageExclusions.unreasoned(DECLARED));
    }

    @Test
    void refusesAnEntryWithoutAReason() {
        List<String> problems = CoverageExclusions.unreasoned("com.example.Application; com.example.Shell --  ");
        assertEquals(2, problems.size(), "neither a bare pattern nor an empty reason gives one");
        assertTrue(problems.getFirst().contains("com.example.Application"), "the problem names the entry");
    }

    @Test
    void refusesAReasonWithoutAPattern() {
        assertEquals(1, CoverageExclusions.unreasoned("-- covered elsewhere").size());
        assertEquals(List.of(), CoverageExclusions.patterns("-- covered elsewhere"), "JaCoCo is handed nothing");
    }

    @Test
    void readsNothingFromAnEmptySetting() {
        assertEquals(List.of(), CoverageExclusions.entries("  "));
        assertEquals("", CoverageExclusions.jacocoPatterns(""));
    }
}
