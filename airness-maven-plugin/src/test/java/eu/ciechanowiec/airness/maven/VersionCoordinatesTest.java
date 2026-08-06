package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Version discovery follows Maven's resolved parent chain and effective properties while keeping the
 * raw pom that owns every declaration visible in the result.
 */
class VersionCoordinatesTest {

    @TempDir
    private Path directory;

    @Test
    void includesTheReactorAndEveryResolvedParent() {
        MavenProject grandparent = this.project("grandparent", this.pom("grand-library", ""), Optional.empty());
        MavenProject parent = this.project("parent", this.pom("parent-library", ""), Optional.of(grandparent));
        MavenProject child = this.project("child", this.pom("child-library", ""), Optional.of(parent));

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(
            List.of(child), this.directory.resolve("empty-repository")
        );
        List<OwnedCoordinate> dependencies = coordinates.stream()
            .filter(coordinate -> "sample.dependencies".equals(coordinate.coordinate().groupId()))
            .toList();

        assertEquals(3, dependencies.size(), "one declaration is read from each raw pom in the lineage");
        assertTrue(
            dependencies.stream().map(OwnedCoordinate::owner).toList()
                .containsAll(List.of("sample:child", "sample:parent", "sample:grandparent")),
            "and each declaration retains the project that owns it"
        );
    }

    @Test
    void resolvesAVersionPropertyInheritedFromTheParentModel() {
        MavenProject parent = this.project("parent", "<project/>", Optional.empty());
        MavenProject child = this.project(
            "child",
            this.pom("library", "${library.version}"),
            Optional.of(parent)
        );
        child.getProperties().setProperty("library.version", "3.4.5");

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(
            List.of(child), this.directory.resolve("empty-repository")
        );

        assertTrue(
            coordinates.stream().anyMatch(coordinate -> "3.4.5".equals(coordinate.coordinate().version())),
            "the raw declaration keeps its owner while Maven's effective property supplies its value"
        );
    }

    @Test
    void resolvesProjectVersionBeforeExcludingAReactorDependency() {
        MavenProject library = this.project("library", "<project/>", Optional.empty());
        MavenProject application = this.project(
            "application",
            this.pom("sample", "library", "${project.version}"),
            Optional.empty()
        );

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(
            List.of(library, application), this.directory.resolve("empty-repository")
        );

        assertTrue(
            coordinates.stream().noneMatch(coordinate -> "library".equals(coordinate.coordinate().artifactId())),
            "a same-reactor dependency is excluded by its resolved versioned coordinate"
        );
    }

    @SneakyThrows
    private MavenProject project(String artifact, CharSequence pom, Optional<MavenProject> parent) {
        MavenProject project = new MavenProject();
        project.setGroupId("sample");
        project.setArtifactId(artifact);
        project.setVersion("1.0.0-SNAPSHOT");
        project.setFile(Files.writeString(this.directory.resolve(artifact + ".xml"), pom).toFile());
        parent.ifPresent(project::setParent);
        return project;
    }

    private String pom(String artifact, String version) {
        String held = version.isEmpty() ? "1.0.0" : version;
        return this.pom("sample.dependencies", artifact, held);
    }

    private String pom(String group, String artifact, String version) {
        return """
            <project>
                <dependencies>
                    <dependency>
                        <groupId>%s</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
            </project>
            """.formatted(group, artifact, version);
    }
}
