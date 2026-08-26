package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Container freshness reports stable tag updates and mutable-tag digest drift without producing a
 * threshold verdict, while registry and declaration failures remain visible as check failures.
 */
class ContainerFreshnessCheckTest {

    private static final String CURRENT = "sha256:" + "1".repeat(64);
    private static final String LATEST = "sha256:" + "2".repeat(64);
    private static final String PLATFORM = "sha256:" + "3".repeat(64);
    private static final String CURRENT_TAG = "v8.30.0";
    private static final String TAGS_PATH = "/tags";
    private static final int HTTP_OK = 200;
    private static final int HTTP_FAILURE = 503;

    private final List<HttpServer> started;

    ContainerFreshnessCheckTest() {
        this.started = new ArrayList<>();
    }

    @AfterEach
    void stopRegistries() {
        this.started.forEach(server -> server.stop(0));
    }

    @Test
    void reportsTheNewestStableTagWithItsDigest() {
        String registry = this.registry(
            List.of(CURRENT_TAG, "v8.30.1", "v8.31.0-rc1", "latest"),
            Map.of(CURRENT_TAG, CURRENT, "v8.30.1", LATEST)
        );

        ContainerFreshnessCheck check = this.check(CURRENT_TAG, CURRENT, registry);

        assertEquals(1, check.scanned());
        assertEquals(
            List.of(
                "gitleaks.image zricethezav/gitleaks:v8.30.0@" + CURRENT
                    + " -> zricethezav/gitleaks:v8.30.1@" + LATEST
            ),
            check.updates()
        );
        assertTrue(check.drifts().isEmpty());
    }

    @Test
    void reportsNoUpdateOrDriftForACurrentImmutablePin() {
        ContainerFreshnessCheck check = this.check(
            CURRENT_TAG, CURRENT, this.registry(List.of(CURRENT_TAG), Map.of(CURRENT_TAG, CURRENT))
        );

        assertTrue(check.updates().isEmpty(), "there is deliberately no update finding or threshold");
        assertTrue(check.drifts().isEmpty());
        assertEquals(List.of(), check.updates(), "returned report collections are immutable copies");
    }

    @Test
    void reportsWhenTheHeldTagNowNamesADifferentDigest() {
        ContainerFreshnessCheck check = this.check(
            CURRENT_TAG, CURRENT, this.registry(List.of(CURRENT_TAG), Map.of(CURRENT_TAG, LATEST))
        );

        assertEquals(
            List.of(
                "gitleaks.image zricethezav/gitleaks:v8.30.0 is pinned to " + CURRENT
                    + " but now resolves to " + LATEST
            ),
            check.drifts()
        );
    }

    @Test
    void keepsCalendarTagsInsideTheirExactStableFamily() {
        String registry = this.registry(
            List.of("2026.1", "2027.1", "2027.1.1", "v2028.1", "2029.1-eap"),
            Map.of("2026.1", CURRENT, "2027.1", LATEST)
        );

        ContainerFreshnessCheck check = this.check("2026.1", CURRENT, registry);

        assertTrue(check.updates().getFirst().contains(":2027.1@" + LATEST));
    }

    @Test
    void rejectsAnUnversionedCurrentTag() {
        assertThrows(
            IllegalStateException.class,
            () -> this.check("latest", CURRENT, this.registry(List.of("latest"), Map.of()))
        );
    }

    @Test
    void rejectsARegistryFailureInsteadOfSilentlyReportingCurrent() {
        String registry = this.registry(HTTP_FAILURE, "unavailable");
        IllegalStateException failure = assertThrows(
            IllegalStateException.class, () -> this.check(CURRENT_TAG, CURRENT, registry)
        );
        assertTrue(failure.toString().contains(Integer.toString(HTTP_FAILURE)));
    }

    @Test
    void rejectsATagResponseWithoutADigest() {
        String registry = this.registry(List.of(CURRENT_TAG), Map.of());
        assertThrows(IllegalStateException.class, () -> this.check(CURRENT_TAG, CURRENT, registry));
    }

    @Test
    void rejectsAResponseWhoseCountDoesNotMatchItsTags() {
        String registry = this.registry(
            HTTP_OK, "{\"count\":2,\"results\":[{\"name\":\"" + CURRENT_TAG + "\"}]}"
        );
        assertThrows(IllegalStateException.class, () -> this.check(CURRENT_TAG, CURRENT, registry));
    }

    private ContainerFreshnessCheck check(String tag, String digest, String registry) {
        DeclaredContainerImage image = new DeclaredContainerImage(
            "gitleaks.image", "zricethezav/gitleaks:" + tag + '@' + digest
        );
        return new ContainerFreshnessCheck(List.of(image), registry);
    }

    private String registry(List<String> tags, Map<String, String> digests) {
        Responder responder = exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith(TAGS_PATH)) {
                String results = tags.stream()
                    .map(tag -> "{\"name\":\"" + tag + "\"}")
                    .reduce((left, right) -> left + ',' + right)
                    .orElse("");
                return new Response(HTTP_OK, "{\"count\":" + tags.size() + ",\"results\":[" + results + "]}");
            }
            String tag = path.substring(path.lastIndexOf('/') + 1);
            String body = Optional.ofNullable(digests.get(tag))
                .map(ContainerFreshnessCheckTest::tagBody)
                .orElse("{}");
            return new Response(HTTP_OK, body);
        };
        return this.registry(responder);
    }

    private String registry(int status, String body) {
        return this.registry(_ -> new Response(status, body));
    }

    @SneakyThrows
    private String registry(Responder responder) {
        HttpServer server = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0
        );
        this.started.add(server);
        server.createContext("/", exchange -> respond(exchange, responder.response(exchange)));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String tagBody(String digest) {
        return "{\"images\":[{\"digest\":\"" + PLATFORM + "\"}],\"digest\":\"" + digest + "\"}";
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange, Response response) {
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length);
        try (exchange) {
            exchange.getResponseBody().write(body);
        }
    }

    @FunctionalInterface
    private interface Responder {

        Response response(HttpExchange exchange);
    }

    private record Response(int status, String body) {
    }
}
