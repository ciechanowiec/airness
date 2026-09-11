package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads complete public OCI tag catalogues using anonymous pull authorization and cursor pagination.
 *
 * @param registry      registry API origin
 * @param authorization anonymous token service origin
 */
record RegistryTags(String registry, String authorization) {

    private static final String HUB = "https://hub.docker.com";
    private static final Pattern AUTHORIZATION_VALUE = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TAGS = Pattern.compile("\"tags\"\\s*:\\s*\\[([^]]*)]", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("\"([A-Za-z0-9_][A-Za-z0-9_.-]{0,127})\"");
    private static final Pattern NEXT = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");
    private static final int OK = 200;
    private static final int PAGE_SIZE = 1000;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    static RegistryTags forHub(String hub) {
        return HUB.equals(hub) ? new RegistryTags("https://registry-1.docker.io", "https://auth.docker.io")
            : new RegistryTags(hub, hub);
    }

    List<String> tags(DockerReference image) {
        String repository = image.namespace() + '/' + image.repository();
        URI tokenUri = URI.create(
            this.authorization + "/token?service=registry.docker.io&scope=repository:" + repository + ":pull"
        );
        String token = AUTHORIZATION_VALUE.matcher(this.fetch(tokenUri, "").body()).results().map(
            match -> match.group(1)
        )
            .findFirst().orElseThrow(() -> new IllegalStateException("Registry supplied no anonymous pull token"));
        URI first = URI.create(this.registry + "/v2/" + repository + "/tags/list?n=" + PAGE_SIZE);
        List<String> names = new ArrayList<>();
        Set<URI> visited = new HashSet<>();
        Optional<URI> next = Optional.of(first);
        while (next.isPresent()) {
            URI page = next.orElseThrow();
            this.validate(page, first, visited);
            HttpResponse<String> response = this.fetch(page, token);
            names.addAll(names(response.body()));
            next = response.headers().firstValue("Link").map(value -> next(first, value));
        }
        return names.stream().distinct().toList();
    }

    private void validate(URI page, URI first, Set<URI> visited) {
        boolean origin = page.getAuthority().equals(first.getAuthority()) && page.getScheme().equals(first.getScheme());
        if (!origin || !page.getPath().equals(first.getPath()) || !visited.add(page)) {
            throw new IllegalStateException("Invalid or repeated registry pagination link: " + page);
        }
    }

    private HttpResponse<String> fetch(URI uri, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET();
        if (!token.isEmpty()) {
            request.header("Authorization", "Bearer " + token);
        }
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            requireSuccess(uri, response);
            return response;
        } catch (IOException exception) {
            throw new UncheckedIOException("Registry is unreachable: " + uri, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading registry tags", exception);
        }
    }

    private static URI next(URI first, String header) {
        return NEXT.matcher(header).results().map(match -> first.resolve(match.group(1))).findFirst()
            .orElseThrow(() -> new IllegalStateException("Malformed registry pagination link"));
    }

    static List<String> names(String body) {
        String content = TAGS.matcher(body).results().map(match -> match.group(1)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Registry response has no tag array"));
        return content.isBlank() ? List.of() : Arrays.stream(content.split(",", -1)).map(String::strip)
            .map(RegistryTags::name).toList();
    }

    private static String name(String value) {
        return TAG.matcher(value).results().filter(match -> match.group().equals(value)).map(match -> match.group(1))
            .findFirst().orElseThrow(() -> new IllegalStateException("Invalid registry tag: " + value));
    }

    private static void requireSuccess(URI uri, HttpResponse<String> response) {
        if (response.statusCode() != OK) {
            throw new IllegalStateException("Registry returned " + response.statusCode() + " for " + uri);
        }
    }
}
