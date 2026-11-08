package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.AssetSync;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The harness's own tree reads the shipped startup configuration followed by the two lines that give each
 * clone a local Maven repository of its own, and a governed project reads the shipped bytes alone. The repair
 * writes that reading and the repository policy, which runs on every build, holds each tree to it.
 */
class TreeCatalogueTest {

    private static final String GROUP = "eu.ciechanowiec";
    private static final String AGGREGATOR = "airness";
    private static final String CONFIGURATION = ".mvn/maven.config";
    private static final String SHIPPED = "/airness/files/generic/" + CONFIGURATION + ".asset";
    private static final String SELF_LINES = """
        -Dmaven.repo.local=${user.home}/.m2/clones${session.rootDirectory}
        -Dmaven.repo.local.tail=${user.home}/.m2/repository
        """;

    @TempDir
    private Path harness;

    @TempDir
    private Path governed;

    @Test
    void writesTheSelfLinesAfterTheShippedConfigurationInTheHarnessTree() {
        assertEquals(
            shipped() + SELF_LINES, synchronize(this.module(), this.harness),
            "the clone's repository follows the shipped startup configuration, which it otherwise keeps unchanged"
        );
    }

    @Test
    void writesTheShippedConfigurationAloneInAGovernedProject() {
        assertEquals(
            shipped(), synchronize(this.consumer(), this.governed),
            "a governed project keeps reading the shared local repository"
        );
    }

    @Test
    void acceptsTheSelfLinesInTheRepositoryPolicyOfTheHarnessTree() {
        synchronize(this.module(), this.harness);

        assertEquals(List.of(), mismatched(this.module(), this.harness));
    }

    @Test
    void refusesTheShippedConfigurationAloneInTheRepositoryPolicyOfTheHarnessTree() {
        new AssetSync(this.harness, catalogue(), List.of(), List.of(AssetSite.root(Set.of()))).write();

        assertEquals(List.of(CONFIGURATION), mismatched(this.module(), this.harness));
    }

    @Test
    void refusesTheSelfLinesInTheRepositoryPolicyOfAGovernedProject() {
        new AssetSync(this.governed, catalogue().selfTree(), List.of(), List.of(AssetSite.root(Set.of()))).write();

        assertEquals(List.of(CONFIGURATION), mismatched(this.consumer(), this.governed));
    }

    private MavenProject module() {
        MavenProject module = located(project(GROUP, "airness-maven-plugin"), this.harness.resolve("plugin"));
        module.setParent(located(project(GROUP, AGGREGATOR), this.harness));
        return module;
    }

    /*
     * A consumer descends from the aggregator too, through the parent it inherits, but that aggregator lies in
     * the local repository rather than at the root of the consumer's tree.
     */
    private MavenProject consumer() {
        Path local = this.harness.resolve("local-repository");
        MavenProject parent = located(project(GROUP, "airness-parent"), local.resolve("airness-parent"));
        parent.setParent(located(project(GROUP, AGGREGATOR), local.resolve(AGGREGATOR)));
        MavenProject consumer = located(project("sample", "application"), this.governed);
        consumer.setParent(parent);
        return consumer;
    }

    @SneakyThrows
    private static String shipped() {
        URL asset = Optional.ofNullable(TreeCatalogueTest.class.getResource(SHIPPED)).orElseThrow(
            () -> new IllegalStateException("The assets artifact is not on the test classpath: " + SHIPPED)
        );
        try (InputStream stream = asset.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Only the startup files are read here, so the settings and mirror verdicts of the bare request are left out.
    private static List<String> mismatched(MavenProject current, Path root) {
        return new MavenRepositoryPolicy(
            root, new DefaultMavenExecutionRequest(), new DefaultMirrorSelector(), TreeCatalogue.of(current, root)
        ).problems().stream()
            .filter(problem -> problem.contains(" must match Airness"))
            .map(problem -> problem.substring(0, problem.indexOf(' ')))
            .toList();
    }

    private static AssetCatalogue catalogue() {
        return new AssetCatalogue(TreeCatalogueTest.class.getClassLoader());
    }

    @SneakyThrows
    private static String synchronize(MavenProject current, Path root) {
        new AssetSync(root, TreeCatalogue.of(current, root), List.of(), List.of(AssetSite.root(Set.of()))).write();
        return Files.readString(root.resolve(CONFIGURATION));
    }

    private static MavenProject located(MavenProject project, Path directory) {
        project.setFile(directory.resolve("pom.xml").toFile());
        return project;
    }

    private static MavenProject project(String group, String artifact) {
        MavenProject project = new MavenProject();
        project.setGroupId(group);
        project.setArtifactId(artifact);
        return project;
    }
}
