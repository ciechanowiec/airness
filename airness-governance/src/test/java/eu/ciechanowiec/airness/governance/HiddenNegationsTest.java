package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.IgnoreFixtures.GENERIC_BLOCK;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.GIT_IGNORE;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.hidden;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A {@code .gitignore} negation beneath a directory an exclusion hides is named with that exclusion, and a negation
 * git still applies, or one whose directory can't be decided, is not.
 */
class HiddenNegationsTest {

    private static final String HIDDEN = ", and git never looks inside an excluded directory; ";

    private static List<String> rootHidden(String name, String content) {
        return hidden(new GitFixture(name).write(GIT_IGNORE, content));
    }

    private static List<String> spelled(String file, String negation) {
        return HiddenNegations.spelledDirectories(new IgnorePattern(file, 1, negation, ""));
    }

    @Test
    void namesANegationBeneathAnExcludedDirectory() {
        assertEquals(
            List.of(
                ".gitignore:2: !.vscode/settings.json - .gitignore:1 excludes the directory .vscode" + HIDDEN
                    + "exclude the directory's contents (.vscode/*) instead of the directory, or remove this line"
            ),
            rootHidden("hidden-directory", ".vscode/\n!.vscode/settings.json\n")
        );
    }

    @Test
    void keepsANegationThatAnExclusionOfTheContentsLeavesWorking() {
        assertEquals(List.of(), rootHidden("hidden-contents", ".vscode/*\n!.vscode/settings.json\n"));
    }

    @Test
    void keepsANegationWhoseDirectoryIsReIncludedFirst() {
        assertEquals(List.of(), rootHidden("hidden-reincluded", "build/\n!build/\n!build/keep.txt\n"));
    }

    @Test
    void offersOnlyRemovalWhenTheExclusionIsManaged() {
        assertEquals(
            List.of(
                ".gitignore:6: !target/keep.txt - .gitignore:2 in the Airness managed generic block excludes the "
                    + "directory target" + HIDDEN + "remove this line"
            ),
            rootHidden("hidden-managed", GENERIC_BLOCK + "\n!target/keep.txt\n")
        );
    }

    @Test
    void neverNamesAManagedNegation() {
        String block = "# BEGIN AIRNESS MANAGED: generic\ntarget/\n!target/keep.txt\n# END AIRNESS MANAGED: generic\n";
        assertEquals(List.of(), rootHidden("hidden-managed-negation", block));
    }

    @Test
    void judgesTheDirectoryByTheFilesAboveIt() {
        GitFixture fixture = new GitFixture("hidden-nested")
            .write(GIT_IGNORE, "!web/dist/keep.js\n")
            .write("web/.gitignore", "dist/\n");
        assertEquals(
            List.of(
                ".gitignore:1: !web/dist/keep.js - web/.gitignore:1 excludes the directory web/dist" + HIDDEN
                    + "exclude the directory's contents (web/dist/*) instead of the directory, or remove this line"
            ),
            hidden(fixture)
        );
    }

    @Test
    void leavesADirectoryUnjudgedWhenAPatternItCannotReadDecidesFirst() {
        assertEquals(List.of(), rootHidden("hidden-undecided", ".vscode/\n[[:alpha:]]x\n!.vscode/settings.json\n"));
    }

    @Test
    void judgesOnlyTheDirectoriesANegationSpellsOut() {
        assertAll(
            () -> assertEquals(List.of("web/a", "web/a/b"), spelled("web/.gitignore", "!a/b/c.txt")),
            () -> assertEquals(List.of("a"), spelled(GIT_IGNORE, "!/a/*/c.txt")),
            () -> assertEquals(List.of(), spelled(GIT_IGNORE, "!keep.log")),
            () -> assertEquals(List.of(), spelled(GIT_IGNORE, "!/a/")),
            () -> assertEquals(List.of(), spelled(GIT_IGNORE, "!**/keep/x"))
        );
    }
}
