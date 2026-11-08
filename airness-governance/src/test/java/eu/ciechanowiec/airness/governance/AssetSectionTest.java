package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared files lead with one block per layer present where they live, the root carries the blocks
 * of every layer in the repository, a node project carries its own, and every project line survives
 * a sync.
 */
class AssetSectionTest {

    private static final String AGENTS = "AGENTS.md";
    private static final String GITIGNORE = ".gitignore";
    private static final String FRONTEND = "shop/src/main/frontend";
    private static final String DRIFTED = "changed or is missing";
    private static final String GENERIC_BLOCK = """
        # BEGIN AIRNESS MANAGED: generic
        .idea/
        # END AIRNESS MANAGED: generic
        """;
    private static final String JAVA_BLOCK = """
        # BEGIN AIRNESS MANAGED: java
        *.class
        # END AIRNESS MANAGED: java
        """;
    private static final String NODE_BLOCK = """
        # BEGIN AIRNESS MANAGED: node
        node_modules/
        # END AIRNESS MANAGED: node
        """;
    private static final String NODE_AGENTS = """
        <!-- BEGIN AIRNESS MANAGED: node -->
        Node rules.
        <!-- END AIRNESS MANAGED: node -->
        """;

    @TempDir
    private Path shipped;

    private AssetCatalogue catalogue() {
        return new AssetFixture(this.shipped, "AGENTS.md\tSECTION\n.gitignore\tSECTION\nGEMINI.md\tREFERENCE\n")
            .manifest(Layer.JAVA, ".gitignore\tSECTION\n")
            .manifest(Layer.NODE, ".gitignore\tSECTION\nAGENTS.md\tSECTION\nAGENTS.md\tSECTION\tNODE_PROJECT\n")
            .ship(AGENTS, "Generic rules.\n")
            .ship(GITIGNORE, ".idea/\n")
            .ship(Layer.JAVA, GITIGNORE, "*.class\n")
            .ship(Layer.NODE, GITIGNORE, "node_modules/\n")
            .ship(Layer.NODE, AGENTS, "Root node rules.\n")
            .shipProject(AGENTS, "Node rules.\n")
            .catalogue();
    }

