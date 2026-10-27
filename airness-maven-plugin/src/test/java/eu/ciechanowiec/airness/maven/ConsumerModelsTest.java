package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsumerModelsTest {

    @TempDir
    private Path directory;

    @Test
    void checksTheParentOncePerSessionAndNamesItsFile() {
        MavenProject parent = this.project("company", "<properties><skipTests>true</skipTests></properties>");
        MavenProject child = this.project("child", "");
        child.setParent(parent);
        SessionData data = new DefaultSessionData();
        assertEquals(
            List.of(parent.getFile().toPath() + ": Remove child property skipTests; it can bypass the Airness verdict"),
            ConsumerModels.problems(child, data, this.directory)
        );
        assertTrue(ConsumerModels.problems(parent, data, this.directory).isEmpty());
        assertEquals(1, ConsumerModels.problems(child, new DefaultSessionData(), this.directory).size());
    }

    @Test
    void stopsAtTheTrustedHarnessParent() {
        MavenProject airness = this.project("airness-parent", "<properties><skipTests>false</skipTests></properties>");
        airness.setGroupId("eu.ciechanowiec");
        MavenProject child = this.project("child", "");
        child.setParent(airness);
        assertTrue(ConsumerModels.problems(child, new DefaultSessionData(), this.directory).isEmpty());
    }

    @Test
    void stopsAtTheTrustedJavaParentBeforeTheGenericOne() {
        MavenProject java = this.project(
            "airness-parent-java", "<properties><airness.coverage.skip>false</airness.coverage.skip></properties>"
        );
        java.setGroupId("eu.ciechanowiec");
        MavenProject child = this.project("child", "");
        child.setParent(java);
        assertTrue(ConsumerModels.problems(child, new DefaultSessionData(), this.directory).isEmpty());
    }

    @Test
    void checksManagedVersionsInTheParent() {
        MavenProject parent = this.project("company", "<properties><airness.version>1</airness.version></properties>");
        MavenProject child = this.project("child", "");
        child.setParent(parent);
        assertTrue(
            ConsumerModels.problems(child, new DefaultSessionData(), this.directory).stream()
                .anyMatch(problem -> problem.contains("company.xml") && problem.contains("airness.version"))
        );
    }

    @Test
    @SneakyThrows
    void readsAnInstalledParentWithoutAProjectFile() {
        Path pom = this.directory.resolve("example/company/1/company-1.pom");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, "<project><properties><skipTests>true</skipTests></properties></project>");
        MavenProject parent = new MavenProject();
        parent.setGroupId("example");
        parent.setArtifactId("company");
        parent.setVersion("1");
        MavenProject child = this.project("child", "");
        child.setParent(parent);
        assertEquals(
            List.of(pom + ": Remove child property skipTests; it can bypass the Airness verdict"),
            ConsumerModels.problems(child, new DefaultSessionData(), this.directory)
        );
    }

    @Test
    void refusesANodeRunExecutionReusingAnIdItsParentBindsOrManages() {
        Build build = new Build();
        build.addPlugin(airness("node-gate-eslint"));
        build.addPlugin(plugin("eu.ciechanowiec", "airness-other-plugin", "sibling"));
        build.addPlugin(plugin("org.example", "airness-maven-plugin", "lookalike"));
        PluginManagement management = new PluginManagement();
        management.addPlugin(airness("node-gate-knip"));
        build.setPluginManagement(management);
        MavenProject parent = this.project("company", "");
        parent.getModel().setBuild(build);
        assertEquals(Set.of("node-gate-eslint", "node-gate-knip"), ConsumerModels.inherited(this.child(parent, "x")));
        SessionData data = new DefaultSessionData();
        List<String> problems = Stream.of("node-gate-eslint", "node-gate-knip")
            .flatMap(id -> ConsumerModels.problems(this.child(parent, id), data, this.directory).stream())
            .filter(problem -> problem.contains("airness-maven-plugin"))
            .toList();
        assertEquals(2, problems.size(), "both reused ids are refused: " + problems);
        assertEquals(
            List.of(), ConsumerModels.problems(
                this.child(parent, "frontend-build"), new DefaultSessionData(), this.directory
            )
        );
    }

    private MavenProject child(MavenProject parent, String id) {
        MavenProject child = this.project(
            "child-" + id, """
                <build><plugins><plugin>
                    <groupId>eu.ciechanowiec</groupId>
                    <artifactId>airness-maven-plugin</artifactId>
                    <executions><execution>
                        <id>%s</id>
                        <phase>generate-resources</phase>
                        <goals><goal>node-run</goal></goals>
                    </execution></executions>
                </plugin></plugins></build>
                """.formatted(id)
        );
        child.setParent(parent);
        return child;
    }

    private static Plugin airness(String id) {
        return plugin("eu.ciechanowiec", "airness-maven-plugin", id);
    }

    private static Plugin plugin(String group, String artifact, String id) {
        Plugin plugin = new Plugin();
        plugin.setGroupId(group);
        plugin.setArtifactId(artifact);
        PluginExecution execution = new PluginExecution();
        execution.setId(id);
        plugin.addExecution(execution);
        return plugin;
    }

    @SneakyThrows
    private MavenProject project(String name, String body) {
        MavenProject project = new MavenProject();
        project.setGroupId("example");
        project.setArtifactId(name);
        project.setFile(
            Files.writeString(this.directory.resolve(name + ".xml"), "<project>" + body + "</project>")
                .toFile()
        );
        return project;
    }
}
