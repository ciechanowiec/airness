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
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The version check asks a registry about every stable dependency, plugin, and parent, reports every
 * update, and fails rather than passing when the registry cannot be read.
 *
 * <p>The registry here is a real HTTP server on a loopback port, which is what makes the last of those
 * assertable: a check that could only ever reach one public host is a check nobody can watch fail, and
 * an outage that read as a green build is the one outcome this must not have.
 */
class DependencyFreshnessCheckTest {

    private static final int HTTP_OK = 200;
    private static final String PLUGIN_POM = """
        <project>
            <profiles>
                <profile>
                    <properties>
                        <sample-plugin.version>1.0.0</sample-plugin.version>
                    </properties>
                    <build>
                        <pluginManagement>
                            <plugins>
                                <plugin>
                                    <groupId>sample</groupId>
                                    <artifactId>build-plugin</artifactId>
                                    <version>${sample-plugin.version}</version>
                                </plugin>
                            </plugins>
                        </pluginManagement>
                    </build>
                </profile>
            </profiles>
        </project>
        """;

    @TempDir
    private Path directory;

    private Optional<HttpServer> server;

    DependencyFreshnessCheckTest() {
        this.server = Optional.empty();
    }

    @AfterEach
    void stopTheRegistry() {
        this.server.ifPresent(active -> active.stop(0));
    }

    @SneakyThrows
    private String registry(String latest) {
        HttpServer active = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server = Optional.of(active);
        active.createContext("/", exchange -> respond(exchange, metadata(latest)));
        active.start();
        return "http://127.0.0.1:" + active.getAddress().getPort() + "/";
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HTTP_OK, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @SneakyThrows
    private DependencyFreshnessCheck check(String declared, String registry) {
        return this.checkPom(pom(declared), registry);
    }

    @SneakyThrows
    private DependencyFreshnessCheck checkPom(CharSequence content, String registry) {
        Path pom = Files.writeString(this.directory.resolve("pom.xml"), content);
        return new DependencyFreshnessCheck(pom, registry);
    }

    @Test
    void passesWhenTheDeclaredMajorIsWithinTheBound() {
        DependencyFreshnessCheck check = this.check("1.0.0", this.registry("2.4.0"));
        assertEquals(1, check.scanned(), "the one stable dependency is checked");
        assertEquals(1, check.updates().size(), "and its available major update is reported");
        assertTrue(Verdicts.clean(check.findings()), "and trailing by one major is within the bound");
    }

    @Test
    void reportsAMinorUpdateWithoutFailingIt() {
        DependencyFreshnessCheck check = this.check("1.0.0", this.registry("1.1.0"));
        assertEquals(1, check.updates().size(), "every stable update belongs in the report");
        assertTrue(Verdicts.clean(check.findings()), "while a minor update is not a freshness offence");
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
    void reportsAPluginFromAnInactiveManagementProfile() {
        List<String> offences = Verdicts.offences(
            this.checkPom(PLUGIN_POM, this.registry("3.0.0")).findings(),
            "trailing"
        );
        assertEquals(1, offences.size(), "the managed plugin is part of the same freshness bound");
        assertTrue(offences.getFirst().contains("sample:build-plugin"), "the offence names the plugin");
    }

    @Test
    void leavesAVersionWithNoComparableMajorOutOfScope() {
        DependencyFreshnessCheck check = this.check("RELEASE", this.registry("3.0.0"));
        assertEquals(1, check.scanned(), "a stable named version still belongs in the update report");
        assertTrue(Verdicts.clean(check.findings()), "but it has no comparable major for the failing bound");
    }

    @Test
    void failsRatherThanPassesWhenTheRegistryCannotBeRead() {
        String unreachable = this.registry("3.0.0");
        this.server.orElseThrow().stop(0);
        UncheckedIOException thrown = assertThrows(
            UncheckedIOException.class, () -> this.check("1.0.0", unreachable),
            "a dependency whose latest release could not be read is not a dependency known to be current"
        );
        assertTrue(
            thrown.toString().contains(unreachable),
            "and it names the registry it could not reach, so the failure is not mistaken for a stale dependency"
        );
    }

    @Test
    void failsRatherThanSkippingAnUnresolvedVersionProperty() {
        String registry = this.registry("3.0.0");
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class, () -> this.check("${inherited.version}", registry)
        );
        assertTrue(thrown.toString().contains("Unresolved version"));
    }

    private static String pom(String version) {
        return """
            <project>
                <dependencies>
                    <dependency>
                        <groupId>sample</groupId>
                        <artifactId>library</artifactId>
                        <version>%s</version>
                    </dependency>
                </dependencies>
            </project>
            """.formatted(version);
    }

    private static String metadata(String version) {
        return """
            <metadata>
                <versioning>
                    <versions>
                        <version>1.0.0</version>
                        <version>%s</version>
                    </versions>
                </versioning>
            </metadata>
            """.formatted(version);
    }
}
