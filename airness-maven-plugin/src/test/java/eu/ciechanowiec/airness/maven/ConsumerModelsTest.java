package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
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
