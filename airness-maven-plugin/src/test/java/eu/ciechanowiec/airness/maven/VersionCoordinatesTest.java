package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Version discovery follows the complete Maven parent chain while keeping the pom that owns every
 * declaration visible in the result.
 */
class VersionCoordinatesTest {

    @TempDir
    private Path directory;

    @Test
    void includesTheReactorAndEveryParent() {
        this.install("grandparent", this.pom("grand-library", ""));
        this.install("parent", this.pom("parent-library", this.parent("grandparent")));
        MavenProject child = this.project(this.pom("child-library", this.parent("parent")));

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(List.of(child), this.directory);
        List<OwnedCoordinate> dependencies = coordinates.stream()
            .filter(coordinate -> coordinate.coordinate().groupId().equals("sample.dependencies"))
            .toList();

        assertEquals(3, dependencies.size(), "one declaration is read from each raw pom in the lineage");
        assertTrue(
            dependencies.stream().map(OwnedCoordinate::owner).toList()
                .containsAll(List.of("sample:child", "sample:parent", "sample:grandparent")),
            "and each declaration retains the project that owns it"
        );
    }

    @SneakyThrows
    private MavenProject project(CharSequence pom) {
        MavenProject project = new MavenProject();
        project.setGroupId("sample");
        project.setArtifactId("child");
        project.setVersion("1.0.0");
        project.setFile(Files.writeString(this.directory.resolve("child.xml"), pom).toFile());
        return project;
    }

    @SneakyThrows
    private void install(String artifact, CharSequence pom) {
        Path version = this.directory.resolve("sample").resolve(artifact).resolve("1.0.0-SNAPSHOT");
        Files.createDirectories(version);
        Files.writeString(version.resolve(artifact + "-1.0.0-SNAPSHOT.pom"), pom);
    }

    private String pom(String artifact, String parent) {
        return """
            <project>
                %s
                <dependencies>
                    <dependency>
                        <groupId>sample.dependencies</groupId>
                        <artifactId>%s</artifactId>
                        <version>1.0.0</version>
                    </dependency>
                </dependencies>
            </project>
            """.formatted(parent, artifact);
    }

    private String parent(String artifact) {
        return """
            <parent>
                <groupId>sample</groupId>
                <artifactId>%s</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </parent>
            """.formatted(artifact);
    }
}
