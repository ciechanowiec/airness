package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The plugin configuration that reaches an analysis the parents bind, kept apart from
 * {@link MavenModelPolicyTest} so neither class outgrows the method cap.
 */
class MavenModelPolicyAnalysisTest {

    @TempDir
    private Path directory;

    @Test
    void rejectsDependencyAnalysisConfigurationThatSilencesAFinding() {
        String ignored = plugin(
            "maven-dependency-plugin",
            """
                <configuration>
                    <ignoredUnusedDeclaredDependencies>
                        <ignoredUnusedDeclaredDependency>com.example:unused</ignoredUnusedDeclaredDependency>
                    </ignoredUnusedDeclaredDependencies>
                </configuration>
                """
        );
        String packaging = plugin(
            "maven-dependency-plugin",
            """
                <executions><execution>
                    <id>default-cli</id>
                    <configuration><ignoredPackagings>jar</ignoredPackagings></configuration>
                </execution></executions>
                """
        );
        assertAll(
            () -> assertEquals(
                List.of(
                    "Remove child maven-dependency-plugin configuration ignoredUnusedDeclaredDependencies; "
                        + "it changes an Airness-owned check input"
                ),
                this.problems(ignored)
            ),
            () -> assertEquals(
                List.of(
                    "Remove child maven-dependency-plugin configuration ignoredPackagings; "
                        + "it changes an Airness-owned check input"
                ),
                this.problems(packaging)
            )
        );
    }

    @Test
    void acceptsADependencyCopyTheProjectBindsUnderItsOwnName() {
        String pom = plugin(
            "maven-dependency-plugin",
            """
                <executions><execution>
                    <id>copy-agent</id>
                    <goals><goal>copy</goal></goals>
                    <configuration><skip>false</skip></configuration>
                </execution></executions>
                """
        );
        assertEquals(List.of(), this.problems(pom), "an execution of the project's own binds beside the analysis");
    }

    @Test
    void rejectsTestConfigurationThatRetriesOrDropsAnEngine() {
        String reruns = plugin(
            "maven-surefire-plugin", "<configuration><rerunFailingTestsCount>3</rerunFailingTestsCount></configuration>"
        );
        String engines = plugin(
            "maven-failsafe-plugin",
            "<configuration><excludeJUnit5Engines>junit-jupiter</excludeJUnit5Engines></configuration>"
        );
        String compiler = plugin("maven-compiler-plugin", "<configuration><skipMain>true</skipMain></configuration>");
        assertAll(
            () -> assertEquals(1, this.problems(reruns).size(), "a rerun turns a flaky failure into a pass"),
            () -> assertEquals(1, this.problems(engines).size(), "an excluded engine runs none of its tests"),
            () -> assertEquals(1, this.problems(compiler).size(), "a skipped compilation leaves nothing to analyse")
        );
    }

    @SneakyThrows
    private List<String> problems(String pom) {
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }

    private static String plugin(String artifact, String body) {
        return "<project><build><plugins><plugin><artifactId>" + artifact + "</artifactId>" + body
            + "</plugin></plugins></build></project>";
    }
}
