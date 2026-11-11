package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.IgnoreFixtures.GENERIC_BLOCK;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.GIT_IGNORE;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.SAME_FILE;
import static eu.ciechanowiec.airness.governance.IgnoreFixtures.offences;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An ignore line that repeats an earlier line of its own file is named with the line it repeats, and a line whose
 * removal could change what is ignored is not. {@link NestedIgnorePatternTest} covers the files of the directories
 * above.
 */
class IgnorePatternCheckTest {

    private static List<String> rootOffences(String name, String content) {
        return offences(new GitFixture(name).write(GIT_IGNORE, content));
    }

    @Test
    void namesALineThatRepeatsAnEarlierLineOfItsFile() {
        assertEquals(
            List.of(".gitignore:3: dist/ - repeats .gitignore:1" + SAME_FILE),
            rootOffences("ignore-same-file", "dist/\n*.tmp\ndist/\n")
        );
    }

    @Test
    void readsTheTrailingSpacesGitDropsAsNoPartOfThePattern() {
        assertEquals(
            List.of(".gitignore:2: dist/ - repeats .gitignore:1" + SAME_FILE),
            rootOffences("ignore-trailing-spaces", "dist/\ndist/   \n")
        );
    }

    @Test
    void keepsASpaceABackslashEscapesAsPartOfThePattern() {
        assertEquals(List.of(), rootOffences("ignore-escaped-space", "notes\\ \nnotes\n"));
    }

    @Test
    void keepsARepeatANegationBetweenThemCouldReverse() {
        assertEquals(List.of(), rootOffences("ignore-negation-between", "*.log\n!keep.log\n*.log\n"));
    }

    @Test
    void namesARepeatPastANegationThatReachesNothingItExcludes() {
        assertEquals(
            List.of(".gitignore:4: *.iml - repeats .gitignore:1" + SAME_FILE),
            rootOffences(
                "ignore-unreachable-negation", "*.iml\n!.vscode/settings.json\n!/.idea/runConfigurations\n*.iml\n"
            )
        );
    }

    @Test
    void keepsARepeatANegationBetweenThemMightReverse() {
        assertEquals(List.of(), rootOffences("ignore-glob-negation", "*.log\n!**/keep*\n*.log\n"));
    }

    @Test
    void namesARepeatedNegationPastAnExclusionThatCannotReachIt() {
        assertEquals(
            List.of(
                ".gitignore:4: !keep.log - repeats .gitignore:2" + SAME_FILE,
                ".gitignore:6: !other.log - repeats .gitignore:3" + SAME_FILE
            ),
            rootOffences("ignore-negations", "*.log\n!keep.log\n!other.log\n!keep.log\n*.tmp\n!other.log\n")
        );
    }

    @Test
    void keepsARepeatedNegationAnExclusionBetweenThemCouldReverse() {
        assertEquals(List.of(), rootOffences("ignore-negation-reversed", "!keep.log\n*.log\n!keep.log\n"));
    }

    @Test
    void passesOverCommentsAndBlankLines() {
        assertEquals(List.of(), rootOffences("ignore-comments", "# Build output\n\ntarget/\n\n# Build output\n"));
    }

    @Test
    void namesTheNearestEarlierDeclarationOfARepeatedLine() {
        assertEquals(
            List.of(
                ".gitignore:2: out/ - repeats .gitignore:1" + SAME_FILE,
                ".gitignore:3: out/ - repeats .gitignore:2" + SAME_FILE
            ),
            rootOffences("ignore-nearest", "out/\nout/\nout/\n")
        );
    }

    @Test
    void namesAProjectRepeatOfAManagedLine() {
        assertEquals(
            List.of(".gitignore:6: target/ - repeats .gitignore:2 in the Airness managed generic block" + SAME_FILE),
            rootOffences("ignore-managed", GENERIC_BLOCK + "\ntarget/\n")
        );
    }

    @Test
    void neverNamesALineOfAManagedBlock() {
        String repeated = GENERIC_BLOCK + "\n# BEGIN AIRNESS MANAGED: node\ntarget/\n# END AIRNESS MANAGED: node\n";
        IgnoreFile file = IgnoreFile.parse(GIT_IGNORE, repeated);
        assertEquals(1, IgnorePatternCheck.repeats(List.of(file), List.of()).size());
        assertEquals(List.of(), rootOffences("ignore-managed-repeat", repeated));
    }

    @Test
    void comparesADockerIgnoreFileWithItselfAlone() {
        GitFixture fixture = new GitFixture("ignore-docker")
            .write(".dockerignore", "node_modules/\n.env\n!.env.example\n.env\n")
            .write("web/.dockerignore", "node_modules/\n")
            .write("api.Dockerfile.dockerignore", "*.md\n*.md\n");
        assertEquals(
            List.of("api.Dockerfile.dockerignore:2: *.md - repeats api.Dockerfile.dockerignore:1" + SAME_FILE),
            offences(fixture)
        );
    }

    @Test
    void countsEveryIgnoreFileItRead() {
        Path root = new GitFixture("ignore-count")
            .write(GIT_IGNORE, "target/\n")
            .write("web/.gitignore", "dist/\n")
            .write(".dockerignore", "target/\n")
            .write("notes.txt", "target/\n")
            .root();
        assertEquals(3, new IgnorePatternCheck(root).scanned());
    }
}
