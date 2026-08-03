package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The asset check reports a file that drifted, a file that must not be there, an opt-out that has
 * stopped differing, and an opt-out naming nothing.
 *
 * <p>The last two are what stop the opt-out list rotting. Without them a list of exemptions outlives the
 * reasons for them, and a typo in it reads exactly like an exemption that works.
 */
class AssetCheckTest {

    private static final String EDITORCONFIG = ".editorconfig";

    private static final String MANIFEST = """
        # a comment the parser skips
        .editorconfig\tPINNED
        .gitignore\tSEED
        rewrite.yml\tFORBIDDEN
        """;

    private static final String CANONICAL = "root = true\n";

    @TempDir
    private Path shipped;

    private AssetCatalogue catalogue() {
        return new AssetFixture(this.shipped, MANIFEST)
            .ship(EDITORCONFIG, CANONICAL)
            .ship(".gitignore", "target/\n")
            .catalogue();
    }

    private List<Findings> findings(Path root, String... unmanaged) {
        return new AssetCheck(root, this.catalogue(), List.of(unmanaged)).findings();
    }

    @Test
    void readsThePolicyOfEveryManagedFile() {
        assertEquals(
            List.of(
                new ManagedAsset(EDITORCONFIG, AssetPolicy.PINNED),
                new ManagedAsset(".gitignore", AssetPolicy.SEED),
                new ManagedAsset("rewrite.yml", AssetPolicy.FORBIDDEN)
            ),
            this.catalogue().assets(),
            "the manifest states a path and a policy, and the comment line is not one of them"
        );
    }

    @Test
    void rejectsAPathThatEscapesTheRepository() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ManagedAsset("../outside", AssetPolicy.PINNED),
            "a lexical parent at the start remains unchanged by normalize(), so it needs an explicit guard"
        );
    }

    @Test
    void passesOverATreeThatMatchesWhatIsShipped() {
        Path root = new GitFixture("assets-clean").write(EDITORCONFIG, CANONICAL).root();
        assertTrue(
            Verdicts.clean(this.findings(root)),
            "the pinned file matches, the seed is unchecked, and the forbidden one is not there"
        );
    }

    @Test
    void reportsAPinnedFileThatDrifted() {
        Path root = new GitFixture("assets-drifted").write(EDITORCONFIG, "root = false\n").root();
        assertEquals(
            1, Verdicts.offences(this.findings(root), "changed or is missing").size(),
            "a file the harness owns is the bytes it ships, and one byte is the whole difference"
        );
    }

    @Test
    void reportsAPinnedFileThatIsNotThere() {
        Path root = new GitFixture("assets-absent").write("README.md", "content\n").root();
        assertEquals(
            1, Verdicts.offences(this.findings(root), "changed or is missing").size(),
            "absent and drifted are one finding, since the remedy for both is the same goal"
        );
    }

    @Test
    void reportsAForbiddenFileThatIsInTheTree() {
        Path root = new GitFixture("assets-forbidden")
            .write(EDITORCONFIG, CANONICAL)
            .write("rewrite.yml", "type: specs.openrewrite.org/v1beta/recipe\n")
            .root();
        assertEquals(
            1, Verdicts.offences(this.findings(root), "must not be in the tree").size(),
            "a copy nothing reads looks authoritative, which is worse than no copy"
        );
    }

    @Test
    void leavesAnOptedOutPathAlone() {
        Path root = new GitFixture("assets-optout").write(EDITORCONFIG, "root = false\n").root();
        assertTrue(
            Verdicts.clean(this.findings(root, EDITORCONFIG)),
            "a project with a reason to differ says so once, in the pom"
        );
    }

    @Test
    void reportsAnOptOutThatNoLongerDiffers() {
        Path root = new GitFixture("assets-settled").write(EDITORCONFIG, CANONICAL).root();
        assertEquals(
            List.of(EDITORCONFIG), Verdicts.offences(this.findings(root, EDITORCONFIG), "no longer differ"),
            "an exemption nobody needs is the first step towards a list nobody reads"
        );
    }

    @Test
    void reportsAnOptOutNamingAPathTheHarnessDoesNotOwn() {
        Path root = new GitFixture("assets-typo").write(EDITORCONFIG, CANONICAL).root();
        assertEquals(
            List.of("no-such-file"), Verdicts.offences(this.findings(root, "no-such-file"), "does not own"),
            "a typo in an exemption list reads exactly like an exemption that works"
        );
    }
}
