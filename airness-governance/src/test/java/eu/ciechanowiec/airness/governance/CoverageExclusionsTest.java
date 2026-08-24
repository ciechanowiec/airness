package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A coverage exclusion names what a file is, and the shapes that name something else are refused.
 */
class CoverageExclusionsTest {

    @Test
    void refusesAnExclusionThatNamesOneClass() {
        assertEquals(
            List.of(
                "Rewrite child coverage exclusion \"com.example.cli.Example\"; exclude by file role such "
                    + "as *Command, never by naming a class"
            ),
            CoverageExclusions.problems("com.example.cli.Example").toList(),
            "a qualified name ending in a class is the form the standard refuses"
        );
    }

    @Test
    void refusesAnExclusionThatNamesALocationRatherThanARole() {
        assertFalse(
            CoverageExclusions.roleShaped("com.example.cli.*"),
            "a package is where a class sits rather than what it is"
        );
        assertFalse(CoverageExclusions.roleShaped("com.example.cli.**"), "and so is a package subtree");
    }

    @Test
    void acceptsASettingThatDeclaresNoExclusionAtAll() {
        assertEquals(
            List.of(), CoverageExclusions.problems("").toList(),
            "declaring no exclusion is the state a project should be in, not one to report"
        );
        assertEquals(
            List.of(), CoverageExclusions.problems("   ").toList(),
            "and whitespace declares no exclusion just as plainly"
        );
    }

    @Test
    void refusesAnEntryThatIsNothingAtAll() {
        assertEquals(
            1, CoverageExclusions.problems("*Command,,*Dto").count(),
            "a stray comma between two entries leaves an empty one, which names no role and is reported"
        );
    }

    @Test
    void acceptsAnExclusionShapedLikeARole() {
        assertEquals(
            List.of(), CoverageExclusions.problems("*Command,com.example.cli.*Dto").toList(),
            "a role reads across the packages it appears in, which is what the harness excludes by"
        );
        assertTrue(
            CoverageExclusions.roleShaped("*Generated*"),
            "and it needs no package in front of it at all"
        );
    }
}
