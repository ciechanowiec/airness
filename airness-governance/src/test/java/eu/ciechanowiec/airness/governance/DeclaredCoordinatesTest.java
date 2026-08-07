package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeclaredCoordinatesTest {

    private static final String SAMPLE = "sample";
    private static final String ALL_DECLARATION_SITES = """
        <project>
            <properties>
                <release>1.2.3</release>
            </properties>
            <parent>
                <groupId>sample</groupId>
                <artifactId>parent</artifactId>
                <version>${release}</version>
            </parent>
            <dependencyManagement>
                <dependencies>
                    <dependency>
                        <groupId>sample</groupId>
                        <artifactId>managed</artifactId>
                        <version>${release}</version>
                    </dependency>
                </dependencies>
            </dependencyManagement>
            <build>
                <plugins>
                    <plugin>
                        <artifactId>compiler</artifactId>
                        <version>${release}</version>
                        <dependencies>
                            <dependency>
                                <groupId>sample</groupId>
                                <artifactId>plugin-library</artifactId>
                                <version>${release}</version>
                            </dependency>
                        </dependencies>
                        <configuration>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>sample</groupId>
                                    <artifactId>processor</artifactId>
                                    <version>${release}</version>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
            <reporting>
                <plugins>
                    <plugin>
                        <groupId>sample</groupId>
                        <artifactId>report</artifactId>
                        <version>${release}</version>
                    </plugin>
                </plugins>
            </reporting>
        </project>
        """;

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
            List.of(new DeclaredCoordinate(SAMPLE, "generator", "1.0.0")),
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
                new DeclaredCoordinate(SAMPLE, "first", "2.0.0"),
                new DeclaredCoordinate(SAMPLE, "second", "3.0.0")
            ),
            coordinates
        );
    }

    @Test
    void readsEveryMavenDeclarationSite() {
        List<DeclaredCoordinate> coordinates = this.coordinates(ALL_DECLARATION_SITES);
        assertTrue(coordinates.contains(new DeclaredCoordinate(SAMPLE, "parent", "1.2.3")));
        assertTrue(coordinates.contains(new DeclaredCoordinate(SAMPLE, "managed", "1.2.3")));
        assertTrue(coordinates.contains(new DeclaredCoordinate(SAMPLE, "plugin-library", "1.2.3")));
        assertTrue(coordinates.contains(new DeclaredCoordinate(SAMPLE, "processor", "1.2.3")));
        assertTrue(
            coordinates.contains(
                new DeclaredCoordinate("org.apache.maven.plugins", "compiler", "1.2.3")
            )
        );
        assertTrue(coordinates.contains(new DeclaredCoordinate(SAMPLE, "report", "1.2.3")));
    }

    @Test
    @SneakyThrows
    void overlaysEffectivePropertiesWithoutErasingProfileProperties() {
        Path pom = Files.writeString(
            this.directory.resolve("pom.xml"),
            """
                <project>
                    <properties>
                        <release>1.0.0</release>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>sample</groupId>
                            <artifactId>direct</artifactId>
                            <version>${release}</version>
                        </dependency>
                    </dependencies>
                    <profiles>
                        <profile>
                            <properties>
                                <release>3.0.0</release>
                            </properties>
                            <dependencies>
                                <dependency>
                                    <groupId>sample</groupId>
                                    <artifactId>profile</artifactId>
                                    <version>${release}</version>
                                </dependency>
                            </dependencies>
                        </profile>
                    </profiles>
                </project>
                """
        );

        assertEquals(
            List.of(
                new DeclaredCoordinate(SAMPLE, "direct", "2.0.0"),
                new DeclaredCoordinate(SAMPLE, "profile", "3.0.0")
            ),
            DeclaredCoordinates.from(pom, Map.of("release", "2.0.0"))
        );
    }

    @SneakyThrows
    private List<DeclaredCoordinate> coordinates(CharSequence pom) {
        Path file = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return DeclaredCoordinates.from(file);
    }
}
