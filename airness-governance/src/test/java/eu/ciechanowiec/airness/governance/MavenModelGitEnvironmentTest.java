package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Git configuration a test reads belongs to the harness, so no project file can hand it back to the machine.
 */
class MavenModelGitEnvironmentTest {

    private static final List<String> RUNNERS = List.of("maven-surefire-plugin", "maven-failsafe-plugin");
    private static final List<String> VARIABLES = List.of("GIT_CONFIG_GLOBAL", "GIT_CONFIG_NOSYSTEM");

    @TempDir
    private Path directory;

    @Test
    void rejectsAChildThatRenamesTheGitConfigurationOfEitherTestRunner() {
        Stream<Executable> cases = RUNNERS.stream().flatMap(
            runner -> VARIABLES.stream().map(
                variable -> () -> assertEquals(
                    List.of(refusal(runner, variable)),
                    this.problems(runner, "<configuration>" + environment(variable) + "</configuration>")
                )
            )
        );
        assertAll(cases);
    }

    @Test
    void rejectsTheGitConfigurationRenamedInsideALifecycleExecution() {
        String execution = """
            <executions><execution>
                <id>default-test</id>
                <configuration>%s</configuration>
            </execution></executions>
            """.formatted(environment("GIT_CONFIG_GLOBAL"));
        assertEquals(
            List.of(refusal("maven-surefire-plugin", "GIT_CONFIG_GLOBAL")),
            this.problems("maven-surefire-plugin", execution)
        );
    }

    @Test
    void acceptsAnEnvironmentVariableOfTheProjectsOwn() {
        String configuration = "<configuration><environmentVariables><SERVICE_NAME>orders</SERVICE_NAME>"
            + "</environmentVariables></configuration>";
        assertTrue(this.problems("maven-surefire-plugin", configuration).isEmpty());
    }

    private static String environment(String variable) {
        return "<environmentVariables><%s>/elsewhere</%s></environmentVariables>".formatted(variable, variable);
    }

    private static String refusal(String runner, String variable) {
        return "Remove child " + runner + " configuration " + variable + "; it changes an Airness-owned check input";
    }

    @SneakyThrows
    private List<String> problems(String runner, String body) {
        String pom = """
            <project><build><plugins><plugin>
                <artifactId>%s</artifactId>%s
            </plugin></plugins></build></project>
            """.formatted(runner, body);
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }
}
