package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The instruction-reference check resolves a backticked path against the tree and a backticked type name
 * against what the sources declare or the analysis configuration names, and throws rather than passing
 * when the instruction file itself is absent.
 */
class InstructionReferenceCheckTest {

    private static final Path INSTRUCTIONS = Path.of("AGENTS.md");
    private static final List<Path> CONFIGURATION = List.of(Path.of("config"));
    private static final List<Path> SOURCES = List.of(Path.of("src/main/java"));
    private static final String SOURCE = "src/main/java/sample/AlphaBeta.java";

    private static final String SOURCE_BODY = """
        package sample;

        class AlphaBeta {
        }
        """;

    private static final String RULESET = "<ruleset><rule name=\"NoThrowsInTestClass\"/></ruleset>\n";

    private static GitFixture repository(String name) {
        return new GitFixture(name).write(SOURCE, SOURCE_BODY).write("config/ruleset.xml", RULESET);
    }

    private static InstructionReferenceCheck check(Path root) {
        return new InstructionReferenceCheck(root, INSTRUCTIONS, CONFIGURATION, SOURCES);
    }

    @Test
    void resolvesAPathThatIsThereAndATypeTheSourcesDeclare() {
        Path root = repository("references-clean")
            .write("AGENTS.md", "See `src/main/java/sample/AlphaBeta.java` for `AlphaBeta`.\n")
            .root();
        assertTrue(Verdicts.clean(check(root).findings()), "both references resolve");
    }

    @Test
    void resolvesATypeNameOnlyTheAnalysisConfigurationHolds() {
        Path root = repository("references-configured")
            .write("AGENTS.md", "The custom rule `NoThrowsInTestClass` is what enforces it.\n")
            .root();
        assertTrue(
            Verdicts.clean(check(root).findings()),
            "a rule name is a name the instructions may honestly use, so the configuration counts as a source of names"
        );
    }

    @Test
    void reportsAPathTheRepositoryDoesNotHold() {
        Path root = repository("references-broken-path")
            .write("AGENTS.md", "See `src/main/java/sample/Missing.java` for the detail.\n")
            .root();
        assertEquals(
            List.of("src/main/java/sample/Missing.java"),
            Verdicts.offences(check(root).findings(), "Broken repository"),
            "a path under a real top-level directory is checked, and this one is not there"
        );
    }

    @Test
    void reportsATypeNameNothingInTheRepositoryAnswers() {
        Path root = repository("references-broken-type")
            .write("AGENTS.md", "The work is done by `NoSuchThing`, which was renamed.\n")
            .root();
        assertEquals(
            List.of("NoSuchThing"),
            Verdicts.offences(check(root).findings(), "names types"),
            "a name surviving only in the prose is what this rule is for"
        );
    }

    @Test
    void refusesToReportOnAnInstructionFileItCouldNotOpen() {
        Path root = repository("references-absent").root();
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> check(root).findings());
        assertTrue(
            thrown.getMessage().contains("AGENTS.md"),
            "a verdict of no offences would render as a pass over a document nobody opened"
        );
    }
}
