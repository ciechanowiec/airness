package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A module's coordinates pair what its raw pom declares, resolved through the effective properties and
 * joined by its build extensions, with what Maven resolved in a stable order.
 */
class ModuleCoordinatesTest {

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void readsDeclarationsThroughEffectivePropertiesAndOrdersTheResolvedSet() {
        String project = """
            <project>
                <build>
                    <extensions>
                        <extension>
                            <groupId>com.gradle</groupId>
                            <artifactId>develocity-maven-extension</artifactId>
                        </extension>
                    </extensions>
                </build>
                <dependencies>
                    <dependency>
                        <groupId>org.mongodb</groupId>
                        <artifactId>bson</artifactId>
                        <version>${mongo.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;
        Path pom = Files.writeString(this.directory.resolve("pom.xml"), project);
        List<DeclaredCoordinate> resolved = List.of(
            new DeclaredCoordinate("org.postgresql", "postgresql", "42.7.4"),
            new DeclaredCoordinate("org.mongodb", "bson", "5.3.0"),
            new DeclaredCoordinate("org.mongodb", "bson", "5.3.0")
        );
        ModuleCoordinates coordinates = ModuleCoordinates.of(pom, Map.of("mongo.version", "5.3.0"), resolved);
        assertEquals(
            List.of(
                new DeclaredCoordinate("org.mongodb", "bson", "5.3.0"),
                new DeclaredCoordinate("com.gradle", "develocity-maven-extension", "")
            ),
            coordinates.declared(),
            "the inherited property resolves, and the extension follows the dependencies"
        );
        assertEquals(
            List.of(
                new DeclaredCoordinate("org.mongodb", "bson", "5.3.0"),
                new DeclaredCoordinate("org.postgresql", "postgresql", "42.7.4")
            ),
            coordinates.resolved(),
            "the resolved set is distinct and ordered by coordinate"
        );
    }
}
