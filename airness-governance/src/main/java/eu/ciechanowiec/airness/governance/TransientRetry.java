package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Sends a registry request again when it fails in a way that passes: a connection that fails or times out, a
 * rate limit (429) or a server error (5xx). Any other answer, a 404 among them, is returned at once for the
 * caller to judge. When every attempt is rate limited or meets a server error, the last answer is returned, which
 * the caller refuses as before, and when no attempt connects the request fails naming how often it was tried, so
 * a registry that cannot be read never passes a check.
 */
public final class TransientRetry {

    /**
     * What registry lookups use: three attempts, two and then six seconds apart, or as long as a rate limit's
     * {@code Retry-After} asks, up to thirty seconds.
     */
    public static final TransientRetry REGISTRIES = new TransientRetry(
        List.of(Duration.ofSeconds(2), Duration.ofSeconds(6)), Duration.ofSeconds(30), Thread::sleep
    );

    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR_FLOOR = 500;
    private static final int SERVER_ERROR_CEILING = 599;
    private static final Pattern SECONDS = Pattern.compile("\\d{1,9}");

    private final List<Duration> pauses;
    private final Duration rateLimitCap;
    private final Pause pause;

    /**
     * Names how long to wait before each further attempt and how waiting is done.
     *
     * @param pauses       the wait before each attempt after the first, so one more attempt than there are waits
     * @param rateLimitCap the longest wait a rate limit's {@code Retry-After} may ask for
     * @param pause        how a wait is spent
     */
    TransientRetry(List<Duration> pauses, Duration rateLimitCap, Pause pause) {
        this.pauses = List.copyOf(pauses);
        this.rateLimitCap = rateLimitCap;
        this.pause = pause;
    }

    /**
     * Sends the request, again after a transient failure, until it is answered otherwise or the attempts run out.
     *
     * @param client  the client that sends the request
     * @param request the request
     * @return the first answer that is neither a rate limit nor a server error, or the last answer
     * @throws IOException          when the last attempt failed to connect or timed out, the message naming the
     *                              attempts
     * @throws InterruptedException when interrupted while sending or waiting
     */
    public HttpResponse<String> send(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        int attempts = this.pauses.size() + 1;
        for (int attempt = 1; attempt < attempts; attempt++) {
            Optional<HttpResponse<String>> answered = answered(client, request);
            if (answered.filter(response -> !isTransient(response.statusCode())).isPresent()) {
                return answered.orElseThrow();
            }
            this.pause.pause(this.delay(answered, this.pauses.get(attempt - 1)));
        }
        return last(client, request, attempts);
    }

    private static Optional<HttpResponse<String>> answered(
        HttpClient client, HttpRequest request
    ) throws InterruptedException {
        try {
            return Optional.of(client.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException _) {
            // A failure before the last attempt only earns another one. The last attempt reports its own.
            return Optional.empty();
        }
    }

    private static HttpResponse<String> last(
        HttpClient client, HttpRequest request, int attempts
    ) throws IOException, InterruptedException {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IOException(
                "failed on each of " + attempts + " attempts, the last with: " + exception.getMessage(), exception
            );
        }
    }

    private Duration delay(Optional<HttpResponse<String>> answered, Duration planned) {
        return answered.filter(response -> response.statusCode() == TOO_MANY_REQUESTS)
            .flatMap(response -> response.headers().firstValue("Retry-After"))
            .map(String::strip)
            .filter(value -> SECONDS.matcher(value).matches())
            .map(value -> Duration.ofSeconds(Long.parseLong(value)))
            .map(asked -> asked.compareTo(this.rateLimitCap) > 0 ? this.rateLimitCap : asked)
            .orElse(planned);
    }

    private static boolean isTransient(int status) {
        boolean rateLimited = status == TOO_MANY_REQUESTS;
        boolean serverError = status >= SERVER_ERROR_FLOOR && status <= SERVER_ERROR_CEILING;
        return rateLimited || serverError;
    }

    /**
     * How a wait between attempts is spent.
     */
    @FunctionalInterface
    public interface Pause {

        /**
         * Waits.
         *
         * @param duration how long
         * @throws InterruptedException when interrupted while waiting
         */
        void pause(Duration duration) throws InterruptedException;
    }
}
