package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IgnoredBuildInputsTest {

    private static final String ROOT = "module/src/main/resources";
    private static final String MODULE = ROOT + "/build/pdf.mjs";
    private static final String KIND = "module main resource";
    private static final String VIEWER = "build/pdf.mjs";
    private static final String IGNORE = ".gitignore";

    @Test
    void rejectsAnIgnoredSelectedRuntimeModule() {
        Path root = new GitFixture("ignored-viewer").write(IGNORE, "build/\n")
            .write(MODULE, "Published viewer bytes\n").root();
        BuildInput input = new BuildInput(root.resolve(ROOT), KIND, Set.of(VIEWER));
        List<String> problems = new IgnoredBuildInputs(root, List.of(input), List.of(root.resolve("target")))
            .problems();
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains(KIND + ": ignored build input " + MODULE));
        assertTrue(problems.getFirst().contains("Track this input or generate it under a Maven build directory"));
    }

    @Test
    void acceptsExplicitlyTrackedFilesEvenWhenAnIgnorePatternMatches() {
        GitFixture fixture = new GitFixture("tracked-viewer").write(IGNORE, "build/\n")
            .write(MODULE, "Published viewer bytes\n");
        fixture.git("add", "-f", MODULE);
        Path root = fixture.root();
        BuildInput input = new BuildInput(root.resolve(ROOT), KIND, Set.of(VIEWER));
        assertTrue(new IgnoredBuildInputs(root, List.of(input), List.of()).problems().isEmpty());
    }

    @Test
    void acceptsNewUntrackedWorkAndHonorsTheBuildSelection() {
        Path root = new GitFixture("selected-inputs").write(IGNORE, "build/\n")
            .write(MODULE, "Excluded viewer\n").write(ROOT + "/page.html", "New page\n").root();
        BuildInput input = new BuildInput(root.resolve(ROOT), KIND, Set.of("page.html"));
        assertTrue(new IgnoredBuildInputs(root, List.of(input), List.of()).problems().isEmpty());
    }

    @Test
    void honorsLocalAndConfiguredGlobalIgnoreRules() {
        GitFixture fixture = new GitFixture("ignore-sources")
            .write(".git/info/exclude", "*.local\n").write("global-ignore", "*.machine\n")
            .write(ROOT + "/first.local", "Local\n").write(ROOT + "/second.machine", "Global\n");
        Path root = fixture.root();
        fixture.git("config", "core.excludesFile", root.resolve("global-ignore").toString());
        BuildInput input = new BuildInput(root.resolve(ROOT), KIND, Set.of("first.local", "second.machine"));
        assertEquals(2, new IgnoredBuildInputs(root, List.of(input), List.of()).problems().size());
    }

    @Test
    void excludesConfiguredBuildDirectoriesInsideAnInputRoot() {
        Path root = new GitFixture("generated-inputs").write(IGNORE, "output/\n")
            .write("output/Produced.java", "Generated\n").root();
        Path output = root.resolve("output");
        BuildInput broad = new BuildInput(root, "main Java", Set.of("output/Produced.java"));
        BuildInput generated = new BuildInput(output, "main Java", Set.of("Produced.java"));
        assertTrue(new IgnoredBuildInputs(root, List.of(broad, generated), List.of(output)).problems().isEmpty());
    }

    @Test
    void scopesAndDeduplicatesFindingsWithoutReadingUnrelatedIgnoredFiles() {
        Path root = new GitFixture("scoped-inputs").write(IGNORE, "build/\n")
            .write(MODULE, "Selected\n").write("other/build/private.txt", "Unrelated\n").root();
        BuildInput input = new BuildInput(root.resolve(ROOT), KIND, Set.of(VIEWER));
        BuildInput absent = new BuildInput(root.resolve("absent"), KIND, Set.of());
        BuildInput outside = new BuildInput(root.resolve("..").normalize(), KIND, Set.of("outside.java"));
        assertEquals(
            1, new IgnoredBuildInputs(
                root, List.of(
                    input, new BuildInput(input.directory(), "module test resource", input.selected()), absent, outside
                ), List.of()
            ).problems().size()
        );
    }

    @Test
    void failsWhenASelectedInputDirectoryDisappears() {
        Path root = new GitFixture("missing-input").root();
        BuildInput input = new BuildInput(root.resolve("absent"), KIND, Set.of("file.txt"));
        IgnoredBuildInputs check = new IgnoredBuildInputs(root, List.of(input), List.of());
        assertThrows(IllegalStateException.class, check::problems);
    }

    @Test
    void refusesAnIncorrectGitRootRatherThanReadingItsParent() {
        Path root = new GitFixture("wrong-input-root").write("child/file.txt", "Child\n").root();
        IgnoredBuildInputs check = new IgnoredBuildInputs(root.resolve("child"), List.of(), List.of());
        assertThrows(IllegalStateException.class, check::problems);
    }
}
