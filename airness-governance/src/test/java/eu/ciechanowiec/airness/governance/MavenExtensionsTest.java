package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The extensions reader finds a core extension and a build extension alike, reads a missing file as
 * none, and keeps an extension whose version the pom manages.
 */
class MavenExtensionsTest {

    @TempDir
    private Path directory;

    @SneakyThrows
    private Path write(String name, String content) {
        return Files.writeString(this.directory.resolve(name), content);
    }

    @Test
    void readsCoreExtensions() {
        String extensions = """
            <extensions>
                <extension>
                    <groupId>com.gradle</groupId>
                    <artifactId>develocity-maven-extension</artifactId>
                    <version>1.23</version>
                </extension>
            </extensions>
            """;
        Path file = this.write("extensions.xml", extensions);
        assertEquals(
            List.of(new DeclaredCoordinate("com.gradle", "develocity-maven-extension", "1.23")), MavenExtensions.in(
                file
            )
        );
    }

    @Test
    void readsBuildExtensionsAndToleratesAMissingVersion() {
        String project = """
            <project>
                <build>
                    <extensions>
                        <extension>
                            <groupId>com.gradle</groupId>
                            <artifactId>develocity-maven-extension</artifactId>
                        </extension>
                    </extensions>
                    <plugins>
                        <plugin>
                            <artifactId>some-plugin</artifactId>
                            <configuration>
                                <extension>xml</extension>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;
        Path pom = this.write("pom.xml", project);
        assertEquals(
            List.of(new DeclaredCoordinate("com.gradle", "develocity-maven-extension", "")), MavenExtensions.in(pom),
            "a configuration value named extension is not one"
        );
    }

    @Test
    void readsAMissingFileAsNone() {
        assertEquals(List.of(), MavenExtensions.in(this.directory.resolve("absent.xml")));
    }
}
