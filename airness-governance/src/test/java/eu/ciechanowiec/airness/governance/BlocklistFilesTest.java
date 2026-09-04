package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The files the repository half of the blocklist reads are found by the names their tools look for.
 */
class BlocklistFilesTest {

    private static final Path ROOT = Path.of("repo");
    private static final List<Path> TRACKED = List.of(
        ROOT.resolve("Dockerfile"),
        ROOT.resolve("docker/Dockerfile.jre"),
        ROOT.resolve("build.dockerfile"),
        ROOT.resolve("compose.yaml"),
        ROOT.resolve("docker-compose.override.yml"),
        ROOT.resolve("deploy/compose.prod.yaml"),
        ROOT.resolve(".github/workflows/build.yml"),
        ROOT.resolve(".github/workflows/nested/skipped.yml"),
        ROOT.resolve(".github/actions/setup/action.yaml"),
        ROOT.resolve("src/main/resources/application.yaml"),
        ROOT.resolve("README.adoc")
    );

    private static List<String> names(List<Path> files) {
        return files.stream().map(ROOT::relativize).map(Path::toString).toList();
    }

    @Test
    void findsEveryDockerfileByName() {
        assertEquals(
            List.of("Dockerfile", "docker/Dockerfile.jre", "build.dockerfile"),
            names(BlocklistFiles.dockerfiles(ROOT, TRACKED))
        );
    }

    @Test
    void findsEveryComposeFileByName() {
        assertEquals(
            List.of("compose.yaml", "docker-compose.override.yml", "deploy/compose.prod.yaml"),
            names(BlocklistFiles.composeFiles(ROOT, TRACKED)),
            "an application file is not a compose file"
        );
    }

    @Test
    void findsWorkflowsAndCompositeActionsWhereGitHubReadsThem() {
        assertEquals(
            List.of(".github/workflows/build.yml", ".github/actions/setup/action.yaml"),
            names(BlocklistFiles.workflows(ROOT, TRACKED)),
            "a nested file under workflows is not a workflow"
        );
    }
}
