package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The harness's own tree holds a pinned file with its self addition after the shipped bytes, and no other
 * tree may.
 *
 * <p>The addition is narrow on purpose: it follows the shipped bytes rather than replacing them, it reaches
 * only a pinned root file, and only the catalogue the harness's own tree reads carries it. A governed project
 * that copies the addition drifts like any other edit of a pinned file.
 */
class AssetSelfTreeTest {

    private static final String CONFIGURATION = ".mvn/maven.config";
    private static final String EDITORCONFIG = ".editorconfig";
    private static final String GITIGNORE = ".gitignore";
    private static final String ESLINT = "eslint.config.mjs";
    private static final String FRONTEND = "frontend";
    private static final String DRIFTED = "changed or is missing";

    private static final String SHIPPED = "--global-settings\n${session.rootDirectory}/.mvn/settings.xml\n";
    private static final String ADDITION = "-Dmaven.repo.local=${user.home}/.m2/clones${session.rootDirectory}\n";
    private static final String EDITOR = "root = true\n";
    private static final String SEEDED = "target/\n";
    private static final String LINT = "export default []\n";

    @TempDir
    private Path shipped;

    private AssetCatalogue catalogue() {
        return new AssetFixture(this.shipped, ".mvn/maven.config\tPINNED\n.editorconfig\tPINNED\n.gitignore\tSEED\n")
            .manifest(Layer.NODE, "eslint.config.mjs\tPINNED\tNODE_PROJECT\n")
            .ship(CONFIGURATION, SHIPPED)
            .ship(EDITORCONFIG, EDITOR)
            .ship(GITIGNORE, SEEDED)
            .shipProject(ESLINT, LINT)
            .shipSelf(Layer.GENERIC, CONFIGURATION, ADDITION)
            .shipSelf(Layer.GENERIC, GITIGNORE, "never added\n")
            .shipSelf(Layer.NODE, ESLINT, "never added\n")
            .catalogue();
    }

    private static List<AssetSite> sites() {
        return List.of(
            AssetSite.root(Set.of(Layer.NODE)), new AssetSite(FRONTEND, AssetLocation.NODE_PROJECT, Set.of(Layer.NODE))
        );
    }

    private static GitFixture tree(String name, String configuration) {
        return new GitFixture(name)
            .write(CONFIGURATION, configuration)
            .write(EDITORCONFIG, EDITOR)
            .write(GITIGNORE, "project-owned\n")
            .write(FRONTEND + "/package.json", "{}\n")
            .write(FRONTEND + '/' + ESLINT, LINT);
    }

    private static List<String> drifted(Path root, AssetCatalogue catalogue) {
        return Verdicts.offences(new AssetCheck(root, catalogue, List.of(), sites()).findings(), DRIFTED);
    }

    @SneakyThrows
    private static String read(Path root, String path) {
        return Files.readString(root.resolve(path));
    }

    @Test
    void expectsThePinnedFileFollowedByItsAdditionInTheHarnessTree() {
        Path root = tree("self-variant", SHIPPED + ADDITION).root();
        assertTrue(
            Verdicts.clean(new AssetCheck(root, this.catalogue().selfTree(), List.of(), sites()).findings()),
            "the addition follows the shipped bytes, and the files without one are held to the shipped bytes alone"
        );
    }

    @Test
    void refusesThePlainPinnedFileInTheHarnessTree() {
        Path root = tree("self-plain", SHIPPED).root();
        assertEquals(
            List.of(CONFIGURATION + " (run mvn airness:assets-sync to restore it)"),
            drifted(root, this.catalogue().selfTree()),
            "the harness's own tree without the addition would install into the shared repository"
        );
    }

    @Test
    void refusesAnyOtherDeviationInTheHarnessTree() {
        Path root = tree("self-deviation", SHIPPED + ADDITION + "-DskipTests\n").root();
        assertEquals(
            List.of(CONFIGURATION + " (run mvn airness:assets-sync to restore it)"),
            drifted(root, this.catalogue().selfTree()),
            "the addition is the one difference the harness's own tree may carry, and a further line is drift"
        );
    }

    @Test
    void refusesTheAdditionInAGovernedProject() {
        Path root = tree("governed-addition", SHIPPED + ADDITION).root();
        assertEquals(
            List.of(CONFIGURATION + " (run mvn airness:assets-sync to restore it)"),
            drifted(root, this.catalogue()),
            "a governed project keeps reading the shared repository, so the addition is drift there"
        );
    }

    @Test
    void writesTheAdditionWhenSynchronizingTheHarnessTree() {
        Path root = new GitFixture("self-sync").write(FRONTEND + "/package.json", "{}\n").root();
        new AssetSync(root, this.catalogue().selfTree(), List.of(), sites()).write();
        assertEquals(
            List.of(SHIPPED + ADDITION, SEEDED, LINT),
            List.of(read(root, CONFIGURATION), read(root, GITIGNORE), read(root, FRONTEND + '/' + ESLINT)),
            "only the pinned root file takes its addition, while a seed and a node project file stay as shipped"
        );
    }

    @Test
    void writesThePlainPinnedFileWhenSynchronizingAGovernedProject() {
        Path root = tree("governed-sync", SHIPPED + ADDITION).root();
        assertEquals(
            List.of(CONFIGURATION),
            new AssetSync(root, this.catalogue(), List.of(), sites()).write(),
            "the copied addition is drift, which the sync repairs"
        );
        assertEquals(SHIPPED, read(root, CONFIGURATION), "and the repair leaves the shipped bytes alone");
    }
}
