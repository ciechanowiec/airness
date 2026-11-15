package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The newest release of a base is read from Docker Hub, kept to the base's variant and major version, and written
 * as the Dockerfile wrote the repository.
 */
class BaseReleasesTest {

    private static final String DIGEST = "@sha256:" + "a".repeat(64);
    private static final int HTTP_OK = 200;
    private static final int HTTP_FAILURE = 503;

    private final List<HttpServer> started;
    private final List<String> asked;

    BaseReleasesTest() {
        this.started = new ArrayList<>();
        this.asked = new ArrayList<>();
    }

    @AfterEach
    void stopRegistries() {
        this.started.forEach(server -> server.stop(0));
    }

    @Test
    void namesTheNewestReleaseOfTheSameVariantAndMajorVersion() {
        BaseReleases releases = new BaseReleases(
            this.hub(
                HTTP_OK, List.of(
                    "26.8.2-alpine", "26.13.1-alpine", "26.9.0-alpine", "26.13.1-alpine3.24", "28.0.0-alpine",
                    "26-alpine", "latest"
                )
            )
        );
        assertAll(
            () -> assertEquals(Optional.of("node:26.13.1-alpine"), releases.newer("node:26.8.2-alpine" + DIGEST)),
            () -> assertEquals(
                List.of("/v2/namespaces/library/repositories/node/tags"), this.asked,
                "an official image is read from the library namespace"
            )
        );
    }

    @Test
    void writesTheRepositoryAsTheDockerfileWroteIt() {
        BaseReleases releases = new BaseReleases(this.hub(HTTP_OK, List.of("1.2.3", "1.4.0")));
        assertAll(
            () -> assertEquals(
                Optional.of("docker.io/bitnami/kit:1.4.0"), releases.newer("docker.io/bitnami/kit:1.2.3")
            ),
            () -> assertEquals("/v2/namespaces/bitnami/repositories/kit/tags", this.asked.getFirst())
        );
    }

    @Test
    void namesNothingWhereNoNewerReleaseCanReplaceTheBase() {
        BaseReleases releases = new BaseReleases(this.hub(HTTP_OK, List.of("26.8.2-alpine")));
        assertAll(
            () -> assertEquals(Optional.empty(), releases.newer("node:26.8.2-alpine")),
            () -> assertEquals(Optional.empty(), releases.newer("node:26-alpine"), "a moving tag is rebuilt in place"),
            () -> assertEquals(Optional.empty(), releases.newer("node" + DIGEST)),
            () -> assertEquals(Optional.empty(), releases.newer("node:${NODE_VERSION}")),
            () -> assertEquals(Optional.empty(), releases.newer("gcr.io/distroless/java25-debian13:1.2.3")),
            () -> assertEquals(Optional.empty(), releases.newer("localhost/kit:1.2.3")),
            () -> assertEquals(1, this.asked.size(), "only the reference to a Docker Hub release asks the registry")
        );
    }

    @Test
    void failsWhenDockerHubCannotBeRead() {
        BaseReleases releases = new BaseReleases(this.hub(HTTP_FAILURE, List.of()));
        assertThrows(IllegalStateException.class, () -> releases.newer("node:26.8.2-alpine"));
    }

    @SneakyThrows
    private String hub(int status, List<String> tags) {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.started.add(server);
        String results = tags.stream().map(tag -> "{\"name\":\"" + tag + "\"}").collect(Collectors.joining(","));
        String body = "{\"count\":" + tags.size() + ",\"results\":[" + results + "]}";
        server.createContext(
            "/", exchange -> {
                this.asked.add(exchange.getRequestURI().getPath());
                respond(exchange, status, body);
            }
        );
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
