package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies how Dockerfiles, compose projects and workflows are found.
 */
class ContainerDefinitionsTest {

    private static List<String> relative(Path root, List<Path> files) {
        return files.stream().map(root::relativize).map(Path::toString).toList();
    }

    @Test
    void takesOneBaseComposeFilePerDirectoryInComposePrecedence() {
        Path root = new GitFixture("containers-compose")
            .write("docker-compose.yml", "services: {}\n")
            .write("compose.yaml", "services: {}\n")
            .write("compose.override.yaml", "services: {}\n")
            .write("app/docker-compose.yaml", "services: {}\n")
            .write("other/compose.prod.yaml", "services: {}\n")
            .root();
        assertEquals(
            List.of(Path.of("app", "docker-compose.yaml").toString(), "compose.yaml"),
            relative(root, new ContainerDefinitions(root).composeFiles()),
            "a directory is one project, read through its highest-precedence base file"
        );
    }

    @Test
    void findsDockerfilesAndWorkflowsButNotCompositeActions() {
        Path root = new GitFixture("containers-files")
            .write("Dockerfile", "FROM alpine:3.22\n")
            .write("images/api.Dockerfile", "FROM alpine:3.22\n")
            .write(".github/workflows/ci.yml", "on: push\n")
            .write(".github/workflows/nested/skip.yml", "on: push\n")
            .write(".github/actions/setup/action.yml", "runs: {}\n")
            .root();
        ContainerDefinitions definitions = new ContainerDefinitions(root);
        assertEquals(
            List.of("Dockerfile", Path.of("images", "api.Dockerfile").toString()),
            relative(root, definitions.dockerfiles()),
            "a Dockerfile is found under each spelling the blocklist reads"
        );
        assertEquals(
            List.of(Path.of(".github", "workflows", "ci.yml").toString()),
            relative(root, definitions.workflows()),
            "only a file directly in the workflow directory is a workflow"
        );
    }
}
