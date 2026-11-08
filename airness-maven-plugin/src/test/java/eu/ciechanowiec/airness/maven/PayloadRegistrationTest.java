package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Layer;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Payload stack's registration in this module: a module inheriting {@code airness-parent-payload} is a
 * Payload project, and that parent names the version of {@code @airness/payload}.
 *
 * <p>Kept apart from the generic tests, which hold no stack name, so that removing the stack removes this file
 * with it and leaves the generic coverage whole.
 */
class PayloadRegistrationTest {

    private static final String CMS = "cms";
    private static final String NODE_PROJECTS = "airness.node.projects";
    private static final String PACKAGE = "package.json";

    @TempDir
    private Path directory;

    @Test
    void readsAPayloadModuleAsANodeAndPayloadProject() {
        Path root = this.directory.resolve("self");
        MavenProject self = RepositorySitesTest.project(
            root.resolve("airness-assets"), "airness-assets", RepositorySitesTest.project(root, "airness", null)
        );
        MavenProject payload = this.payloadModule(root);
        assertEquals(
            List.of(
                AssetSite.root(Set.of(Layer.JAVA, Layer.NODE, Layer.PAYLOAD)),
                new AssetSite(CMS, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE, Layer.PAYLOAD))
            ),
            RepositorySites.of(root, List.of(self, payload))
        );
    }

    @Test
    void mergesAPlainModuleListingThePayloadDirectoryIntoThePayloadSite() {
        Path root = this.directory.resolve("merged");
        MavenProject payload = this.payloadModule(root);
        MavenProject plain = RepositorySitesTest.project(root.resolve("tools"), "tools", null);
        plain.getProperties().setProperty(NODE_PROJECTS, "../cms");
        assertEquals(
            List.of(
                AssetSite.root(Set.of(Layer.NODE, Layer.PAYLOAD)),
                new AssetSite(CMS, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE, Layer.PAYLOAD))
            ),
            RepositorySites.of(root, List.of(plain, payload)),
            "one directory is one site, carrying the union of what every module listing it says it is"
        );
    }

    @Test
    void readsEachPackageFromItsOwnParentInTheChain() {
        MavenProject root = HarnessVersionsTest.project("airness-parent", "2.0.0");
        MavenProject node = HarnessVersionsTest.project("airness-parent-node", "2.0.0");
        node.setParent(root);
        MavenProject payload = HarnessVersionsTest.project("airness-parent-payload", "1.4.0");
        payload.setParent(node);
        MavenProject module = HarnessVersionsTest.project(CMS, "9.0.0");
        module.setParent(payload);
        assertEquals("@airness/payload=1.4.0,@airness/node=2.0.0", HarnessVersions.of(module).value());
    }

    @Test
    void holdsThePayloadPackageToItsParentRatherThanToItsLatestRelease() {
        assertTrue(HarnessVersions.harnessPackage("@airness/payload"));
    }

    private MavenProject payloadModule(Path root) {
        MavenProject payload = RepositorySitesTest.project(
            root.resolve(CMS), CMS, RepositorySitesTest.project(root.resolve("parent"), "airness-parent-payload", null)
        );
        payload.getProperties().setProperty(NODE_PROJECTS, ".");
        RepositorySitesTest.write(root.resolve(CMS).resolve(PACKAGE), "{}\n");
        return payload;
    }
}
