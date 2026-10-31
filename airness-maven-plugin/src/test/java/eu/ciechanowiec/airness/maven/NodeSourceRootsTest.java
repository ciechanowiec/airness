package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The checked source roots of a module's node projects are the defaults plus what each project adds.
 */
class NodeSourceRootsTest {

    @TempDir
    private Path module;

    @SneakyThrows
    private void manifest(String directory, String content) {
        Path project = Files.createDirectories(this.module.resolve(directory));
        Files.writeString(project.resolve("package.json"), content);
    }

    private Path resolved(String path) {
        return this.module.resolve(path).toAbsolutePath().normalize();
    }

    @Test
    void readsTheDefaultRootsAndTheOnesAProjectAdds() {
        this.manifest("frontend", "{\"airness\": {\"sourceRoots\": [\"./styles/\"]}}\n");
        assertEquals(
            List.of(
                this.resolved("frontend/scripts"), this.resolved("frontend/src"), this.resolved("frontend/styles"),
                this.resolved("frontend/tests")
            ),
            NodeSourceRoots.of(NodeProjects.of(this.module, "frontend"))
        );
    }

    @Test
    void readsTheWholeProjectForADotRoot() {
        this.manifest(".", "{\"airness\": {\"sourceRoots\": [\".\"]}}\n");
        assertEquals(
            this.resolved("."), NodeSourceRoots.of(NodeProjects.of(this.module, ".")).getFirst(),
            "a root of . is the project itself"
        );
    }

    @Test
    void passesOverADeclarationOutsideTheModule() {
        assertEquals(List.of(), NodeSourceRoots.of(NodeProjects.of(this.module, "../sibling")));
    }

    @Test
    void passesOverADeclarationWithoutAManifest() {
        assertEquals(List.of(), NodeSourceRoots.of(NodeProjects.of(this.module, "missing")));
    }
}
