package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclaredCoordinatesTest {

    @TempDir
    private Path directory;

    @Test
    void ignoresCoordinateShapedPluginConfiguration() {
        List<DeclaredCoordinate> coordinates = this.coordinates(
            """
                <project>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>sample</groupId>
                                <artifactId>generator</artifactId>
                                <version>1.0.0</version>
                                <configuration>
                                    <dependency>
                                        <groupId>org.projectlombok</groupId>
                                        <artifactId>lombok</artifactId>
                                        <version>1.0.0</version>
                                    </dependency>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
        );
        assertEquals(
            List.of(new DeclaredCoordinate("sample", "generator", "1.0.0")),
            coordinates,
            "only declarations in Maven's model structure are coordinates"
        );
    }

    @Test
    void resolvesPropertiesInTheProfileThatOwnsTheDeclaration() {
        List<DeclaredCoordinate> coordinates = this.coordinates(
            """
                <project>
                    <properties>
                        <library.version>1.0.0</library.version>
                    </properties>
                    <profiles>
                        <profile>
                            <id>first</id>
                            <properties>
                                <library.version>2.0.0</library.version>
                            </properties>
                            <dependencies>
                                <dependency>
                                    <groupId>sample</groupId>
                                    <artifactId>first</artifactId>
                                    <version>${library.version}</version>
                                </dependency>
                            </dependencies>
                        </profile>
                        <profile>
                            <id>second</id>
                            <properties>
                                <library.version>3.0.0</library.version>
                            </properties>
                            <dependencies>
                                <dependency>
                                    <groupId>sample</groupId>
                                    <artifactId>second</artifactId>
                                    <version>${library.version}</version>
                                </dependency>
                            </dependencies>
                        </profile>
                    </profiles>
                </project>
                """
        );
        assertEquals(
            List.of(
                new DeclaredCoordinate("sample", "first", "2.0.0"),
                new DeclaredCoordinate("sample", "second", "3.0.0")
            ),
            coordinates
        );
    }

    @SneakyThrows
    private List<DeclaredCoordinate> coordinates(CharSequence pom) {
        Path file = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return DeclaredCoordinates.from(file);
    }
}
