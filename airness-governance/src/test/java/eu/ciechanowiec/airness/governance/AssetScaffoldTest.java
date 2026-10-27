package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetScaffoldTest {

    private static final String WORKFLOW = ".github/workflows/verify-full.yml";
    private static final String BODY = "name: Full verification\n";

    @TempDir
    private Path shipped;

    private AssetCatalogue catalogue() {
        return new AssetFixture(this.shipped, WORKFLOW + "\tSCAFFOLD\n")
            .ship(".github%2Fworkflows%2Fverify-full.yml", BODY).catalogue();
    }

    private AssetSync sync(Path root, String... unmanaged) {
        return new AssetSync(root, this.catalogue(), List.of(unmanaged), List.of(AssetSite.root(Set.of())));
    }

    @Test
    @SneakyThrows
    void initializesOnceAndPreservesProjectChanges() {
        Path root = new GitFixture("scaffold-initialize").root();
        assertEquals(List.of(WORKFLOW), this.sync(root).scaffold());
        assertEquals(BODY, Files.readString(root.resolve(WORKFLOW)));
        Files.writeString(root.resolve(WORKFLOW), "name: Project-owned workflow\n");
        assertTrue(this.sync(root).scaffold().isEmpty());
        assertEquals("name: Project-owned workflow\n", Files.readString(root.resolve(WORKFLOW)));
    }

    @Test
    @SneakyThrows
    void ordinarySyncAndVerificationKeepADeletedScaffoldAbsent() {
        Path root = new GitFixture("scaffold-delete").root();
        this.sync(root).scaffold();
        Files.delete(root.resolve(WORKFLOW));
        assertTrue(this.sync(root).write().isEmpty());
        assertFalse(Files.exists(root.resolve(WORKFLOW)));
        assertTrue(new AssetCheck(root, this.catalogue(), List.of()).findings().stream().allMatch(Findings::clean));
    }

    @Test
    void initializationHonoursProjectOwnershipAndNeverPinsScaffolds() {
        Path root = new GitFixture("scaffold-owned").root();
        assertTrue(this.sync(root, WORKFLOW).scaffold().isEmpty());
        assertFalse(Files.exists(root.resolve(WORKFLOW)));
        assertTrue(this.sync(root).pinned().isEmpty());
    }
}
