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
    void writesABracketInANameAsItIsBecauseTheLinterReadsItLiterally() {
        // The brackets are escaped in the rule, because a character class is what they mean to git. The
        // linter's patterns have no character class, and read a backslash as a path separator.
        Path root = ignoring("logs\\[old\\]/\n", "logs[old]/one.txt", "glob-syntax");
        assertTrue(patterns(root).contains("logs[old]"), "a bracket in a name is written as part of the name");
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
    void namesAFileAGeneratorOwnsAsThePathItIsRelativeToTheModule() {
        Path root = generatedFixture("generated-file");
        assertEquals(
            List.of("src/generated-types.ts"), generated(root, "web", "web/src/generated-types.ts"),
            "a generated file is left to its generator, named where the linter reads it"
        );
    }

    @Test
    void namesEveryFileABraceGlobMatchesBecauseTheLinterHasNoBraces() {
        Path root = generatedFixture("generated-braces");
        assertEquals(
            List.of("src/generated-schema.ts", "src/generated-types.ts"),
            generated(root, "web", "web/src/{generated-types,generated-schema}.ts"),
            "each alternative is matched here and written as the path it matched"
        );
    }

    @Test
    void namesTheFilesAGlobOpeningWithAWildcardMatchesInASubmodule() {
        Path root = generatedFixture("generated-wildcard-first");
        assertEquals(
            List.of("src/app/(admin)/admin/importMap.js"), generated(root, "web", "**/(admin)/**"),
            "a glob naming no module still reaches the module whose files it matches"
        );
    }

    @Test
    void namesNoFileAGeneratorOwnsInAnotherModule() {
        Path root = generatedFixture("generated-other-module");
        assertTrue(
            generated(root, "web", "other/src/gen.ts").isEmpty(),
            "another module's generated file is not this module's"
        );
        assertEquals(
            List.of("other/src/gen.ts"), generated(root, "", "other/src/gen.ts"),
            "the root module names a generated file anywhere in the tree"
        );
    }

    @Test
    void namesNoFileAGeneratedGlobMatchesNothingOf() {
        Path root = generatedFixture("generated-unmatched");
        assertTrue(generated(root, "web", "web/src/absent.ts").isEmpty(), "a glob is passed on only as matches");
    }

    @Test
    void leavesTheTrackedFilesOfTheModuleUnnamed() {
        Path root = ignoring(IGNORING_A_DIRECTORY, "scratch/notes.txt", "tracked-untouched");
        assertFalse(patterns(root).contains(".gitignore"), "a file git carries is not one it ignores");
    }

    private static List<String> patterns(Path root) {
        return new EditorconfigExcludes(root, root).patterns();
    }

    private static List<String> generated(Path root, String module, String glob) {
        return new EditorconfigExcludes(root, root.resolve(module), List.of(glob)).patterns();
    }

    private static Path generatedFixture(String fixture) {
        return new GitFixture("editorconfig-" + fixture)
            .write("web/src/generated-types.ts", "export {}")
            .write("web/src/generated-schema.ts", "export {}")
            .write("web/src/app/(admin)/admin/importMap.js", "export {}")
            .write("web/src/page.ts", "export {}\n")
            .write("other/src/gen.ts", "export {}")
            .commit("feat(core): add generated sources")
            .root();
    }

    private static Path ignoring(String rules, String ignoredFile, String fixture) {
        return new GitFixture("editorconfig-" + fixture)
            .write(".gitignore", rules)
            .write(ignoredFile, "Written by something that is not this project.\n")
            .commit("feat(core): record what this tree ignores")
            .root();
    }
}
