package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The freshness check asks a registry about every dependency that carries a comparable major, skips the
 * ones that do not, and fails rather than passing when the registry cannot be read.
 *
 * <p>The registry here is a real HTTP server on a loopback port, which is what makes the last of those
 * assertable: a check that could only ever reach one public host is a check nobody can watch fail, and
 * an outage that read as a green build is the one outcome this must not have.
 */
class DependencyFreshnessCheckTest {

    private static final String POM = """
        <project>
            <dependencies>
                <dependency>
                    <groupId>sample</groupId>
                    <artifactId>library</artifactId>
                    <version>%s</version>
                </dependency>
            </dependencies>
        </project>
        """;

    private static final String METADATA = """
        <metadata>
            <versioning>
                <versions>
                    <version>1.0.0</version>
                    <version>%s</version>
                </versions>
            </versioning>
        </metadata>
        """;

    @TempDir
    private Path directory;

    private HttpServer server;

    @AfterEach
    void stopTheRegistry() {
        this.server.stop(0);
    }

    @SneakyThrows
    private String registry(String latest) {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", exchange -> respond(exchange, METADATA.formatted(latest)));
        this.server.start();
        return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/";
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @SneakyThrows
    private DependencyFreshnessCheck check(String declared, String registry) {
        Path pom = Files.writeString(this.directory.resolve("pom.xml"), POM.formatted(declared));
        return new DependencyFreshnessCheck(pom, registry);
    }

    @Test
    void passesWhenTheDeclaredMajorIsWithinTheBound() {
        DependencyFreshnessCheck check = this.check("1.0.0", this.registry("2.4.0"));
        assertEquals(1, check.scanned(), "the one dependency carries a comparable major");
        assertTrue(Verdicts.clean(check.findings()), "and trailing by one major is within the bound");
    }

    @Test
    void reportsADependencyTrailingByTheBound() {
        List<String> offences = Verdicts.offences(
            this.check("1.0.0", this.registry("3.0.0")).findings(), "trailing"
        );
        assertEquals(1, offences.size(), "trailing by two majors is what the bound fails on");
        assertTrue(offences.getFirst().contains("sample:library"), "and the offence names it: " + offences);
    }

    @Test
    void leavesAVersionWithNoComparableMajorOutOfScope() {
        DependencyFreshnessCheck check = this.check("RELEASE", this.registry("3.0.0"));
        assertEquals(0, check.scanned(), "a version with no comparable level is not asked about");
        assertTrue(Verdicts.clean(check.findings()), "so it cannot be reported either");
    }

    @Test
    void failsRatherThanPassesWhenTheRegistryCannotBeRead() {
        String unreachable = this.registry("3.0.0");
        this.server.stop(0);
        UncheckedIOException thrown = assertThrows(
            UncheckedIOException.class, () -> this.check("1.0.0", unreachable).findings(),
            "a dependency whose latest release could not be read is not a dependency known to be current"
        );
        assertTrue(
            thrown.getMessage().contains(unreachable),
            "and it names the registry it could not reach, so the failure is not mistaken for a stale dependency"
        );
    }
}
