package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.TemplateMessageInputs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.SneakyThrows;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringTemplateMessageScopeTest {

    private static final String THYMELEAF = "org.thymeleaf";
    private static final String INTEGRATION = "thymeleaf-spring6";

    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void usesProductionResourcesAndTheConfiguredBuildDirectory() {
        Process git = new ProcessBuilder("git", "init", "--quiet", this.root.toString()).start();
        assertEquals(0, git.waitFor());
        Path sources = Files.createDirectories(this.root.resolve("src/main/java/example"));
        Path resources = Files.createDirectories(this.root.resolve("src/main/resources/templates"));
        Path tests = Files.createDirectories(this.root.resolve("src/test/resources/templates"));
        Files.writeString(
            sources.resolve("Application.java"), """
                package example;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class Application {}
                """
        );
        Files.writeString(resources.resolve("page.html"), "<p th:text=\"#{caption}\"></p>");
        Files.writeString(tests.resolve("test.html"), "<p th:text=\"#{test.only}\"></p>");
        MavenProject project = InputProjects.project(this.root);
        project.setArtifacts(Set.of(artifact(THYMELEAF, INTEGRATION, "compile")));
        project.getBuild().setDirectory(this.root.resolve("output").toString());
        SpringTemplateMessageScope scope = new SpringTemplateMessageScope(this.root, project, 1);
        TemplateMessageInputs inputs = scope.inputs();
        assertEquals(List.of("example.Application"), inputs.applications());
        assertEquals(1, inputs.references().size());
        assertEquals("caption", inputs.references().getFirst().key());
        assertEquals(this.root.resolve("output/airness/spring-context.evidence"), scope.evidence());
        inputs.write(TemplateMessageInputs.beside(scope.evidence()));
        assertTrue(inputs.matches(TemplateMessageInputs.beside(scope.evidence())));
        refreshWithoutProductionThymeleaf(project, scope, inputs);
        Files.delete(sources.resolve("Application.java"));
        Files.writeString(sources.resolve("Library.java"), "package example; public final class Library {}");
        assertTrue(scope.inputs().applications().isEmpty());
        assertTrue(scope.inputs().references().isEmpty());
    }

    @Test
    void requiresProductionSpringThymeleafIntegration() {
        MavenProject project = InputProjects.project(this.root);
        assertFalse(SpringTemplateMessageScope.includesThymeleaf(project));
        project.setArtifacts(Set.of(artifact(THYMELEAF, INTEGRATION, "compile")));
        assertTrue(SpringTemplateMessageScope.includesThymeleaf(project));
        project.setArtifacts(Set.of(artifact("other", INTEGRATION, "compile")));
        assertFalse(SpringTemplateMessageScope.includesThymeleaf(project));
        project.setArtifacts(Set.of(artifact(THYMELEAF, "thymeleaf", "compile")));
        assertFalse(SpringTemplateMessageScope.includesThymeleaf(project));
        project.setArtifacts(Set.of(artifact(THYMELEAF, INTEGRATION, "test")));
        assertFalse(SpringTemplateMessageScope.includesThymeleaf(project));
        project.setArtifacts(Set.of(artifact(THYMELEAF, INTEGRATION, "runtime")));
        assertTrue(SpringTemplateMessageScope.includesThymeleaf(project));
    }

    private static void refreshWithoutProductionThymeleaf(
        MavenProject project, SpringTemplateMessageScope scope, TemplateMessageInputs inputs
    ) {
        project.setArtifacts(Set.of(artifact(THYMELEAF, INTEGRATION, "test")));
        TemplateMessageInputs inactive = scope.inputs();
        inactive.write(TemplateMessageInputs.beside(scope.evidence()));
        assertTrue(inactive.applications().isEmpty());
        assertTrue(inactive.references().isEmpty());
        assertFalse(inputs.matches(TemplateMessageInputs.beside(scope.evidence())));
        project.setArtifacts(Set.of(artifact(THYMELEAF, INTEGRATION, "compile")));
    }

    private static DefaultArtifact artifact(String group, String name, String scope) {
        return new DefaultArtifact(group, name, "3.1.5.RELEASE", scope, "jar", "", new DefaultArtifactHandler("jar"));
    }
}
