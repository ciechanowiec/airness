package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Reads the latest stable version of a dependency from a registry's metadata. Pre-release versions
 * are ignored: the six forms the versions report ignores, spelled here as the same anchored patterns
 * so the two agree on what counts as stable, plus snapshots. Any unreachable registry, non-200
 * response, or absence of a stable release throws, so the version check fails closed rather than
 * passing on missing data.
 *
 * <p>The registry is a parameter rather than a constant, because a check that can only ever reach one
 * host is a check nobody can watch fail. Pointing it at a host that does not answer is how the caller
 * proves the fail-closed behaviour above is real, and a project behind a mirror needs the same knob for
 * an ordinary reason.
 */
@UtilityClass
final class MavenCentral {

    private static final String SLASH = "/";
    private static final String PROPERTY_OPEN = "${";
    private static final Pattern PRERELEASE = Pattern.compile(
        "(?i).*alpha.*|.*beta.*|.*preview.*|.*snapshot.*|[0-9].+-m[0-9]+|[0-9].+\\.cr[0-9]+|[0-9].+-rc-?[0-9]*"
    );
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;

    static Optional<VersionUpdate> update(String registry, OwnedCoordinate declared) {
        DeclaredCoordinate coordinate = declared.coordinate();
        String metadata = fetch(metadataUrl(registry, coordinate));
        String latest = MavenMetadata.versions(metadata).stream()
            .filter(version -> !PRERELEASE.matcher(version).matches())
            .filter(version -> DependencyFreshnessRules.sameScheme(coordinate.version(), version))
            .max(Comparator.comparing(ComparableVersion::new))
            .orElseThrow(() -> new IllegalStateException("No stable release found for " + coordinate));
        return Optional.of(new VersionUpdate(declared, latest)).filter(MavenCentral::isNewer);
    }

    static boolean checkable(OwnedCoordinate declared) {
        String version = declared.coordinate().version();
        if (version.contains(PROPERTY_OPEN)) {
            throw new IllegalStateException("Unresolved version for " + declared);
        }
        return !PRERELEASE.matcher(version).matches();
    }

    private static boolean isNewer(VersionUpdate update) {
        ComparableVersion declared = new ComparableVersion(update.declared().coordinate().version());
        return new ComparableVersion(update.latest()).compareTo(declared) > 0;
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
