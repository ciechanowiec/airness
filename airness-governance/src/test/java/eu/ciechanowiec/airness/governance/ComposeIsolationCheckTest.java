package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every compose project of a tree is read for what it fixes, and an offence names the file and the line.
 */
class ComposeIsolationCheckTest {

    private static final String FIXED = """
        services:
          db:
            image: postgres:18-alpine
            container_name: shop-db
            ports:
              - "5432:5432"
        """;
    private static final String CHOSEN = """
        services:
          db:
            image: postgres:18-alpine
            ports:
              - "127.0.0.1:${DB_PORT}:5432"
        """;

    @Test
    void namesTheFileAndLineOfEveryFixedPortAndContainerName() {
        Path root = new GitFixture("compose-isolation-fixed")
            .write("deploy/compose.yaml", FIXED)
            .write("compose.yaml", CHOSEN)
            .root();
        ComposeIsolationCheck check = new ComposeIsolationCheck(root);
        String deploy = Path.of("deploy", "compose.yaml").toString();
        assertAll(
            () -> assertEquals(2, check.scanned()),
            () -> assertEquals(
                List.of(deploy + ":6: db publishes host port 5432"),
                Verdicts.offences(check.findings(), "host port every checkout asks for")
            ),
            () -> assertEquals(
                List.of(deploy + ":4: db is named shop-db"),
                Verdicts.offences(check.findings(), "container name every checkout asks for")
            )
        );
    }

    @Test
    void passesATreeWhoseComposeProjectsLeaveTheirPortsAndNamesToTheirStart() {
        Path root = new GitFixture("compose-isolation-chosen").write("compose.yaml", CHOSEN).root();
        assertTrue(Verdicts.clean(new ComposeIsolationCheck(root).findings()));
    }

    @Test
    void readsAFileGivenByItsText() {
        assertEquals(
            List.of("compose.yaml.fixture:6: db publishes host port 5432"),
            Verdicts.offences(
                ComposeIsolationCheck.findings("compose.yaml.fixture", FIXED), "host port every checkout asks for"
            )
        );
    }
}
