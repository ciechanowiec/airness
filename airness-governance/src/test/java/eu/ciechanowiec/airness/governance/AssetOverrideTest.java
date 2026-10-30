package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every path has one owner per location, a capability replaces a file only by naming the one it replaces,
 * and any other disagreement between manifests is refused when they are read.
 *
 * <p>A refusal here is a harness bug caught by the harness's own tests. Were two claims resolved by
 * order instead, which body a consumer received would depend on which jar the classpath listed first.
 * Every refusal below is decided before whether one capability refines another, so a second manifest of
 * any capability exercises it.
 */
class AssetOverrideTest {

    private static final String ESLINT = "eslint.config.mjs";
    private static final String NODE_ROW = "eslint.config.mjs\tPINNED\tNODE_PROJECT\n";

    @TempDir
    private Path directory;

    private AssetFixture fixture(String node, String java) {
        return new AssetFixture(this.directory, ".editorconfig\tPINNED\n")
            .manifest(Capability.NODE, node)
            .manifest(Capability.JAVA, java);
    }

    private static String message(AssetFixture fixture) {
        return String.valueOf(assertThrows(IllegalStateException.class, fixture::catalogue).getMessage());
    }

    @Test
    void readsTheLocationOfARowAndTheRootWhenNoneIsStated() {
        AssetCatalogue catalogue = this.fixture(NODE_ROW, "").catalogue();
        assertEquals(
            List.of(
                new ManagedAsset(".editorconfig", AssetPolicy.PINNED),
                new ManagedAsset(ESLINT, AssetPolicy.PINNED, Capability.NODE, AssetLocation.NODE_PROJECT)
            ),
            catalogue.assets(),
            "a row without a location is a repository-root file of the capability whose manifest holds it"
        );
    }

    @Test
    void rejectsTwoCapabilitiesClaimingOneFileWithoutAnOverride() {
        String refusal = message(this.fixture(NODE_ROW, NODE_ROW));
        assertTrue(refusal.contains("[java, node]"), refusal);
    }

    @Test
    void rejectsAnOverrideOfAPathTheNamedCapabilityDoesNotOwn() {
        String refusal = message(this.fixture("", "eslint.config.mjs\tPINNED\tNODE_PROJECT\tOVERRIDES node\n"));
        assertTrue(refusal.contains("which does not own it"), refusal);
    }

    @Test
    void rejectsAnOverrideByACapabilityThatIsNotMoreSpecific() {
        AssetFixture fixture = new AssetFixture(this.directory, ".editorconfig\tPINNED\n")
            .manifest(Capability.NODE, ".editorconfig\tPINNED\tROOT\tOVERRIDES generic\n");
        String refusal = message(fixture);
        assertTrue(refusal.contains("node is not a more specific form of generic"), refusal);
    }

    @Test
    void rejectsAnOverrideOfASharedBlock() {
        AssetFixture fixture = this.fixture("AGENTS.md\tSECTION\n", "AGENTS.md\tSECTION\tROOT\tOVERRIDES node\n");
        String refusal = message(fixture);
        assertTrue(refusal.contains("shared through blocks"), refusal);
    }

    @Test
    void rejectsAPathClaimedBothAsABlockAndAsAWholeFile() {
        String refusal = message(this.fixture(".gitignore\tSECTION\n", ".gitignore\tPINNED\tROOT\tOVERRIDES node\n"));
        assertTrue(refusal.contains("both as a shared block and as a whole file"), refusal);
    }

    @Test
    void rejectsOneCapabilityClaimingAPathTwice() {
        String refusal = message(this.fixture(NODE_ROW + NODE_ROW, ""));
        assertTrue(refusal.contains("claimed twice by one capability"), refusal);
    }

    @Test
    void sharesARootFileBetweenCapabilitiesThroughBlocks() {
        AssetCatalogue catalogue = new AssetFixture(this.directory, ".gitignore\tSECTION\n")
            .manifest(Capability.NODE, ".gitignore\tSECTION\n")
            .catalogue();
        assertEquals(
            List.of(Capability.GENERIC, Capability.NODE),
            catalogue.select(AssetLocation.ROOT, Set.of(Capability.GENERIC, Capability.NODE)).stream()
                .map(ManagedAsset::owner)
                .toList(),
            "two capabilities each own a block of one file, which is not a conflict"
        );
    }
}
