package eu.ciechanowiec.airness.governance;

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
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RegistryTagsTest {

    private static final String TOKEN_PATH = "/token";
    private static final String CURSOR = "last=";
    private static final int OK = 200;
    private static final int UNAVAILABLE = 503;
    private static final String TAGS = "/v2/example/image/tags/list";
    private static final int DIGEST_LENGTH = 64;
    private final List<HttpServer> servers;

    RegistryTagsTest() {
        this.servers = new ArrayList<>();
    }

    @AfterEach
    void stopServers() {
        this.servers.forEach(server -> server.stop(0));
    }

    @Test
    void readsEveryCursorPageWithPublicPullAuthorization() {
        String base = this.server(
            exchange -> {
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith(TOKEN_PATH)) {
                    return new Response(OK, "{\"token\":\"public\"}");
                }
                return page(exchange);
            }
        );
        assertEquals(List.of("1.0", "2.0", "3.0"), RegistryTags.forHub(base).tags(image()));
        assertEquals("https://registry-1.docker.io", RegistryTags.forHub("https://hub.docker.com").registry());
    }

    @Test
    void refusesRepeatedOrForeignPaginationAndMissingAuthorization() {
        String repeated = this.server(
            exchange -> {
                if (exchange.getRequestURI().getPath().endsWith(TOKEN_PATH)) {
                    return new Response(OK, "{\"token\":\"public\"}");
                }
                exchange.getResponseHeaders().set("Link", '<' + TAGS + "?n=1000>; rel=\"next\"");
                return new Response(OK, "{\"tags\":[\"1.0\"]}");
            }
        );
        assertThrows(IllegalStateException.class, () -> RegistryTags.forHub(repeated).tags(image()));
        String missing = this.server(_ -> new Response(OK, "{}"));
        assertThrows(IllegalStateException.class, () -> RegistryTags.forHub(missing).tags(image()));
        String failure = this.server(_ -> new Response(UNAVAILABLE, "unavailable"));
        assertThrows(IllegalStateException.class, () -> RegistryTags.forHub(failure).tags(image()));
    }

    @Test
    void rejectsMalformedNamesAndPreservesAnEmptyCatalogue() {
        assertEquals(List.of(), RegistryTags.names("{\"tags\":[]}"));
        assertEquals(List.of("1.0", "latest"), RegistryTags.names("{\"tags\":[\"1.0\", \"latest\"]}"));
        assertThrows(IllegalStateException.class, () -> RegistryTags.names("{}"));
        assertThrows(IllegalStateException.class, () -> RegistryTags.names("{\"tags\":[\"bad/name\"]}"));
        assertThrows(IllegalStateException.class, () -> RegistryTags.names("{\"tags\":[\"1.0\",]}"));
    }

    private static DockerReference image() {
        return DockerReference.from(
            new DeclaredContainerImage("image", "example/image:1.0@sha256:" + "a".repeat(DIGEST_LENGTH))
        );
    }

    private static Response page(HttpExchange exchange) {
        assertEquals("Bearer public", exchange.getRequestHeaders().getFirst("Authorization"));
        String query = Optional.ofNullable(exchange.getRequestURI().getQuery()).orElse("");
        if (!query.contains(CURSOR)) {
            exchange.getResponseHeaders().set("Link", '<' + TAGS + "?last=2.0&n=1000>; rel=\"next\"");
            return new Response(OK, "{\"tags\":[\"1.0\",\"2.0\"]}");
        }
        return new Response(OK, "{\"tags\":[\"3.0\"]}");
    }

    @SneakyThrows
    private String server(Reply reply) {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.servers.add(server);
        server.createContext("/", exchange -> respond(exchange, reply.response(exchange)));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange, Response response) {
        byte[] content = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), content.length);
        try (exchange) {
            exchange.getResponseBody().write(content);
        }
    }

    @FunctionalInterface
    private interface Reply {

        Response response(HttpExchange exchange);
    }

    private record Response(int status, String body) {
    }
}
