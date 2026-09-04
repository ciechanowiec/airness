package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The freshness bound stops short of a newest release the blocklist refuses, so a project on the last
 * open release of a coordinate is never failed for declining the one it may not declare.
 */
class DependencyFreshnessRefusalTest {

    private static final int HTTP_OK = 200;
    private static final String METADATA = """
        <metadata>
            <versioning>
                <versions>
                    <version>7.10.2</version>
                    <version>9.0.0</version>
                </versions>
            </versioning>
        </metadata>
        """;

    private final List<HttpServer> started;

    DependencyFreshnessRefusalTest() {
        this.started = new ArrayList<>();
    }

    @AfterEach
    void stopTheRegistry() {
        this.started.forEach(active -> active.stop(0));
    }

    @SneakyThrows
    private String registry() {
        HttpServer active = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.started.add(active);
        active.createContext("/", DependencyFreshnessRefusalTest::respond);
        active.start();
        return "http://127.0.0.1:" + active.getAddress().getPort() + "/";
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange) {
        byte[] bytes = METADATA.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HTTP_OK, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @Test
    void stopsShortOfANewestReleaseTheBlocklistRefuses() {
        DeclaredCoordinate client = new DeclaredCoordinate(
            "org.elasticsearch.client", "elasticsearch-rest-high-level-client", "7.10.2"
        );
        DependencyFreshnessCheck check = new DependencyFreshnessCheck(
            List.of(new OwnedCoordinate("pom.xml", client)), this.registry()
        );
        assertEquals(1, check.updates().size(), "the newest release is still reported");
        assertTrue(Verdicts.clean(check.findings()), "but the bound does not demand an upgrade into a refused release");
        assertEquals(1, check.refusedLatest().size(), "and a note says why");
        assertTrue(check.refusedLatest().getFirst().contains("9.0.0 is refused"), check.refusedLatest().getFirst());
    }
}
