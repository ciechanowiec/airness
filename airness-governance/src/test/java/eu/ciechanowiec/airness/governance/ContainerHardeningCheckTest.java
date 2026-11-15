package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every compose project and Dockerfile of a tree is read, and an offence names the file and the line.
 */
class ContainerHardeningCheckTest {

    private static final String LIFTED = """
        services:
          app:
            image: app:1.0.0
            privileged: true
            ports:
              - "8080:80"
            environment:
              APP_TOKEN: fixed
        """;
    private static final String HARDENED = """
        services:
          app:
            image: app:1.0.0
            ports:
              - "127.0.0.1:${APP_PORT}:80"
            environment:
              APP_TOKEN: ${APP_TOKEN}
        """;
    private static final String BAKED = "FROM node:26.8.2-alpine\nARG NPM_TOKEN\n";
    private static final String CLEAN = "FROM node:26.8.2-alpine\nUSER 1000:1000\n";

    @Test
    void namesTheFileAndLineOfEveryOffence() {
        Path root = new GitFixture("container-hardening-lifted")
            .write("deploy/compose.yaml", LIFTED)
            .write("compose.yaml", HARDENED)
            .write("web/Dockerfile", BAKED)
            .write("Dockerfile", CLEAN)
            .root();
        ContainerHardeningCheck check = new ContainerHardeningCheck(root);
        String deploy = Path.of("deploy", "compose.yaml").toString();
        assertAll(
            () -> assertEquals("2 compose project(s) and 2 Dockerfile(s)", check.scanned()),
            () -> assertEquals(
                List.of(deploy + ":4: app runs privileged"),
                Verdicts.offences(check.findings(), "lift the isolation of their container")
            ),
            () -> assertEquals(
                List.of(deploy + ":6: app publishes 8080:80 beyond loopback"),
                Verdicts.offences(check.findings(), "beyond the host's loopback interface")
            ),
            () -> assertEquals(
                List.of(deploy + ":8: app gives APP_TOKEN a value"),
                Verdicts.offences(check.findings(), "credentials the file writes")
            ),
            () -> assertEquals(
                List.of(Path.of("web", "Dockerfile") + ":2: ARG NPM_TOKEN"),
                Verdicts.offences(check.findings(), "declare a credential as a build argument")
            )
        );
    }

    @Test
    void passesATreeWhoseContainersKeepTheirIsolation() {
        Path root = new GitFixture("container-hardening-clean")
            .write("compose.yaml", HARDENED)
            .write("Dockerfile", CLEAN)
            .root();
        assertTrue(Verdicts.clean(new ContainerHardeningCheck(root).findings()));
    }

    @Test
    void readsFilesGivenByTheirText() {
        assertAll(
            () -> assertEquals(
                List.of("compose.yaml.fixture:4: app runs privileged"),
                Verdicts.offences(
                    ContainerHardeningCheck.composeFindings("compose.yaml.fixture", LIFTED),
                    "lift the isolation of their container"
                )
            ),
            () -> assertEquals(
                List.of("starter/Dockerfile:2: ARG NPM_TOKEN"),
                ContainerHardeningCheck.dockerfileFindings("starter/Dockerfile", BAKED).offences()
            )
        );
    }
}
