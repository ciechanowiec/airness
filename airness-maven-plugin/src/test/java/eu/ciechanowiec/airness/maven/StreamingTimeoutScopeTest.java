package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.StreamingTimeoutInputs;
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

class StreamingTimeoutScopeTest {

    private static final String MVC = "spring-webmvc";
    private static final String SETTINGS = "spring.mvc.async.request-timeout=0\n";
    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void indexesProductionSettingsAndSourcesFromMavenRoots() {
        MavenProject project = this.application();
        this.resource(project, "application.properties", SETTINGS);
        StreamingTimeoutScope scope = new StreamingTimeoutScope(this.directory, project, List.of(project), 1);
        StreamingTimeoutInputs inputs = scope.inputs();
        assertEquals(List.of("example.Application"), inputs.applications());
        assertTrue(inputs.declarations().stream().anyMatch(input -> "resource".equals(input.kind())));
        assertTrue(inputs.declarations().stream().anyMatch(input -> "example.Application".equals(input.name())));
        assertFalse(inputs.declarations().stream().anyMatch(input -> input.name().contains("test")));
        inputs.write(StreamingTimeoutInputs.beside(scope.evidence()));
        assertTrue(inputs.matches(StreamingTimeoutInputs.beside(scope.evidence())));
        Path settings = this.directory.resolve("src/main/resources/application.properties");
        Files.writeString(settings, SETTINGS + "note=changed\n");
        assertNotEquals(inputs.digest(), scope.inputs().digest());
        assertEquals(this.directory.resolve("target/airness/spring-context.evidence"), scope.evidence());
    }

    @Test
    void testsAndNonMvcModulesDoNotActivateTheRule() {
        MavenProject project = this.application();
        StreamingTimeoutScope scope = new StreamingTimeoutScope(this.directory, project, List.of(project), 1);
        project.setArtifacts(Set.of(artifact("org.springframework", MVC, "test")));
        assertTrue(scope.inputs().applications().isEmpty());
        project.setArtifacts(Set.of(artifact("other", MVC, "compile")));
        assertTrue(scope.inputs().applications().isEmpty());
        project.setArtifacts(Set.of(artifact("org.springframework", "spring-core", "compile")));
        assertTrue(scope.inputs().applications().isEmpty());
    }

    @Test
    @SneakyThrows
    void unrelatedResourcesCannotDeclareATimeout() {
        MavenProject project = this.application();
        this.resource(project, "messages.properties", "caption=confidential-setting-value\n");
        StreamingTimeoutInputs inputs = new StreamingTimeoutScope(this.directory, project, List.of(project), 1)
            .inputs();
        assertFalse(inputs.declarations().stream().anyMatch(input -> "resource".equals(input.kind())));
        Path manifest = this.directory.resolve("inputs");
        inputs.write(manifest);
        assertFalse(Files.readString(manifest).contains("confidential-setting-value"));
    }

    @Test
    @SneakyThrows
    void followsOnlyProductionReactorDependencies() {
        MavenProject app = this.application();
        MavenProject library = project(this.directory.resolve("library"), "shared");
        MavenProject unrelated = project(this.directory.resolve("unrelated"), "other");
        this.resource(library, "application-library.properties", SETTINGS);
        this.resource(unrelated, "application-other.properties", SETTINGS);
        app.setArtifacts(Set.of(artifact("org.springframework", MVC, "compile"), library.getArtifact()));
        StreamingTimeoutScope scope = new StreamingTimeoutScope(
            this.directory, app, List.of(app, library, unrelated), 1
        );
        assertTrue(scope.inputs().declarations().stream().anyMatch(input -> input.name().contains("library")));
        assertFalse(scope.inputs().declarations().stream().anyMatch(input -> input.name().contains("unrelated")));
        library.getArtifact().setScope("test");
        assertFalse(scope.inputs().declarations().stream().anyMatch(input -> input.name().contains("library")));
    }

    @Test
    @SneakyThrows
    void honorsResourceSelectionAndMissingCopies() {
        MavenProject project = this.application();
        this.resource(project, "application.properties", SETTINGS);
        Path missing = this.resource(
            project, "application-missing.yaml", "spring:\n  mvc:\n    async:\n      request-timeout: 0\n"
        );
        Files.delete(Path.of(project.getBuild().getOutputDirectory()).resolve(missing.getFileName()));
        project.getResources().getFirst().setExcludes(List.of("application.properties"));
        assertFalse(
            new StreamingModuleInputs(this.directory, project).read().stream()
                .anyMatch(input -> "resource".equals(input.kind()))
        );
    }

    @Test
    @SneakyThrows
    void recordsPackagedConfigurationAtItsResourceTargetPath() {
        MavenProject project = this.application();
        this.resource(project, "policy.yml", "spring:\n  mvc:\n    async:\n      request-timeout: 0\n");
        this.resource(project, "unrelated.txt", "ordinary content");
        Path output = Path.of(project.getBuild().getOutputDirectory());
        Path nested = Files.createDirectories(output.resolve("nested directory"));
        Files.move(output.resolve("policy.yml"), nested.resolve("policy.yml"));
        project.getResources().getFirst().setTargetPath("nested directory");
        project.getArtifact().setFile(this.directory.resolve("target/application.jar").toFile());
        assertTrue(
            new StreamingModuleInputs(this.directory, project).read().stream()
                .anyMatch(input -> input.origin().endsWith("application.jar!/nested%20directory/policy.yml"))
        );
    }

    @SneakyThrows
    private MavenProject application() {
        Process git = new ProcessBuilder("git", "init", "--quiet", this.directory.toString()).start();
        if (git.waitFor() != 0) {
            throw new IllegalStateException("Fixture repository creation failed");
        }
        MavenProject project = project(this.directory, "app");
        Path source = Files.createDirectories(this.directory.resolve("src/main/java/example"));
        Files.writeString(
            source.resolve("Application.java"), """
                package example;
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class Application {}
                """
        );
        Path tests = Files.createDirectories(this.directory.resolve("src/test/resources"));
        Files.writeString(tests.resolve("application-test.properties"), SETTINGS);
        project.setArtifacts(Set.of(artifact("org.springframework", MVC, "compile")));
        return project;
    }

    @SneakyThrows
    private Path resource(MavenProject project, String name, String content) {
        Path source = Files.createDirectories(project.getBasedir().toPath().resolve("src/main/resources")).resolve(
            name
        );
        Files.writeString(source, content);
        Path output = Files.createDirectories(Path.of(project.getBuild().getOutputDirectory())).resolve(name);
        Files.writeString(output, content);
        return source;
    }

    private static MavenProject project(Path root, String name) {
        MavenProject project = InputProjects.project(root);
        project.setGroupId("example");
        project.setArtifactId(name);
        project.setVersion("1.0.0");
        project.setArtifact(artifact("example", name, "compile"));
        project.getBuild().setOutputDirectory(root.resolve("target/classes").toString());
        return project;
    }

    private static DefaultArtifact artifact(String group, String name, String scope) {
        return new DefaultArtifact(group, name, "1.0.0", scope, "jar", "", new DefaultArtifactHandler("jar"));
    }
}
