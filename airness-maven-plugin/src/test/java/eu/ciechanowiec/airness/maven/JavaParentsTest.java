package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParentsTest {

    private static final String GROUP = "eu.ciechanowiec";

    @TempDir
    private Path directory;

    @Test
    void refusesJavaSourcesUnderTheGenericParentAndNamesTheJavaOne() {
        MavenProject module = this.module("src/main/java/com/example/Example.java");
        String problem = JavaParents.problem(module).orElseThrow();
        assertTrue(problem.contains("Name airness-parent-java"), problem);
    }

    @Test
    void refusesTestSourcesAloneUnderTheGenericParent() {
        MavenProject module = this.module("src/test/java/com/example/ExampleTest.java");
        assertTrue(JavaParents.problem(module).isPresent());
    }

    @Test
    void acceptsJavaSourcesThatReachTheJavaParentThroughTheSpringOne() {
        MavenProject module = this.module("src/main/java/com/example/Example.java");
        MavenProject spring = project("airness-parent-spring-boot");
        MavenProject java = project("airness-parent-java");
        spring.setParent(java);
        java.setParent(module.getParent());
        module.setParent(spring);
        assertEquals(Optional.empty(), JavaParents.problem(module));
    }

    @Test
    void acceptsAModuleWithoutJavaUnderTheGenericParent() {
        MavenProject module = this.module("src/main/java/com/example/notes.txt");
        assertEquals(Optional.empty(), JavaParents.problem(module));
    }

    @Test
    void acceptsAModuleWithoutSourceDirectories() {
        MavenProject module = this.module("README.md");
        assertEquals(Optional.empty(), JavaParents.problem(module));
    }

    @Test
    void readsASameNamedParentOfAnotherGroupAsNoJavaParent() {
        MavenProject module = this.module("src/main/java/com/example/Example.java");
        MavenProject foreign = project("airness-parent-java");
        foreign.setGroupId("com.example");
        module.setParent(foreign);
        assertTrue(JavaParents.problem(module).isPresent());
    }

    @SneakyThrows
    private MavenProject module(String file) {
        Path source = this.directory.resolve(file);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "content\n");
        MavenProject module = project("module");
        module.setFile(Files.writeString(this.directory.resolve("pom.xml"), "<project/>\n").toFile());
        module.setParent(project("airness-parent"));
        return module;
    }

    private static MavenProject project(String artifact) {
        MavenProject project = new MavenProject();
        project.setGroupId(GROUP);
        project.setArtifactId(artifact);
        return project;
    }
}
