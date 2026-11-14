package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Layer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Payload stack's registration in this module: a module inheriting {@code airness-parent-payload} is a
 * Payload project, that parent names the version of {@code @airness/payload}, a new application installs that
 * package from the tarball {@code airness-payload-node} attaches, and {@code airness:new} writes the
 * {@code payload-cms} starter.
 *
 * <p>Kept apart from the generic tests, which hold no stack name, so that removing the stack removes this file
 * with it and leaves the generic coverage whole.
 */
class PayloadRegistrationTest {

    private static final String CMS = "cms";
    private static final String NODE_PROJECTS = "airness.node.projects";
    private static final String PACKAGE = "package.json";
    private static final String STARTER = "payload-cms";
    private static final String RELEASE = "9.8.7";
    private static final String SNAPSHOT = "9.8.8-SNAPSHOT";
    private static final StarterRequest REQUEST = StarterRequest.of(
        "org.sample", "content-site", Optional.empty(), Optional.empty()
    );

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

    @Test
    void servesThePayloadPackageBesideTheNodePackageToANewApplication() {
        Path repository = Path.of("repository");
        assertEquals(
            List.of(
                repository.resolve("eu/ciechanowiec/airness-node/9.8.8-SNAPSHOT/airness-node-9.8.8-SNAPSHOT.tgz"),
                repository.resolve(
                    "eu/ciechanowiec/airness-payload-node/9.8.8-SNAPSHOT/airness-payload-node-9.8.8-SNAPSHOT.tgz"
                )
            ),
            LocalHarnessRegistry.tarballs(
                repository, Set.of("@airness/payload", "@airness/node", "payload"), "9.8.8-SNAPSHOT"
            )
        );
    }

    @Test
    @SneakyThrows
    void writesThePayloadStarterUnderTheApplicationsNames() {
        try (StarterTemplate starter = StarterTemplate.open(PayloadRegistrationTest.class.getClassLoader(), STARTER)) {
            StarterCopy.write(starter.root(), this.directory, REQUEST, RELEASE);
        }

        String pom = Files.readString(this.directory.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>content-site</artifactId>"));
        assertTrue(pom.contains("<airness.package.root>org.sample.contentsite</airness.package.root>"));
        String manifest = Files.readString(this.directory.resolve(PACKAGE));
        assertTrue(manifest.contains("\"name\": \"content-site\""));
        assertTrue(manifest.contains("\"@airness/node\": \"9.8.7\""));
        assertTrue(manifest.contains("\"@airness/payload\": \"9.8.7\""));
        assertTrue(Files.readString(this.directory.resolve("compose.yaml")).contains("services:"));
        assertTrue(Files.readString(this.directory.resolve("AGENTS.md")).startsWith("## Content Site"));
        assertTrue(Files.isRegularFile(this.directory.resolve(".dockerignore")));
        assertFalse(pom.contains("npm-registry.py"), "a released harness installs from the public registry");
        assertFalse(Files.readString(this.directory.resolve(".gitignore")).contains(".npmrc"));
        assertFalse(Files.readString(this.directory.resolve("AGENTS.md")).contains("local npm registry"));
        try (Stream<Path> files = Files.walk(this.directory)) {
            assertEquals(
                List.of(), files.filter(Files::isRegularFile).filter(PayloadRegistrationTest::placeholder).toList(),
                "no file keeps a placeholder or a region marker"
            );
        }
    }

    @Test
    @SneakyThrows
    void servesTheSnapshotPayloadStarterFromTheLocalRegistry() {
        try (StarterTemplate starter = StarterTemplate.open(PayloadRegistrationTest.class.getClassLoader(), STARTER)) {
            StarterCopy.write(starter.root(), this.directory, REQUEST, SNAPSHOT);
        }

        String pom = Files.readString(this.directory.resolve("pom.xml"));
        assertTrue(pom.contains("npm-registry.py"));
        assertTrue(pom.contains("airness-payload-node-${project.parent.version}.tgz"));
        assertTrue(Files.readString(this.directory.resolve(".gitignore")).contains(".npmrc"));
        assertTrue(Files.readString(this.directory.resolve("AGENTS.md")).contains("local npm registry"));
        try (Stream<Path> files = Files.walk(this.directory)) {
            assertEquals(
                List.of(), files.filter(Files::isRegularFile).filter(PayloadRegistrationTest::placeholder).toList(),
                "no file keeps a placeholder or a region marker"
            );
        }
    }

    @SneakyThrows
    private static boolean placeholder(Path file) {
        // Read as ISO-8859-1, which decodes every byte, so a binary file reads too. The names are ASCII.
        String text = Files.readString(file, StandardCharsets.ISO_8859_1);
        return Stream.of("@AIRNESS_VERSION@", "@GROUP_ID@", "@ARTIFACT_ID@", "@PACKAGE@", "@SNAPSHOT_ONLY_", "blankus")
            .anyMatch(text::contains);
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
