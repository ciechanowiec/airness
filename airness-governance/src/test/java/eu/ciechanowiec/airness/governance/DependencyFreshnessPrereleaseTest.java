package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What the version check counts as a release worth moving to. A Multi-Release JAR qualifier repackages
 * a release rather than superseding it, and Maven orders it above the release it repackages, so a report
 * that took the newest version at its word would name it. A release candidate is a candidate whichever
 * separator writes it, and only the hyphen was read before, so a project whose newest version was written
 * with a dot was offered a candidate as though it were a release.
 */
class DependencyFreshnessPrereleaseTest {

    private static final int HTTP_OK = 200;
    private static final String DECLARED = "1.0.3";

    private final List<HttpServer> started;

    DependencyFreshnessPrereleaseTest() {
        this.started = new ArrayList<>();
    }

    @AfterEach
    void stopTheRegistry() {
        this.started.forEach(active -> active.stop(0));
    }

    @Test
    void skipsAMultiReleaseJarQualifier() {
        assertEquals(
            0, this.check(DECLARED, "2.0.1.MR").updates().size(),
            "a Multi-Release JAR repackages a release rather than superseding it"
        );
    }

    @Test
    void skipsAReleaseCandidateWrittenWithADot() {
        assertEquals(
            0, this.check(DECLARED, "2.0.1.RC1").updates().size(),
            "a release candidate is one whichever separator writes it"
        );
    }

    @Test
    void reportsTheReleaseThatAMultiReleaseJarRepackages() {
        DependencyFreshnessCheck check = this.check(DECLARED, "2.0.1", "2.0.1.MR");
        assertEquals(1, check.updates().size(), "the plain release is still an update");
        assertEquals(
            "2.0.1", check.updates().getFirst().latest(),
            "and it is the one reported, rather than the qualifier Maven orders above it"
        );
    }

    private DependencyFreshnessCheck check(String... available) {
        DeclaredCoordinate coordinate = new DeclaredCoordinate("sample", "library", DECLARED);
        return new DependencyFreshnessCheck(
            List.of(new OwnedCoordinate("pom.xml", coordinate)), this.registry(available)
        );
    }

    @SneakyThrows
    private String registry(String... versions) {
        HttpServer active = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.started.add(active);
        active.createContext("/", exchange -> respond(exchange, metadata(versions)));
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

    private static String metadata(String... versions) {
        String listed = Arrays.stream(versions)
            .map("<version>%s</version>"::formatted)
            .collect(Collectors.joining());
        return """
            <metadata>
                <versioning>
                    <versions>%s</versions>
                </versioning>
            </metadata>
            """.formatted(listed);
    }
}
