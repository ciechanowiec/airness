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
                "Rewrite child coverage exclusion \"com/example/Example\"; exclude by file role such as "
                    + "**/*Mojo*, never by naming a class"
            ),
            CoverageExclusions.problems("com/example/Example").toList()
        );
    }

    @Test
    void refusesAnExclusionThatNamesALocationRatherThanARole() {
        assertFalse(
            CoverageExclusions.roleShaped("com/example/**"),
            "a directory is where a class sits rather than what it is"
        );
        assertFalse(CoverageExclusions.roleShaped("com/example/*"), "and so is a single directory level");
    }

    @Test
    void refusesAnEntryThatIsNothingAtAll() {
        assertEquals(
            1, CoverageExclusions.problems("**/*Mojo*,,**/*Dto").count(),
            "a stray comma between two entries leaves an empty one, which names no role and is reported"
        );
        assertEquals(
            List.of(), CoverageExclusions.problems("**/*Mojo*,").toList(),
            "a trailing comma leaves nothing behind it, so there is no entry there to report"
        );
    }

    @Test
    void acceptsAnExclusionShapedLikeARole() {
        assertEquals(
            List.of(), CoverageExclusions.problems("**/*Mojo*,**/*Dto").toList(),
            "a role reads across the tree, which is what the harness excludes by"
        );
        assertTrue(CoverageExclusions.roleShaped("**/*Generated*"), "and it needs no directory at all");
    }
}
