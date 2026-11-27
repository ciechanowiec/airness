package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spring Cloud Gateway brings its web stack through a starter of its own, so a gateway that declares only
 * that starter is read as serving HTTP, and the rules that ask a deployed service for security and for
 * probes ask the gateway as well.
 */
class SpringModelGatewayTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));
    private static final String POM = "pom.xml";
    private static final String AUTHENTICATION = "nothing that authenticates a caller";
    private static final String ACTUATOR = "publishing nothing an orchestrator can read";

    private static List<Findings> findings(String artifact) {
        Path root = new GitFixture("model-gateway-" + artifact).write(POM, "<project/>\n").root();
        List<SpringDependency> dependencies = List.of(
            new SpringDependency("org.springframework.cloud", artifact, false)
        );
        return new SpringModelCheck(root.resolve(POM), RESOURCES, dependencies, true).findings();
    }

    @Test
    void asksEachGatewayStarterForSecurityAndProbes() {
        List.of(
            "spring-cloud-starter-gateway-server-webmvc", "spring-cloud-starter-gateway-mvc"
        ).forEach(
            artifact -> {
                List<Findings> findings = findings(artifact);
                assertEquals(
                    1, Verdicts.offences(findings, AUTHENTICATION).size(),
                    artifact + " serves HTTP, so a gateway declaring it alone is asked for security"
                );
                assertEquals(
                    1, Verdicts.offences(findings, ACTUATOR).size(),
                    artifact + " serves HTTP, so a gateway declaring it alone is asked for probes"
                );
            }
        );
    }

    @Test
    void leavesASpringCloudStarterThatServesNothing() {
        assertEquals(
            List.of(),
            Verdicts.offences(findings("spring-cloud-starter-config"), AUTHENTICATION),
            "a configuration client serves no HTTP of its own, so nobody is there to authenticate"
        );
    }
}
