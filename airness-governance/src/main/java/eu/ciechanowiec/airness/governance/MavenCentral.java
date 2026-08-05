package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads the latest stable comparable version of a dependency from a registry's metadata: the
 * numeric major for conventional versions, or the leading year for versions beginning with
 * {@code 20**}. Pre-release versions are ignored: the six forms the versions report ignores (see its
 * ruleset in the pom), spelled here as the same anchored patterns so the two agree on what counts as
 * stable, plus snapshots, which the report never meets because it reads release metadata. Any
 * unreachable registry, non-200 response, or absence of a stable release throws, so the freshness
 * check fails closed rather than passing on missing data.
 *
 * <p>The registry is a parameter rather than a constant, because a check that can only ever reach one
 * host is a check nobody can watch fail. Pointing it at a host that does not answer is how the caller
 * proves the fail-closed behaviour above is real, and a project behind a mirror needs the same knob for
 * an ordinary reason.
 */
@UtilityClass
final class MavenCentral {

    private static final String SLASH = "/";
    private static final Pattern PRERELEASE = Pattern.compile(
        "(?i).*alpha.*|.*beta.*|.*preview.*|.*snapshot.*|[0-9].+-m[0-9]+|[0-9].+\\.cr[0-9]+|[0-9].+-rc-?[0-9]*"
    );
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;

    static int latestMajor(String registry, DeclaredCoordinate coordinate) {
        String metadata = fetch(metadataUrl(registry, coordinate));
        List<String> versions = MavenMetadata.versions(metadata);
        return versions.stream()
            .filter(version -> !PRERELEASE.matcher(version).matches())
            .filter(version -> DependencyFreshnessRules.sameScheme(coordinate.version(), version))
            .map(DependencyFreshnessRules::major)
            .flatMapToInt(OptionalInt::stream)
            .max()
            .orElseThrow(() -> new IllegalStateException("No stable release found for " + coordinate));
    }

    private static String metadataUrl(String registry, DeclaredCoordinate coordinate) {
        String base = registry.endsWith(SLASH) ? registry : registry + SLASH;
        String group = coordinate.groupId().replace('.', '/');
        return base + group + SLASH + coordinate.artifactId() + "/maven-metadata.xml";
    }

    private static String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            return body(url, send(client, request));
        }
    }

    private static String body(String url, HttpResponse<String> response) {
        if (response.statusCode() != OK) {
            throw new IllegalStateException("Registry returned " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private static HttpResponse<String> send(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new UncheckedIOException("Registry unreachable: " + request.uri(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying the registry", exception);
        }
    }
}
