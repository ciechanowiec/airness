package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Reads public tag metadata from Docker Hub's versioned HTTP API.
 */
final class DockerHub {

    private static final Pattern COUNT = Pattern.compile("\"count\"\\s*:\\s*(?<count>[0-9]+)");
    private static final Pattern NAME = Pattern.compile(
        "\"name\"\\s*:\\s*\"(?<name>[A-Za-z0-9_][A-Za-z0-9_.-]{0,127})\""
    );
    private static final Pattern DIGEST = Pattern.compile(
        "\"digest\"\\s*:\\s*\"(?<digest>sha256:[0-9a-f]{64})\""
    );
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int PAGE_SIZE = 100;
    private static final int OK = 200;

    private final String base;

    DockerHub(String base) {
        this.base = base.replaceAll("/$", "");
    }

    List<String> tags(DockerReference image) {
        String first = this.fetch(this.tagsUri(image, 1));
        int count = count(first);
        int pages = Math.max(1, Math.ceilDiv(count, PAGE_SIZE));
        List<String> found = IntStream.rangeClosed(1, pages)
            .mapToObj(page -> page == 1 ? first : this.fetch(this.tagsUri(image, page)))
            .flatMap(body -> names(body).stream())
            .toList();
        if (found.size() != count) {
            throw new IllegalStateException(
                "Docker Hub reported " + count + " tag(s) but returned " + found.size()
                    + " for " + image.name()
            );
        }
        return found;
    }

    String digest(DockerReference image, String tag) {
        String body = this.fetch(this.tagUri(image, tag));
        List<String> digests = DIGEST.matcher(body).results().map(result -> result.group("digest")).toList();
        if (digests.isEmpty()) {
            throw new IllegalStateException("Docker Hub returned no digest for " + image.name() + ':' + tag);
        }
        return digests.getLast();
    }

    private URI tagsUri(DockerReference image, int page) {
        return URI.create(
            this.repositoryUri(image) + "/tags?page_size=" + PAGE_SIZE + "&page=" + page
        );
    }

    private URI tagUri(DockerReference image, String tag) {
        return URI.create(this.repositoryUri(image) + "/tags/" + tag);
    }

    private String repositoryUri(DockerReference image) {
        return this.base + "/v2/namespaces/" + image.namespace()
            + "/repositories/" + image.repository();
    }

    private String fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            return body(uri, client.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Docker Hub is unreachable: " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying Docker Hub", exception);
        }
    }

    private static String body(URI uri, HttpResponse<String> response) {
        if (response.statusCode() != OK) {
            throw new IllegalStateException(
                "Docker Hub returned " + response.statusCode() + " for " + uri
            );
        }
        return response.body();
    }

    private static int count(String body) {
        return Integer.parseInt(
            COUNT.matcher(body).results().map(result -> result.group("count")).findFirst()
                .orElseThrow(() -> new IllegalStateException("Docker Hub response has no tag count"))
        );
    }

    private static List<String> names(String body) {
        return NAME.matcher(body).results().map(result -> result.group("name")).toList();
    }
}
