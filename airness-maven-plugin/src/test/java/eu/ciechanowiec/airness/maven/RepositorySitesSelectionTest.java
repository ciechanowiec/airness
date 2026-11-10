package eu.ciechanowiec.airness.maven;

import static eu.ciechanowiec.airness.maven.RepositorySitesTest.initializeGit;
import static eu.ciechanowiec.airness.maven.RepositorySitesTest.nodeProjects;
import static eu.ciechanowiec.airness.maven.RepositorySitesTest.pom;
import static eu.ciechanowiec.airness.maven.RepositorySitesTest.sorted;
import static eu.ciechanowiec.airness.maven.RepositorySitesTest.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The project files the sites are read from are the ones a commit would carry, as Git lists them, without the
 * ones that are data rather than modules, and a project file Maven cannot build stops nothing.
 */
class RepositorySitesSelectionTest {

    private static final String PACKAGE = "package.json";
    private static final String PACKAGE_BODY = "{}\n";
    private static final List<AssetSite> MONOREPO_SITES = List.of(
        AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)),
        new AssetSite("shop/src/main/frontend", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)),
        new AssetSite("web", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
    );

    @TempDir
    private Path directory;

    @Test
    void passesOverAProjectFileMavenCannotBuildRatherThanFailingTheRead() {
        Path root = RepositorySitesTest.monorepo(this.directory);
        write(root.resolve("docs/example/pom.xml"), "");
        write(
            root.resolve("docs/orphan/pom.xml"), pom(
                "<parent><groupId>com.example</groupId><artifactId>absent</artifactId><version>1</version></parent>"
                    + "<artifactId>orphan</artifactId>", nodeProjects(".")
            )
        );
        write(root.resolve("docs/orphan").resolve(PACKAGE), PACKAGE_BODY);
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                MONOREPO_SITES,
                sorted(RepositorySites.read(maven.builder(), maven.request(), root)),
                "a project file no build of this repository can read carries no stack and stops nothing"
            );
        }
    }

    @Test
    void readsOnlyTheProjectFilesACommitWouldCarry() {
        Path root = RepositorySitesTest.monorepo(this.directory);
        write(root.resolve(".gitignore"), "scratch/\n");
        write(root.resolve("scratch/pom.xml"), "");
        stray(root.resolve("scratch/checkout"));
        stray(root.resolve("nested"));
        initializeGit(root.resolve("nested"));
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                MONOREPO_SITES,
                sorted(RepositorySites.read(maven.builder(), maven.request(), root)),
                "an ignored scratch checkout and a repository nested in the clone, such as a worktree, are not modules"
            );
        }
    }

    @Test
    @SneakyThrows
    void passesOverAnIgnoredDirectoryNobodyMayRead() {
        Path root = RepositorySitesTest.monorepo(this.directory);
        write(root.resolve(".gitignore"), "data/\n");
        Path data = root.resolve("data");
        write(data.resolve("pom.xml"), "");
        Set<PosixFilePermission> readable = Files.getPosixFilePermissions(data);
        Files.setPosixFilePermissions(data, Set.of());
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                MONOREPO_SITES,
                sorted(RepositorySites.read(maven.builder(), maven.request(), root)),
                "a directory a container owns, which git ignores, is never opened"
            );
        } finally {
            Files.setPosixFilePermissions(data, readable);
        }
    }

    @Test
    void passesOverProjectFilesThatAreTemplatesOrResources() {
        Path root = RepositorySitesTest.monorepo(this.directory);
        write(
            root.resolve("shop/src/it/simple/pom.xml"),
            pom(
                "<groupId>@project.groupId@</groupId><artifactId>@project.artifactId@</artifactId>"
                    + "<version>@project.version@</version>", ""
            )
        );
        write(
            root.resolve("shop/src/main/resources/archetype-resources/pom.xml"),
            pom("<groupId>${groupId}</groupId><artifactId>${artifactId}</artifactId><version>${version}</version>", "")
        );
        stray(root.resolve("shop/src/test/resources/fixture"));
        stray(root.resolve("src/it/consumer"));
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                MONOREPO_SITES,
                sorted(RepositorySites.read(maven.builder(), maven.request(), root)),
                "an invoker template, an archetype and a test fixture are data, never modules of this repository"
            );
        }
    }

    @Test
    void refusesARootGitCannotList() {
        Path root = this.directory.resolve("unversioned");
        write(
            root.resolve("pom.xml"), pom(
                "<groupId>com.example</groupId><artifactId>unversioned</artifactId><version>1</version>", ""
            )
        );
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertThrows(
                IllegalStateException.class,
                () -> RepositorySites.read(maven.builder(), maven.request(), root),
                "outside a working tree there is no set of committable project files to read"
            );
        }
    }

    /**
     * A valid module that would be a site of its own if it were read: a node project with a package.json.
     */
    private static void stray(Path module) {
        write(
            module.resolve("pom.xml"), pom(
                "<groupId>com.example</groupId><artifactId>stray-" + module.getFileName() + "</artifactId>"
                    + "<version>1</version><packaging>pom</packaging>",
                nodeProjects(".")
            )
        );
        write(module.resolve(PACKAGE), PACKAGE_BODY);
    }
}
