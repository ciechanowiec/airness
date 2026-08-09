package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies safe partial ownership of the root instruction file.
 */
class AgentInstructionsTest {

    private static final String FILE = "AGENTS.md";
    private static final String CANONICAL = """
        <!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->
        Current rules.
        <!-- END AIRNESS MANAGED INSTRUCTIONS -->
        """;

    @TempDir
    private Path temporary;

    @Test
    void createsAMissingInstructionFile() {
        Path root = new GitFixture("instructions-create").root();
        assertTrue(new AgentInstructions(root, CANONICAL).write());
        assertEquals(CANONICAL, read(root));
    }

    @Test
    void prependsTheBlockAndPreservesProjectInstructions() {
        Path root = new GitFixture("instructions-prepend").write(FILE, "# Project\n\nKeep this.\n").root();
        assertTrue(new AgentInstructions(root, CANONICAL).write());
        assertEquals(CANONICAL + "\n# Project\n\nKeep this.\n", read(root));
    }

    @Test
    void refreshesOnlyAValidManagedBlock() {
        String project = "# Project\n\nKeep this.\n";
        Path root = new GitFixture("instructions-refresh")
            .write(FILE, CANONICAL.replace("Current", "Old") + project)
            .root();
        assertTrue(new AgentInstructions(root, CANONICAL).write());
        assertEquals(CANONICAL + project, read(root));
    }

    @Test
    void leavesCurrentInstructionsUntouched() {
        Path root = new GitFixture("instructions-current").write(FILE, CANONICAL).root();
        assertFalse(new AgentInstructions(root, CANONICAL).write());
    }

    @Test
    void refusesDuplicateMarkersWithoutWriting() {
        String malformed = CANONICAL + CANONICAL;
        Path root = new GitFixture("instructions-duplicate").write(FILE, malformed).root();
        assertThrows(IllegalStateException.class, () -> new AgentInstructions(root, CANONICAL).write());
        assertEquals(malformed, read(root), "ambiguous project content remains untouched");
    }

    @Test
    void rejectsCanonicalBlocksMissingEitherRequiredBoundary() {
        Path root = new GitFixture("instructions-canonical").root();
        assertThrows(IllegalArgumentException.class, () -> new AgentInstructions(root, "rules\n"));
        assertThrows(
            IllegalArgumentException.class,
            () -> new AgentInstructions(root, AgentInstructions.BEGIN + "\nrules\n")
        );
    }

    @Test
    void recognizesMalformedNonLeadingAndUnterminatedMarkers() {
        AgentInstructions instructions = new AgentInstructions(
            new GitFixture("instructions-malformed").root(), CANONICAL
        );
        assertTrue(instructions.malformed("prefix\n" + CANONICAL));
        assertTrue(instructions.malformed(AgentInstructions.BEGIN + "\nrules\n"));
        assertTrue(
            instructions.malformed(
                AgentInstructions.BEGIN + "\nrules\n" + AgentInstructions.END + "junk"
            )
        );
        assertFalse(instructions.malformed("# Project only\n"));
    }

    @Test
    void refreshesABlockWhoseEndMarkerHasNoFollowingNewline() {
        String old = CANONICAL.replace("Current", "Old").stripTrailing();
        Path root = new GitFixture("instructions-no-newline").write(FILE, old).root();
        assertTrue(new AgentInstructions(root, CANONICAL).write());
        assertEquals(CANONICAL, read(root));
    }

    @Test
    @SneakyThrows
    void refusesToWriteThroughASymbolicLink() {
        Path root = new GitFixture("instructions-symlink").root();
        Path outside = this.temporary.resolve("outside");
        Files.writeString(outside, "must stay untouched\n");
        Files.createSymbolicLink(root.resolve(FILE), outside);
        assertThrows(IllegalStateException.class, () -> new AgentInstructions(root, CANONICAL).write());
        assertEquals("must stay untouched\n", Files.readString(outside));
    }

    @SneakyThrows
    private static String read(Path root) {
        return Files.readString(root.resolve(FILE));
    }
}
