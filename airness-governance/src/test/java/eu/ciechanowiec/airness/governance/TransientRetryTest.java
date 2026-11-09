package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a registry request is sent again after a transient failure and never otherwise.
 */
class TransientRetryTest {

    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int UNAVAILABLE = 503;
    private static final Duration FIRST_PAUSE = Duration.ofMillis(10);
    private static final Duration SECOND_PAUSE = Duration.ofMillis(20);
    private static final Duration CAP = Duration.ofSeconds(5);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration SLOW_ANSWER = Duration.ofSeconds(2);
    private static final int ATTEMPTS = 3;

    private final List<HttpServer> servers;
    private final ExecutorService executor;
    private final List<Duration> waited;
    private final AtomicInteger requests;

    TransientRetryTest() {
        this.servers = new ArrayList<>();
        this.executor = Executors.newCachedThreadPool();
        this.waited = new ArrayList<>();
        this.requests = new AtomicInteger();
    }

    @AfterEach
    void stopServers() {
        this.servers.forEach(server -> server.stop(0));
        this.executor.shutdownNow();
    }

    @Test
    @SneakyThrows
    void answersAfterAServerErrorAndARateLimitWaitingAsTheRateLimitAsks() {
        URI uri = this.server(request -> switch (request) {
            case 1 -> new Answer(UNAVAILABLE, "");
            case 2 -> new Answer(TOO_MANY_REQUESTS, "1");
            default -> new Answer(OK, "");
        });
        HttpResponse<String> response = this.retry().send(client(), get(uri));
        assertEquals(OK, response.statusCode(), "the third attempt is answered");
        assertEquals(ATTEMPTS, this.requests.get(), "after two transient failures");
        assertEquals(List.of(FIRST_PAUSE, Duration.ofSeconds(1)), this.waited, "the rate limit sets its own wait");
    }

    @Test
    @SneakyThrows
    void waitsNoLongerThanTheCapWhateverARateLimitAsks() {
        URI uri = this.server(request -> request == 1 ? new Answer(TOO_MANY_REQUESTS, "120") : new Answer(OK, ""));
        assertEquals(OK, this.retry().send(client(), get(uri)).statusCode(), "the second attempt is answered");
        assertEquals(List.of(CAP), this.waited, "a two-minute Retry-After waits only as long as the cap");
    }

    @Test
    @SneakyThrows
    void returnsTheLastServerErrorForTheCallerToRefuseWhenEveryAttemptMeetsOne() {
        URI uri = this.server(_ -> new Answer(UNAVAILABLE, ""));
        assertEquals(UNAVAILABLE, this.retry().send(client(), get(uri)).statusCode(), "the last answer is returned");
        assertEquals(ATTEMPTS, this.requests.get(), "after every attempt was made");
        assertEquals(List.of(FIRST_PAUSE, SECOND_PAUSE), this.waited, "with the planned waits between them");
    }

    @Test
    @SneakyThrows
    void returnsAnyOtherAnswerAtOnce() {
        URI uri = this.server(_ -> new Answer(NOT_FOUND, ""));
        assertEquals(NOT_FOUND, this.retry().send(client(), get(uri)).statusCode(), "a 404 is the registry's answer");
        assertEquals(1, this.requests.get(), "so it is asked once");
        assertEquals(List.of(), this.waited, "and nothing is waited for");
    }

    @Test
    @SneakyThrows
    void answersAfterARequestThatTimedOut() {
        URI uri = this.server(request -> {
            if (request == 1) {
                pauseFor(SLOW_ANSWER);
            }
            return new Answer(OK, "");
        });
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(SHORT_TIMEOUT).GET().build();
        assertEquals(OK, this.retry().send(client(), request).statusCode(), "the attempt after the timeout answers");
        assertEquals(List.of(FIRST_PAUSE), this.waited, "after one planned wait");
    }

    @Test
    void failsNamingTheAttemptsWhenNoAttemptConnects() {
        URI uri = this.server(_ -> new Answer(OK, ""));
        this.servers.forEach(server -> server.stop(0));
        IOException failure = assertThrows(IOException.class, () -> this.retry().send(client(), get(uri)));
        String message = String.valueOf(failure.getMessage());
        assertTrue(message.contains("failed on each of 3 attempts"), message);
        assertEquals(List.of(FIRST_PAUSE, SECOND_PAUSE), this.waited, "with the planned waits between them");
    }

    private TransientRetry retry() {
        return new TransientRetry(List.of(FIRST_PAUSE, SECOND_PAUSE), CAP, this.waited::add);
    }

    private static HttpClient client() {
        return HttpClient.newHttpClient();
    }

    private static HttpRequest get(URI uri) {
        return HttpRequest.newBuilder(uri).GET().build();
    }

    @SneakyThrows
    private static void pauseFor(Duration duration) {
        Thread.sleep(duration);
    }

    @SneakyThrows
    private URI server(IntFunction<Answer> answers) {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(this.executor);
        server.createContext("/", exchange -> this.respond(exchange, answers));
        server.start();
        this.servers.add(server);
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/tags");
    }

    @SneakyThrows
    private void respond(HttpExchange exchange, IntFunction<Answer> answers) {
        Answer answer = answers.apply(this.requests.incrementAndGet());
        if (answer.status() == TOO_MANY_REQUESTS && !answer.retryAfter().isEmpty()) {
            exchange.getResponseHeaders().set("Retry-After", answer.retryAfter());
        }
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /**
     * What the fixture registry answers one request with.
     *
     * @param status     the HTTP status
     * @param retryAfter the {@code Retry-After} a rate limit carries, empty for none
     */
    private record Answer(int status, String retryAfter) {
    }
}
