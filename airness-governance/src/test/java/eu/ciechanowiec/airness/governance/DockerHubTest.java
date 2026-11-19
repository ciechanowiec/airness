package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Docker Hub's tag count can lag the tags it counts in either direction, so the listing is read page after page
 * until one comes back short, and only a listing that holds fewer tags than the count is refused.
 */
class DockerHubTest {

    private static final int HTTP_OK = 200;
    private static final int PAGE_SIZE = 100;
    private static final DockerReference IMAGE = DockerReference.from(
        new DeclaredContainerImage("scanner.image", "jetbrains/qodana-jvm-community:2026.1@sha256:" + "1".repeat(64))
    );

    private final List<HttpServer> started;

    DockerHubTest() {
        this.started = new ArrayList<>();
    }

    @AfterEach
    void stopRegistries() {
        this.started.forEach(server -> server.stop(0));
    }

    @Test
    void keepsEveryTagOfAListingThatHoldsMoreThanItsCount() {
        List<String> tags = List.of("2025.3", "2026.1", "2026.2");
        assertEquals(tags, new DockerHub(this.hub(1, List.of(tags))).tags(IMAGE));
    }

    @Test
    void readsThePageAFullOneLeadsToEvenWhenTheCountEndsBeforeIt() {
        List<String> full = IntStream.range(0, PAGE_SIZE).mapToObj(patch -> "2024." + patch).toList();
        List<String> rest = List.of("2026.1", "2026.2");
        assertEquals(
            Stream.concat(full.stream(), rest.stream()).toList(),
            new DockerHub(this.hub(PAGE_SIZE, List.of(full, rest))).tags(IMAGE)
        );
    }

    @Test
    void stopsAtAShortPage() {
        List<String> tags = List.of("2026.1");
        assertEquals(tags, new DockerHub(this.hub(1, List.of(tags, List.of("never-asked")))).tags(IMAGE));
    }

    // A hub whose listing states the given count and serves the given pages in turn.
    @SneakyThrows
    private String hub(int count, List<List<String>> pages) {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.started.add(server);
        server.createContext("/", exchange -> respond(exchange, listing(count, pageOf(pages, exchange))));
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static List<String> pageOf(List<List<String>> pages, HttpExchange exchange) {
        String query = Optional.ofNullable(exchange.getRequestURI().getQuery()).orElse("");
        int page = Integer.parseInt(query.replaceAll(".*page=([0-9]+).*", "$1"));
        return page <= pages.size() ? pages.get(page - 1) : List.of();
    }

    private static String listing(int count, List<String> tags) {
        String results = String.join(",", tags.stream().map(tag -> "{\"name\":\"" + tag + "\"}").toList());
        return "{\"count\":" + count + ",\"results\":[" + results + "]}";
    }

    @SneakyThrows
    private static void respond(HttpExchange exchange, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HTTP_OK, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
