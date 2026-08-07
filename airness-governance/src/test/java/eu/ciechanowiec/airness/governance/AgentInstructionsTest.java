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
        Path root = new GitFixture("instructions-prepend").write("AGENTS.md", "# Project\n\nKeep this.\n").root();
        assertTrue(new AgentInstructions(root, CANONICAL).write());
        assertEquals(CANONICAL + "\n# Project\n\nKeep this.\n", read(root));
    }

    @Test
    void refreshesOnlyAValidManagedBlock() {
        String project = "# Project\n\nKeep this.\n";
        Path root = new GitFixture("instructions-refresh")
            .write("AGENTS.md", CANONICAL.replace("Current", "Old") + project)
            .root();
        assertTrue(new AgentInstructions(root, CANONICAL).write());
        assertEquals(CANONICAL + project, read(root));
    }

    @Test
    void leavesCurrentInstructionsUntouched() {
        Path root = new GitFixture("instructions-current").write("AGENTS.md", CANONICAL).root();
        assertFalse(new AgentInstructions(root, CANONICAL).write());
    }

    @Test
    void refusesDuplicateMarkersWithoutWriting() {
        String malformed = CANONICAL + CANONICAL;
        Path root = new GitFixture("instructions-duplicate").write("AGENTS.md", malformed).root();
        assertThrows(IllegalStateException.class, () -> new AgentInstructions(root, CANONICAL).write());
        assertEquals(malformed, read(root), "ambiguous project content remains untouched");
    }

    @Test
    @SneakyThrows
    void refusesToWriteThroughASymbolicLink() {
        Path root = new GitFixture("instructions-symlink").root();
        Path outside = this.temporary.resolve("outside");
        Files.writeString(outside, "must stay untouched\n");
        Files.createSymbolicLink(root.resolve("AGENTS.md"), outside);
        assertThrows(IllegalStateException.class, () -> new AgentInstructions(root, CANONICAL).write());
        assertEquals("must stay untouched\n", Files.readString(outside));
    }

    @SneakyThrows
    private static String read(Path root) {
        return Files.readString(root.resolve("AGENTS.md"));
    }
}
