package eu.ciechanowiec.airness.maven;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One local HTTPS server that answers some paths with a body and redirects others, recording the credential
 * every request carried.
 */
final class RecordingServer {

    private static final int SUCCESS = 200;
    private static final int FOUND = 302;
    private static final int NOT_FOUND = 404;
    private static final long NO_BODY = -1;

    private final HttpsServer server;
    private final Map<String, String> bodies;
    private final Map<String, String> redirects;
    private final List<Optional<String>> credentials;

    RecordingServer(HttpsServer server) {
        this.server = server;
        this.bodies = new ConcurrentHashMap<>();
        this.redirects = new ConcurrentHashMap<>();
        this.credentials = new CopyOnWriteArrayList<>();
        this.server.createContext("/", this::respond);
        this.server.start();
    }

    void answer(String path, String body) {
        this.bodies.put(path, body);
    }

    void redirect(String path, String location) {
        this.redirects.put(path, location);
    }

    String authority() {
        return "127.0.0.1:" + this.server.getAddress().getPort();
    }

    String uri(String path) {
        return "https://" + this.authority() + path;
    }

    /**
     * The {@code Authorization} header of every request, in the order they came.
     *
     * @return one entry per request, empty for a request that carried none
     */
    List<Optional<String>> credentials() {
        return List.copyOf(this.credentials);
    }

    /**
     * Stops the server, which the servers that started it do when they close.
     */
    void stop() {
        this.server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        this.credentials.add(Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")));
        String path = exchange.getRequestURI().getPath();
        Optional<String> location = Optional.ofNullable(this.redirects.get(path));
        Optional<String> body = Optional.ofNullable(this.bodies.get(path));
        if (location.isPresent()) {
            exchange.getResponseHeaders().add("Location", location.get());
            exchange.sendResponseHeaders(FOUND, NO_BODY);
        } else if (body.isPresent()) {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(SUCCESS, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } else {
            exchange.sendResponseHeaders(NOT_FOUND, NO_BODY);
        }
        exchange.close();
    }
}
