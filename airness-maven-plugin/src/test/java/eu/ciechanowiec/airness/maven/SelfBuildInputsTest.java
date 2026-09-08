package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.IgnoredBuildInputs;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class SelfBuildInputsTest {

    @Test
    void theHarnessOwnInputDirectoriesContainNoIgnoredBuildInputs() {
        Path root = SelfModules.repository();
        List<Path> modules = SelfModules.directories();
        List<Path> outputs = Stream.concat(Stream.of(root), modules.stream())
            .map(module -> module.resolve("target")).toList();
        assertTrue(modules.size() > 1, "the self-check must read the declared reactor");
        assertEquals(
            List.of(), modules.stream().flatMap(module -> problems(root, module, outputs).stream()).toList()
        );
    }

    private static List<String> problems(Path root, Path module, List<Path> outputs) {
        MavenProject project = InputProjects.project(module);
        ProjectBuildInputs inputs = new ProjectBuildInputs(project, outputs, root, List.of("compile"));
        return new IgnoredBuildInputs(root, inputs.selections(), outputs).problems();
    }
}
