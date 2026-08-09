package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedVersionsTest {

    private static final String CHILD_POM = """
        <project>
            <build>
                <plugins>
                    <plugin>
                        <artifactId>maven-shade-plugin</artifactId>
                        <version>1</version>
                    </plugin>
                    <plugin>
                        <artifactId>maven-enforcer-plugin</artifactId>
                    </plugin>
                    <plugin>
                        <artifactId>maven-source-plugin</artifactId>
                    </plugin>
                    <plugin>
                        <groupId>org.jacoco</groupId>
                        <artifactId>jacoco-maven-plugin</artifactId>
                    </plugin>
                    <plugin>
                        <groupId>org.codehaus.mojo</groupId>
                        <artifactId>versions-maven-plugin</artifactId>
                    </plugin>
                    <plugin>
                        <groupId>org.owasp</groupId>
                        <artifactId>dependency-check-maven</artifactId>
                    </plugin>
                    <plugin>
                        <artifactId>unrelated-plugin</artifactId>
                        <version>1</version>
                    </plugin>
                </plugins>
            </build>
            <profiles>
                <profile>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </profile>
            </profiles>
        </project>
        """;

    @TempDir
    private Path directory;

    @Test
    void rejectsVersionsOnChildExtensionPlugins() {
        List<String> problems = this.problems(CHILD_POM);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("maven-shade-plugin")));
    }

    @Test
    void rejectsDeclarationsOfHarnessSuppliedCoordinates() {
        List<String> problems = this.problems(CHILD_POM);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("jacoco-maven-plugin")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("dependency-check-maven")));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("lombok")));
    }

    @Test
    void acceptsUnversionedExtensionPluginsAndUnrelatedCoordinates() {
        List<String> problems = this.problems(CHILD_POM);
        assertFalse(problems.stream().anyMatch(problem -> problem.contains("maven-enforcer-plugin")));
        assertFalse(problems.stream().anyMatch(problem -> problem.contains("maven-source-plugin")));
        assertFalse(problems.stream().anyMatch(problem -> problem.contains("unrelated-plugin")));
    }

    // The parent pins this version without binding the plugin to anything, so the child is the only one
    // who can bind it. Forbidding the declaration left a pinned version nobody was allowed to use, while
    // the message told the child that the harness supplied a plugin the harness never ran.
    @Test
    void acceptsAnExtensionPluginTheParentOnlyPinsAVersionFor() {
        List<String> problems = this.problems(CHILD_POM);
        assertFalse(
            problems.stream().anyMatch(problem -> problem.contains("versions-maven-plugin")),
            "a plugin airness-parent does not declare is the child's to bind: " + problems
        );
    }

    @Test
    void rejectsEveryProtectedProperty() {
        assertAll(
            ManagedVersions.protectedProperties().stream()
                .map(property -> () -> this.assertPropertyRejected(property))
        );
    }

    @Test
    void ignoresCoordinatesThatBelongToPluginConfiguration() {
        String pom = """
            <project>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>sample</groupId>
                            <artifactId>generator</artifactId>
                            <configuration>
                                <dependency>
                                    <groupId>org.projectlombok</groupId>
                                    <artifactId>lombok</artifactId>
                                    <version>1</version>
                                </dependency>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;
        assertTrue(this.problems(pom).isEmpty(), "plugin-specific XML is not a Maven dependency declaration");
    }

    private void assertPropertyRejected(String property) {
        String pom = "<project><properties><%s>1</%s></properties></project>".formatted(property, property);
        assertEquals(1, this.problems(pom).size(), property);
    }

    @SneakyThrows
    private List<String> problems(CharSequence pom) {
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return ManagedVersions.problems(path);
    }
}
