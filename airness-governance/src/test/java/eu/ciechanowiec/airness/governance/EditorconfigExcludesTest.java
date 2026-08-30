package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class EditorconfigExcludesTest {

    private static final String IGNORING_A_DIRECTORY = "scratch/\n";

    @Test
    void namesADirectoryThatGitIsConfiguredNeverToCarry() {
        Path root = ignoring(IGNORING_A_DIRECTORY, "scratch/notes.txt", "in-directory");
        assertTrue(patterns(root).contains("scratch"), "the ignored directory is named");
    }

    @Test
    void namesEverythingBelowThatDirectoryAsWell() {
        Path root = ignoring(IGNORING_A_DIRECTORY, "scratch/notes.txt", "below-directory");
        assertTrue(patterns(root).contains("scratch/**"), "a glob naming a directory says nothing about it");
    }

    @Test
    void collapsesAnIgnoredDirectoryRatherThanNamingEveryFileInIt() {
        Path root = new GitFixture("editorconfig-collapsed")
            .write(".gitignore", IGNORING_A_DIRECTORY)
            .write("scratch/one.txt", "One.\n")
            .write("scratch/two.txt", "Two.\n")
            .write("scratch/deep/three.txt", "Three.\n")
            .commit("feat(core): ignore the scratch directory")
            .root();
        assertEquals(2, patterns(root).size(), "a whole ignored directory costs two patterns");
    }

    @Test
    void namesAnIgnoredFileThatSitsOnItsOwn() {
        Path root = ignoring("*.log\n", "build.log", "single-file");
        assertEquals(List.of("build.log"), patterns(root), "a file is named once and has nothing below it");
    }

    @Test
    void namesNothingWhenTheTreeIgnoresNothing() {
        Path root = new GitFixture("editorconfig-nothing-ignored")
            .write("src/main/java/Read.java", "class Read {}\n")
            .commit("feat(core): add a source the tree does not ignore")
            .root();
        assertTrue(patterns(root).isEmpty(), "a tree that ignores nothing excludes nothing");
    }

    @Test
    void writesAReadableDocumentEvenWhenTheTreeIgnoresNothing() {
        Path root = new GitFixture("editorconfig-empty-document")
            .write("src/main/java/Read.java", "class Read {}\n")
            .commit("feat(core): add a source the tree does not ignore")
            .root();
        assertTrue(
            new EditorconfigExcludes(root, root).document().startsWith("#"),
            "the linter is given a file it can read rather than none at all"
        );
    }

    @Test
    void escapesWhatAGlobWouldOtherwiseReadAsSyntax() {
        // The brackets are escaped in the rule as well, because a character class is what they mean to
        // git too, and an unescaped rule would ignore a directory called logso rather than this one.
        Path root = ignoring("logs\\[old\\]/\n", "logs[old]/one.txt", "glob-syntax");
        assertTrue(
            patterns(root).contains("logs\\[old\\]"),
            "a bracket in a name is part of the name rather than a character class"
        );
    }

    @Test
    void namesNothingThatSitsOutsideTheModuleBeingRead() {
        Path root = new GitFixture("editorconfig-other-module")
            .write(".gitignore", IGNORING_A_DIRECTORY)
            .write("elsewhere/scratch/notes.txt", "Notes.\n")
            .write("module/src/main/java/Read.java", "class Read {}\n")
            .commit("feat(core): ignore scratch directories anywhere")
            .root();
        assertTrue(
            new EditorconfigExcludes(root, root.resolve("module")).patterns().isEmpty(),
            "a module is told about its own ignored paths and no others"
        );
    }

    @Test
    void namesAnIgnoredPathOfTheModuleRelativeToTheModule() {
        Path root = new GitFixture("editorconfig-relative")
            .write(".gitignore", IGNORING_A_DIRECTORY)
            .write("module/scratch/notes.txt", "Notes.\n")
            .commit("feat(core): ignore scratch directories anywhere")
            .root();
        List<String> patterns = new EditorconfigExcludes(root, root.resolve("module")).patterns();
        assertTrue(patterns.contains("scratch"), "the linter matches a pattern against the module it reads");
    }

    @Test
    void leavesTheTrackedFilesOfTheModuleUnnamed() {
        Path root = ignoring(IGNORING_A_DIRECTORY, "scratch/notes.txt", "tracked-untouched");
        assertFalse(patterns(root).contains(".gitignore"), "a file git carries is not one it ignores");
    }

    private static List<String> patterns(Path root) {
        return new EditorconfigExcludes(root, root).patterns();
    }

    private static Path ignoring(String rules, String ignoredFile, String fixture) {
        return new GitFixture("editorconfig-" + fixture)
            .write(".gitignore", rules)
            .write(ignoredFile, "Written by something that is not this project.\n")
            .commit("feat(core): record what this tree ignores")
            .root();
    }
}