    private static List<AssetSite> embeddedFrontend() {
        return List.of(
            AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)),
            new AssetSite(FRONTEND, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
        );
    }

    private List<Findings> check(Path root, Collection<AssetSite> sites) {
        return new AssetCheck(root, this.catalogue(), List.of(), sites).findings();
    }

    private List<String> sync(Path root, Collection<AssetSite> sites) {
        return new AssetSync(root, this.catalogue(), List.of(), sites).write();
    }

    @SneakyThrows
    private static String read(Path root, String path) {
        return Files.readString(root.resolve(path));
    }

    @Test
    void writesTheRootBlocksOfEveryLayerAndTheNodeBlockIntoTheFrontend() {
        Path root = new GitFixture("section-embedded").write(GITIGNORE, "coverage-local/\n").root();
        assertEquals(List.of(AGENTS, GITIGNORE, FRONTEND + "/AGENTS.md"), this.sync(root, embeddedFrontend()));
        assertEquals(
            GENERIC_BLOCK + "\n" + JAVA_BLOCK + "\n" + NODE_BLOCK + "\ncoverage-local/\n",
            read(root, GITIGNORE),
            "the blocks lead in layer order and the project's line stays below them"
        );
        assertEquals(NODE_AGENTS, read(root, FRONTEND + "/AGENTS.md"), "the frontend gets the node block alone");
        assertTrue(
            read(root, AGENTS).contains("Root node rules."),
            "the root carries the node body written for the root, not the one written for a node project"
        );
        assertTrue(Verdicts.clean(this.check(root, embeddedFrontend())), "and the synced tree passes");
        assertEquals(List.of(), this.sync(root, embeddedFrontend()), "a second sync has nothing to write");
    }

    @Test
    void reportsAFrontendWithoutItsNodeBlock() {
        Path root = new GitFixture("section-frontend-missing").root();
        this.sync(root, embeddedFrontend());
        Path frontend = root.resolve(FRONTEND + "/AGENTS.md");
        write(frontend);
        assertEquals(
            List.of(FRONTEND + "/AGENTS.md (run mvn airness:assets-sync to restore its managed blocks)"),
            Verdicts.offences(this.check(root, embeddedFrontend()), DRIFTED),
            "a frontend inside a Java module is a node project however its module is parented"
        );
    }

    @Test
    void reportsABlockForALayerTheRepositoryDoesNotHave() {
        Path root = new GitFixture("section-unexpected").root();
        this.sync(root, embeddedFrontend());
        List<AssetSite> javaOnly = List.of(AssetSite.root(Set.of(Layer.JAVA)));
        assertEquals(
            List.of(
                AGENTS + " (run mvn airness:assets-sync to restore its managed blocks)",
                GITIGNORE + " (run mvn airness:assets-sync to restore its managed blocks)"
            ),
            Verdicts.offences(this.check(root, javaOnly), DRIFTED),
            "a block appears exactly when its layer is present somewhere in the repository"
        );
    }

    @Test
    void reportsARootWithoutTheBlockOfALayerTheRepositoryHas() {
        List<AssetSite> javaAndNode = List.of(AssetSite.root(Set.of(Layer.JAVA, Layer.NODE)));
        Path withoutJava = new GitFixture("section-no-java").write(GITIGNORE, GENERIC_BLOCK + "\n" + NODE_BLOCK).root();
        Path withoutNode = new GitFixture("section-no-node").write(GITIGNORE, GENERIC_BLOCK + "\n" + JAVA_BLOCK).root();
        String stale = GITIGNORE + " (run mvn airness:assets-sync to restore its managed blocks)";
        assertTrue(
            Verdicts.offences(this.check(withoutJava, javaAndNode), DRIFTED).contains(stale),
            "a repository with a Java module needs the java block at its root"
        );
        assertTrue(
            Verdicts.offences(this.check(withoutNode, javaAndNode), DRIFTED).contains(stale),
            "a repository with a node project needs the node block at its root"
        );
    }

    @Test
    void reportsAStaleBodyAMissingBlockAndABlockBelowTheProjectLines() {
        List<AssetSite> generic = List.of(AssetSite.root(Set.of()));
        Path stale = new GitFixture("section-stale").write(GITIGNORE, GENERIC_BLOCK.replace(".idea/", ".vscode/"))
            .root();
        Path missing = new GitFixture("section-missing").write(GITIGNORE, "target/\n").root();
        Path below = new GitFixture("section-below").write(GITIGNORE, "target/\n\n" + GENERIC_BLOCK).root();
        assertFalse(Verdicts.clean(this.check(stale, generic)), "the body is the harness's");
        assertFalse(Verdicts.clean(this.check(missing, generic)), "the block is mandatory");
        assertFalse(
            Verdicts.clean(this.check(below, generic)), "the blocks lead, so project lines cannot precede them"
        );
    }

    @Test
    void replacesStaleAndLegacyBlocksAndKeepsEveryProjectLine() {
        String legacy
            = "<!-- BEGIN AIRNESS MANAGED INSTRUCTIONS -->\nOld rules.\n<!-- END AIRNESS MANAGED INSTRUCTIONS -->\n";
        Path root = new GitFixture("section-legacy").write(AGENTS, legacy + "\n# Project\n\nOwn rules.\n").root();
        this.sync(root, List.of(AssetSite.root(Set.of())));
        assertEquals(
            """
                <!-- BEGIN AIRNESS MANAGED: generic -->
                Generic rules.
                <!-- END AIRNESS MANAGED: generic -->

                # Project

                Own rules.
                """,
            read(root, AGENTS),
            "the earlier single block is replaced rather than left beside the new one"
        );
    }

    @Test
    void refusesToEditMarkersThatDoNotPairUp() {
        Path root = new GitFixture("section-unpaired")
            .write(GITIGNORE, "# BEGIN AIRNESS MANAGED: generic\n.idea/\n")
            .root();
        List<AssetSite> generic = List.of(AssetSite.root(Set.of()));
        assertThrows(IllegalStateException.class, () -> this.sync(root, generic));
    }

    @Test
    void writesTheNodeBlockOnceWhereTheRootIsTheNodeProject() {
        Path root = new GitFixture("section-root-node").root();
        List<AssetSite> sites = List.of(
            AssetSite.root(Set.of(Layer.NODE)),
            new AssetSite("", AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
        );
        this.sync(root, sites);
        assertEquals(
            "<!-- BEGIN AIRNESS MANAGED: generic -->\nGeneric rules.\n<!-- END AIRNESS MANAGED: generic -->\n\n"
                + NODE_AGENTS,
            read(root, AGENTS),
            "one directory that is the root and a node project holds each block once, with the node project body"
        );
    }

    @Test
    void namesAFormerSeedOptOutAsAFileThatCanNoLongerBeOptedOut() {
        Path root = new GitFixture("section-opt-out").root();
        List<AssetSite> generic = List.of(AssetSite.root(Set.of()));
        this.sync(root, generic);
        List<Findings> findings = new AssetCheck(root, this.catalogue(), List.of(GITIGNORE), generic).findings();
        assertEquals(List.of(GITIGNORE), Verdicts.offences(findings, "now managed in sections"));
        assertEquals(List.of(), Verdicts.offences(findings, "which is a typo"), "the harness does own the path");
    }

    @Test
    void acceptsAnAbsentOrPointingReferenceAndReportsOneStatingRules() {
        List<AssetSite> generic = List.of(AssetSite.root(Set.of()));
        Path absent = new GitFixture("reference-absent").root();
        this.sync(absent, generic);
        Path pointing = new GitFixture("reference-pointing").write("GEMINI.md", "Read AGENTS.md.\n").root();
        this.sync(pointing, generic);
        Path stating = new GitFixture("reference-stating").write("GEMINI.md", "Read AGENTS.md.\nUse tabs.\n").root();
        this.sync(stating, generic);
        String headline = "state rules instead of pointing";
        assertEquals(List.of(), Verdicts.offences(this.check(absent, generic), headline), "the file is optional");
        assertEquals(List.of(), Verdicts.offences(this.check(pointing, generic), headline), "it may only point");
        assertEquals(
            List.of("GEMINI.md (reduce it to a line that points at AGENTS.md)"),
            Verdicts.offences(this.check(stating, generic), headline),
            "a second set of rules for one agent is a second contract"
        );
    }

    @SneakyThrows
    private static void write(Path file) {
        Files.writeString(file, "Project rules only.\n");
    }
}
