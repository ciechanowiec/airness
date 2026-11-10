package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.Layer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sites that receive managed files come from the layers of every module in the repository,
 * read from every project file a commit would carry, whichever module the build started in.
 */
class RepositorySitesTest {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String FRONTEND = "shop/src/main/frontend";
    private static final String NODE_PROJECTS = "airness.node.projects";
    private static final String PACKAGE = "package.json";
    private static final String PACKAGE_BODY = "{}\n";
    private static final String CMS = "cms";
    private static final String WEB = "web";
    private static final String NEXT_MANIFEST = "{\"dependencies\": {\"next\": \"16.4.2\"}}\n";
    private static final String PAGE = "export default function Page() {}\n";

    @TempDir
    private Path directory;

    @Test
    void readsAMonorepoWithAnEmbeddedFrontendAndANodeModule() {
        Path root = this.monorepo();
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                List.of(
                    AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)),
                    new AssetSite(FRONTEND, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)),
                    new AssetSite(WEB, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
                ),
                sorted(RepositorySites.read(maven.builder(), maven.request(), root)),
                "the root carries Java and node although no root parent names them, and the frontend is a node project"
            );
        }
    }

    @Test
    void addsTheNextJsSiteToANodeProjectThatDeclaresNext() {
        Path root = this.monorepo();
        write(root.resolve(WEB).resolve(PACKAGE), NEXT_MANIFEST);
        write(root.resolve("web/src/app/page.tsx"), PAGE);
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                List.of(
                    AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)),
                    new AssetSite(FRONTEND, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)),
                    new AssetSite(WEB, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE)),
                    new AssetSite(WEB, AssetLocation.NEXT_PROJECT, Set.of(Layer.NODE))
                ),
                sorted(RepositorySites.read(maven.builder(), maven.request(), root)),
                "a node project that installs next and routes under src is also a site for the Next.js facet's files"
            );
        }
    }

    @Test
    @SneakyThrows
    void addsNoNextJsSiteWhereTheSeedWouldBeASecondHookOrUnread() {
        Path root = this.monorepo();
        write(root.resolve(WEB).resolve(PACKAGE), NEXT_MANIFEST);
        write(root.resolve("web/src/app/page.tsx"), PAGE);
        write(root.resolve("web/src/middleware.ts"), PAGE);
        write(root.resolve(FRONTEND).resolve(PACKAGE), "{\"peerDependencies\": {\"next\": \">=16\"}}\n");
        write(root.resolve(FRONTEND).resolve("src/app/page.tsx"), PAGE);
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            List<AssetSite> sites = RepositorySites.read(maven.builder(), maven.request(), root);
            assertEquals(
                List.of(),
                sites.stream().filter(site -> site.location() == AssetLocation.NEXT_PROJECT).toList(),
                "a middleware of the project's own, or next declared only as a peer, places no proxy seed"
            );
        }
        Files.delete(root.resolve("web/src/middleware.ts"));
        write(root.resolve("web/proxy.mjs"), PAGE);
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                List.of(),
                RepositorySites.read(maven.builder(), maven.request(), root).stream()
                    .filter(site -> site.location() == AssetLocation.NEXT_PROJECT)
                    .toList(),
                "a proxy at the project root is a hook of its own too"
            );
        }
    }

    @Test
    void agreesOnTheRootWhenNoAggregatorListsTheModules() {
        Path root = this.monorepo();
        write(
            root.resolve("pom.xml"), pom(
                "<groupId>com.example</groupId><artifactId>platform</artifactId>"
                    + "<version>1</version><packaging>pom</packaging>", ""
            )
        );
        try (ReportMavenFixture maven = new ReportMavenFixture(this.directory)) {
            assertEquals(
                AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)),
                RepositorySites.read(maven.builder(), maven.request(), root).getFirst(),
                "two builds sharing a root but no aggregator must demand the same root blocks"
            );
        }
    }

    @Test
    void readsTheAirnessBuildAsJavaAndANodeModuleAsANodeProject() {
        Path root = this.directory.resolve("self");
        MavenProject self = project(root.resolve("airness-assets"), "airness-assets", project(root, "airness", null));
        MavenProject node = project(root.resolve(CMS), CMS, null);
        node.getProperties().setProperty(NODE_PROJECTS, ".");
        write(root.resolve(CMS).resolve(PACKAGE), PACKAGE_BODY);
        assertEquals(
            List.of(
                AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)),
                new AssetSite(CMS, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
            ),
            RepositorySites.of(root, List.of(self, node))
        );
    }

    @Test
    void mergesTwoModulesListingOneDirectoryIntoOneSite() {
        Path root = this.directory.resolve("merged");
        MavenProject node = project(root.resolve(CMS), CMS, null);
        node.getProperties().setProperty(NODE_PROJECTS, ".");
        MavenProject plain = project(root.resolve("tools"), "tools", null);
        plain.getProperties().setProperty(NODE_PROJECTS, "../cms");
        write(root.resolve(CMS).resolve(PACKAGE), PACKAGE_BODY);
        assertEquals(
            List.of(
                AssetSite.root(Set.of(Layer.NODE)),
                new AssetSite(CMS, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
            ),
            RepositorySites.of(root, List.of(plain, node)),
            "one directory is one site, so it never receives two competing bodies for one file"
        );
    }

    @Test
    void passesOverHarnessParentsAndDirectoriesWithoutAPackage() {
        Path root = this.directory.resolve("passed");
        MavenProject parent = project(root.resolve("airness-parent-node"), "airness-parent-node", null);
        parent.getProperties().setProperty(NODE_PROJECTS, ".");
        write(root.resolve("airness-parent-node").resolve(PACKAGE), PACKAGE_BODY);
        MavenProject typo = project(root.resolve("shop"), "shop", null);
        typo.getProperties().setProperty(NODE_PROJECTS, "src/main/frontnd");
        assertEquals(
            List.of(AssetSite.root(Set.of())),
            RepositorySites.of(root, List.of(parent, typo)),
            "a parent's default speaks for its children, and a directory with no package.json gets no files"
        );
    }

    @Test
    void refusesANodeProjectOutsideTheRepository() {
        Path root = this.directory.resolve("bounded");
        MavenProject module = project(root, "bounded", null);
        module.getProperties().setProperty(NODE_PROJECTS, "../elsewhere");
        write(this.directory.resolve("elsewhere").resolve(PACKAGE), PACKAGE_BODY);
        List<MavenProject> modules = List.of(module);
        assertThrows(IllegalStateException.class, () -> RepositorySites.of(root, modules));
    }

    @SneakyThrows
    static void initializeGit(Path directory) {
        Path output = Files.createTempFile("airness-git", ".txt");
        try {
            ScannerCommand.run(List.of("git", "init", "--quiet", directory.toString()), output);
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(output.resolveSibling(output.getFileName() + ".log"));
        }
    }

    static List<AssetSite> sorted(List<AssetSite> sites) {
        return sites.stream().sorted(Comparator.comparing(AssetSite::directory)).toList();
    }

    private Path monorepo() {
        return monorepo(this.directory);
    }

    static Path monorepo(Path directory) {
        Path root = directory.resolve("platform");
        write(
            root.resolve("pom.xml"),
            pom(
                "<groupId>com.example</groupId><artifactId>platform</artifactId><version>1</version>"
                    + "<packaging>pom</packaging><modules><module>shop</module><module>web</module></modules>",
                ""
            )
        );
        write(
            root.resolve("parents/java/pom.xml"),
            pom(
                "<groupId>" + GROUP + "</groupId><artifactId>airness-parent-java</artifactId><version>1</version>"
                    + "<packaging>pom</packaging>",
                ""
            )
        );
        write(
            root.resolve("shop/pom.xml"),
            pom(
                "<parent><groupId>" + GROUP + "</groupId><artifactId>airness-parent-java</artifactId>"
                    + "<version>1</version><relativePath>../parents/java/pom.xml</relativePath></parent>"
                    + "<groupId>com.example</groupId><artifactId>shop</artifactId>",
                nodeProjects("src/main/frontend")
            )
        );
        write(root.resolve(FRONTEND).resolve(PACKAGE), PACKAGE_BODY);
        write(
            root.resolve("web/pom.xml"),
            pom(
                "<groupId>com.example</groupId><artifactId>web</artifactId><version>1</version>"
                    + "<packaging>pom</packaging>",
                nodeProjects(".")
            )
        );
        write(root.resolve(WEB).resolve(PACKAGE), PACKAGE_BODY);
        write(root.resolve("web/node_modules/dependency/pom.xml"), "not a project file\n");
        initializeGit(root);
        return root;
    }

    static String nodeProjects(String directories) {
        return "<properties><" + NODE_PROJECTS + ">" + directories + "</" + NODE_PROJECTS + "></properties>";
    }

    static String pom(String coordinates, String properties) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion>"
            + coordinates + properties + "</project>\n";
    }

    @SneakyThrows
    static MavenProject project(Path basedir, String artifact, @Nullable MavenProject parent) {
        Files.createDirectories(basedir);
        MavenProject project = new MavenProject();
        project.setGroupId(GROUP);
        project.setArtifactId(artifact);
        project.setFile(basedir.resolve("pom.xml").toFile());
        project.setParent(parent);
        return project;
    }

    @SneakyThrows
    static void write(Path file, String content) {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
