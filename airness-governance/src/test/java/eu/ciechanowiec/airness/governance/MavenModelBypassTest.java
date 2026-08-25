package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ways a project file can switch a verdict off without saying so.
 *
 * <p>A separate class from {@link MavenModelPolicyTest} because that one is at the method ceiling the
 * rule set sets, which is the same reason {@link ProjectFiles} stands apart from
 * {@link SelfHarnessMirrorTest}.
 *
 * <p>Each case here reported nothing before the rule it exercises existed. That is what makes them
 * worth pinning: a check that fails loudly gets fixed, while one that passes quietly is believed.
 */
class MavenModelBypassTest {

    @TempDir
    private Path directory;

    @Test
    void rejectsARepointedCoverageReport() {
        String pom = """
            <project>
                <properties><jacoco.reportFile>nowhere/jacoco.xml</jacoco.reportFile></properties>
            </project>
            """;
        assertEquals(
            List.of("Remove child property jacoco.reportFile; it can bypass the Airness verdict"),
            this.problems(pom),
            "a report path aimed at nothing leaves every coverage exclusion unexamined and reading clean"
        );
    }

    @Test
    void rejectsATestBypassInsideALifecycleExecution() {
        String pom = plugin(
            "maven-surefire-plugin",
            """
                <executions><execution>
                    <id>default-test</id>
                    <configuration><skip>true</skip></configuration>
                </execution></executions>
                """
        );
        assertTrue(
            this.problems(pom).stream().anyMatch(problem -> problem.contains("skip")),
            "Maven merges executions by id, so a default-* execution edits the inherited run rather than adding one"
        );
    }

    @Test
    void acceptsAnExecutionTheProjectAddsUnderANameOfItsOwn() {
        String pom = plugin(
            "maven-surefire-plugin",
            """
                <executions><execution>
                    <id>project-smoke-tests</id>
                    <configuration><skip>true</skip></configuration>
                </execution></executions>
                """
        );
        assertTrue(
            this.problems(pom).isEmpty(),
            "an execution bound beside the inherited one adds work rather than replacing what the harness bound"
        );
    }

    @Test
    void readsAPropertyBlockThatNamesOneNameTwice() {
        String pom = """
            <project>
                <properties><a.version>1</a.version><a.version>2</a.version></properties>
            </project>
            """;
        assertTrue(
            this.problems(pom).isEmpty(),
            "a repeated name is well-formed XML that Maven accepts, so reading it must not end every model rule"
        );
    }

    private static String plugin(String artifact, String body) {
        return """
            <project><build><plugins><plugin>
                <artifactId>%s</artifactId>%s
            </plugin></plugins></build></project>
            """.formatted(artifact, body);
    }

    @SneakyThrows
    private List<String> problems(CharSequence pom) {
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }
}
