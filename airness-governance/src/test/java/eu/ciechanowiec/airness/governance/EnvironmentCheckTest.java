package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a compose file's variables must reach the example file and every verifying workflow.
 */
class EnvironmentCheckTest {

    private static final String COMPOSE = """
        services:
          db:
            image: postgres:17.5
            environment:
              POSTGRES_PASSWORD: ${DB_PASSWORD}
              POSTGRES_USER: ${DB_USER}
              POSTGRES_PORT: ${DB_PORT:-5432}
        """;
    private static final String VERIFYING = """
        on: push
        jobs:
          verify:
            runs-on: ubuntu-latest
            env:
              DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
            steps:
              - run: mvn --batch-mode verify
        """;

    @Test
    void namesEveryVariableTheExampleFileDoesNotDocument() {
        Path root = new GitFixture("environment-undocumented")
            .write("deploy/compose.yaml", COMPOSE)
            .write(".env.example", "DB_PASSWORD=\n")
            .write(".env.sample", "DB_USER=\n")
            .root();
        assertEquals(
            List.of("DB_USER in " + Path.of("deploy", "compose.yaml")),
            Verdicts.offences(new EnvironmentCheck(root).findings(), "no example file documents"),
            "only the first example file is read, and a variable with a default needs no documentation"
        );
    }

    @Test
    void namesEveryVariableAVerifyingWorkflowDoesNotSupply() {
        Path root = new GitFixture("environment-unsupplied")
            .write("compose.yaml", COMPOSE)
            .write(".env.example", "DB_PASSWORD=\nDB_USER=\n")
            .write(".github/workflows/verify.yml", VERIFYING)
            .write(".github/workflows/label.yml", "on: issues\njobs: {}\n")
            .root();
        assertEquals(
            List.of("DB_USER in " + Path.of(".github", "workflows", "verify.yml")),
            Verdicts.offences(new EnvironmentCheck(root).findings(), "verifying workflow does not supply"),
            "a workflow that runs verification owes every compose variable, and one that does not owes none"
        );
    }

    @Test
    void reportsEveryVariableWhenThereIsNoExampleFile() {
        Path root = new GitFixture("environment-no-example").write("compose.yaml", COMPOSE).root();
        assertEquals(
            List.of("DB_PASSWORD in compose.yaml", "DB_USER in compose.yaml"),
            Verdicts.offences(new EnvironmentCheck(root).findings(), "no example file documents"),
            "a repository with no example file documents nothing"
        );
    }

    @Test
    void passesARepositoryWithNoComposeFile() {
        Path root = new GitFixture("environment-none").write(".github/workflows/verify.yml", VERIFYING).root();
        assertTrue(Verdicts.clean(new EnvironmentCheck(root).findings()), "nothing interpolates, so nothing is owed");
    }
}
