package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.IgnoreFixtures.GENERIC_BLOCK;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.GIT_IGNORE;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.INHERITED;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.offences;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An un-anchored {@code .gitignore} line that repeats a line of a {@code .gitignore} in a directory above is named
 * with the line it repeats, and an anchored line, a line a negation between them could reverse, and a line of a
 * sibling directory are not.
 */
class NestedIgnorePatternTest {

    private static final String NESTED = "web/.gitignore";

    private static List<String> nestedOffences(String name, String root, String nested) {
        return offences(new GitFixture(name).write(GIT_IGNORE, root).write(NESTED, nested));
    }

    @Test
    void namesAnUnanchoredLineAFileAboveAlreadyDeclares() {
        assertEquals(
            List.of("web/.gitignore:2: node_modules/ - repeats .gitignore:1" + INHERITED),
            nestedOffences("ignore-nested", "node_modules/\n", "*.cache\nnode_modules/\n")
        );
    }

    @Test
    void namesABareNameAFileAboveAlreadyDeclares() {
        assertEquals(
            List.of("web/.gitignore:1: coverage - repeats .gitignore:1" + INHERITED),
            nestedOffences("ignore-nested-bare", "coverage\n", "coverage\n")
        );
    }

    @Test
    void namesANegationAFileAboveAlreadyDeclares() {
        assertEquals(
            List.of("web/.gitignore:1: !keep.log - repeats .gitignore:2" + INHERITED),
            nestedOffences("ignore-nested-negated", "*.log\n!keep.log\n", "!keep.log\n")
        );
    }

    @Test
    void namesANestedRepeatOfAManagedLine() {
        assertEquals(
            List.of("web/.gitignore:1: *.log - repeats .gitignore:3 in the Airness managed generic block" + INHERITED),
            nestedOffences("ignore-managed-nested", GENERIC_BLOCK, "*.log\n")
        );
    }

    @Test
    void neverComparesAnAnchoredLineWithAFileAbove() {
        assertEquals(
            List.of(),
            nestedOffences("ignore-anchored", "/dist\nbuild/output\n**/cache\n", "/dist\nbuild/output\n**/cache\n")
        );
    }

    @Test
    void keepsARepeatANegationInTheFileAboveCouldReverse() {
        assertEquals(List.of(), nestedOffences("ignore-nested-negation", "*.log\n!audit.log\n", "*.log\n"));
    }

    @Test
    void keepsARepeatANegationEarlierInItsOwnFileCouldReverse() {
        assertEquals(List.of(), nestedOffences("ignore-nested-own-negation", "*.log\n", "!debug.log\n*.log\n"));
    }

    @Test
    void keepsARepeatANegationInADirectoryBetweenCouldReverse() {
        GitFixture fixture = new GitFixture("ignore-intermediate")
            .write(GIT_IGNORE, "tmp/\n")
            .write("app/.gitignore", "!tmp/\n")
            .write("app/web/.gitignore", "tmp/\n");
        assertEquals(List.of(), offences(fixture));
    }

    @Test
    void comparesALineOnlyWithTheFilesOfTheDirectoriesAboveIt() {
        GitFixture fixture = new GitFixture("ignore-siblings")
            .write("app/.gitignore", "cache/\n")
            .write("application/.gitignore", "cache/\n")
            .write(NESTED, "cache/\n");
        assertEquals(List.of(), offences(fixture));
    }
}
