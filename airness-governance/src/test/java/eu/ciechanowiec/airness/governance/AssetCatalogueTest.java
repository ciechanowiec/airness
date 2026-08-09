package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers malformed and incomplete asset distributions as fail-closed catalogue inputs.
 */
class AssetCatalogueTest {

    @TempDir
    private Path directory;

    @Test
    void rejectsAClasspathWithoutAManifest() {
        URL[] empty = {};
        assertThrows(
            IllegalStateException.class,
            () -> new AssetCatalogue(new URLClassLoader(empty, null))
        );
    }

    @Test
    void rejectsAManifestEntryWithoutExactlyTwoFields() {
        AssetFixture fixture = new AssetFixture(this.directory, "only-a-path\n");
        assertThrows(IllegalStateException.class, fixture::catalogue);
    }
}
