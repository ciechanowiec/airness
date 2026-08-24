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
            CoverageExclusions.problems("com/example/Example").toList(),
            "the separator is right here, so naming a class is the only thing left to report"
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

    @Test
    void refusesAPatternWrittenTheWayTheSourceWritesIt() {
        assertTrue(
            CoverageExclusions.problems("com.example.generated.*Dto")
                .anyMatch(problem -> problem.contains("separate packages with / rather than .")),
            "the coverage tool reads the VM name, so a dotted pattern excludes nothing and says nothing"
        );
    }

    @Test
    void refusesADottedPatternEvenWhenItIsShapedLikeARole() {
        assertFalse(
            CoverageExclusions.matchable("com.example.cli.*Command"),
            "a role that cannot match is a setting that reads as though it worked"
        );
        assertTrue(
            CoverageExclusions.roleShaped("com.example.cli.*Command"),
            "and its shape is why nothing else would have reported it"
        );
    }

    @Test
    void reportsBothDefectsWhenAnEntryCarriesBoth() {
        assertEquals(
            2, CoverageExclusions.problems("com.example.Example").count(),
            "naming a class and writing it with dots are two defects, and one edit answers both"
        );
    }

    @Test
    void acceptsTheSeparatorTheCoverageToolReads() {
        assertTrue(CoverageExclusions.matchable("**/*Mojo*"), "a role pattern carries no package at all");
        assertTrue(
            CoverageExclusions.matchable("com/example/generated/*Dto"),
            "and a rooted one separates its packages the way the VM name does"
        );
    }
}
