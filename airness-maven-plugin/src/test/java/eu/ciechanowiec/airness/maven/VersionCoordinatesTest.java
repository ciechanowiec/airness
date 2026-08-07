package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static final String LIBRARY = "library";

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
            this.pom(LIBRARY, "${library.version}"),
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
        MavenProject library = this.project(LIBRARY, "<project/>", Optional.empty());
        MavenProject application = this.project(
            "application",
            this.pom("sample", LIBRARY, "${project.version}"),
            Optional.empty()
        );

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(
            List.of(library, application), this.directory.resolve("empty-repository")
        );

        assertTrue(
            coordinates.stream().noneMatch(coordinate -> LIBRARY.equals(coordinate.coordinate().artifactId())),
            "a same-reactor dependency is excluded by its resolved versioned coordinate"
        );
    }

    @Test
    void resolvesAnInheritedDeclarationWithTheChildsEffectiveProperty() {
        MavenProject parent = this.project(
            "parent",
            """
                <project>
                    <properties>
                        <library.version>4.7.7</library.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>sample.dependencies</groupId>
                            <artifactId>library</artifactId>
                            <version>${library.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """,
            Optional.empty()
        );
        parent.getProperties().setProperty("library.version", "4.7.7");
        MavenProject child = this.project("child", "<project/>", Optional.of(parent));
        child.getProperties().setProperty("library.version", "2.0.0");

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(
            List.of(child), this.directory.resolve("empty-repository")
        );

        assertTrue(
            coordinates.stream().anyMatch(
                coordinate -> LIBRARY.equals(coordinate.coordinate().artifactId())
                    && "2.0.0".equals(coordinate.coordinate().version())
            ),
            "an inherited declaration resolves as Maven resolves it for the consuming child"
        );
    }

    @Test
    void resolvesNestedAndEmbeddedEffectiveProperties() {
        MavenProject project = this.project(
            "nested",
            this.pom(LIBRARY, "${major}.${minor}"),
            Optional.empty()
        );
        project.getProperties().setProperty("major", "${base}");
        project.getProperties().setProperty("base", "2");
        project.getProperties().setProperty("minor", "3");

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(
            List.of(project), this.directory.resolve("empty-repository")
        );

        assertTrue(
            coordinates.stream().anyMatch(coordinate -> "2.3".equals(coordinate.coordinate().version()))
        );
    }

    @Test
    void rejectsCyclicEffectiveProperties() {
        MavenProject project = this.project("cyclic", this.pom(LIBRARY, "${first}"), Optional.empty());
        project.getProperties().setProperty("first", "${second}");
        project.getProperties().setProperty("second", "${first}");

        assertThrows(
            IllegalStateException.class,
            () -> VersionCoordinates.from(List.of(project), this.directory.resolve("empty-repository"))
        );
    }

    @Test
    @SneakyThrows
    void readsAResolvedParentFromTheLocalRepositoryWhenItHasNoProjectFile() {
        Path repository = this.directory.resolve("repository");
        Path parentPom = repository.resolve("sample/remote-parent/1.0.0/remote-parent-1.0.0.pom");
        Files.createDirectories(parentPom.getParent());
        Files.writeString(parentPom, this.pom("remote-library", "1.2.3"));
        MavenProject parent = new MavenProject();
        parent.setGroupId("sample");
        parent.setArtifactId("remote-parent");
        parent.setVersion("1.0.0");
        MavenProject child = this.project("local-child", "<project/>", Optional.of(parent));

        List<OwnedCoordinate> coordinates = VersionCoordinates.from(List.of(child), repository);

        assertTrue(
            coordinates.stream().anyMatch(
                coordinate -> "remote-library".equals(coordinate.coordinate().artifactId())
            )
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
