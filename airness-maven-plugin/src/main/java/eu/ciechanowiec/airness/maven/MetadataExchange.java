package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.TransientRetry;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * How one request is sent and answered, which a build does through {@link TransientRetry#REGISTRIES} and a test
 * does without its pauses.
 */
@FunctionalInterface
interface MetadataExchange {

    /**
     * Sends the request.
     *
     * @param client  the client that sends it
     * @param request the request
     * @return the answer
     * @throws IOException          when no answer came
     * @throws InterruptedException when interrupted while waiting
     */
    HttpResponse<String> send(HttpClient client, HttpRequest request) throws IOException, InterruptedException;
}
