package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.BuildInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectBuildInputsTest {

    private static final String COMPILER = "maven-compiler-plugin";
    private static final String RESOURCES = "maven-resources-plugin";
    private static final String JAVA = "main Java";
    private static final String RESOURCE = "main resource";
    private static final String KEEP = "Keep.java";
    private static final String DROP = "Drop.java";

    @TempDir
    private Path directory;

    @Test
    void selectsMainAndTestInputsWithTheEffectiveFilters() {
        this.write("src/main/java/Keep.java");
        this.write("src/main/java/Drop.java");
        this.write("src/test/java/Keep.java");
        this.write("src/test/java/Drop.java");
        this.write("src/main/resources/page.html");
        this.write("src/main/resources/skip.txt");
        this.write("src/test/resources/data.json");
        MavenProject project = InputProjects.project(this.directory);
        InputProjects.plugin(
            project, COMPILER, "<configuration><excludes><exclude>**/Drop.java</exclude></excludes>"
                + "<testIncludes><testInclude>**/Keep.java</testInclude></testIncludes></configuration>"
        );
        project.getResources().getFirst().setIncludes(List.of("**/*.html"));
        assertEquals(Set.of(KEEP), this.selected(project, JAVA));
        assertEquals(Set.of(KEEP), this.selected(project, "test Java"));
        assertEquals(Set.of("page.html"), this.selected(project, RESOURCE));
        assertEquals(Set.of("data.json"), this.selected(project, "test resource"));
    }

    @Test
    void mergesExecutionFiltersWithoutInventingAnUnfilteredDefaultExecution() {
        this.write("src/main/java/Keep.java");
        this.write("src/main/java/Drop.java");
        PluginExecution execution = new PluginExecution();
        execution.setId("default-compile");
        execution.addGoal("compile");
        execution.setConfiguration(
            InputProjects.xml(
                "<configuration><excludes><exclude>**/Drop.java</exclude></excludes></configuration>"
            )
        );
        MavenProject project = InputProjects.project(this.directory);
        Plugin plugin = InputProjects.plugin(project, COMPILER, "<configuration/>");
        plugin.addExecution(execution);
        assertEquals(Set.of(KEEP), this.selected(project, JAVA));
        PluginExecution extra = new PluginExecution();
        extra.setId("extra-compile");
        extra.addGoal("compile");
        extra.setConfiguration(
            InputProjects.xml(
                "<configuration><includes><include>**/Drop.java</include></includes></configuration>"
            )
        );
        plugin.addExecution(extra);
        assertEquals(Set.of(KEEP, DROP), this.selected(project, JAVA));
        extra.setPhase("none");
        assertEquals(Set.of(KEEP), this.selected(project, JAVA));
        extra.setPhase("compile");
        assertEquals(Set.of(KEEP, DROP), this.selected(project, JAVA));
    }

    @Test
    void ignoresDisabledDefaultExecutions() {
        this.write("src/main/java/Keep.java");
        PluginExecution execution = new PluginExecution();
        execution.setId("default-compile");
        execution.setPhase("none");
        MavenProject project = InputProjects.project(this.directory);
        InputProjects.plugin(project, COMPILER, "<configuration/>").addExecution(execution);
        assertTrue(this.selected(project, JAVA).isEmpty());
        execution.setPhase("compile");
        assertEquals(Set.of(KEEP), this.selected(project, JAVA));
    }

    @Test
    void readsCustomAndLaterRegisteredSourceRoots() {
        this.write("custom/Keep.java");
        this.write("custom/note.txt");
        this.write("later/Later.java");
        MavenProject project = InputProjects.project(this.directory);
        project.addCompileSourceRoot(this.directory.resolve("later").toString());
        InputProjects.plugin(
            project, COMPILER, "<configuration><compileSourceRoots><compileSourceRoot>custom</compileSourceRoot>"
                + "</compileSourceRoots></configuration>"
        );
        assertEquals(Set.of(KEEP), this.selected(project, JAVA));
        project.getBuild().setPlugins(List.of());
        assertEquals(Set.of("Later.java"), this.selected(project, JAVA));
    }

    @Test
    void readsExplicitResourceSelectionsAndDefaultExclusionSettings() {
        this.write("custom/public/page.html");
        this.write("custom/private/skip.txt");
        this.write("custom/.git/config");
        MavenProject project = InputProjects.project(this.directory);
        InputProjects.plugin(
            project, RESOURCES, "<configuration><addDefaultExcludes>false</addDefaultExcludes><resources><resource>"
                + "<directory>custom</directory><excludes><exclude>private/**</exclude></excludes>"
                + "</resource></resources></configuration>"
        );
        assertEquals(Set.of("public/page.html", ".git/config"), this.selected(project, RESOURCE));
    }

    @Test
    void ignoresBuildDirectoriesAndRootsOutsideTheRepository() {
        this.write("target/generated/Generated.java");
        MavenProject project = InputProjects.project(this.directory);
        project.addCompileSourceRoot(this.directory.resolve("target/generated").toString());
        project.addCompileSourceRoot(this.directory.resolve("..").normalize().toString());
        assertTrue(this.selected(project, JAVA).isEmpty());
    }

    @Test
    void refusesUnresolvedInputPaths() {
        MavenProject project = InputProjects.project(this.directory);
        project.addCompileSourceRoot("${unresolved}");
        assertThrows(IllegalStateException.class, () -> this.selected(project, JAVA));
    }

    private Set<String> selected(MavenProject project, String kind) {
        return new ProjectBuildInputs(
            project, List.of(this.directory.resolve("target")), this.directory, Set.of("compile")
        )
            .selections().stream().filter(input -> input.kind().endsWith(kind))
            .map(BuildInput::selected).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
    }

    @SneakyThrows
    private void write(String name) {
        Path file = this.directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Fixture\n");
    }
}
