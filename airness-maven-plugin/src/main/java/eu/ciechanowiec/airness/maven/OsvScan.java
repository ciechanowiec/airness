package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reads the known vulnerabilities of npm packages from the OSV database, by their package URLs.
 *
 * <p>The npm SBOM names every resolved package by its package URL, which is what OSV's batch query takes, so the
 * scan needs no Docker, no credential and no lockfile reader of its own. It runs under the extended profile with
 * the compliance report, as the Maven scan does. A query or an advisory that cannot be read fails the scan: an
 * unreachable database is never a clean result. A withdrawn advisory is passed over. No npm advisory can be
 * excused yet, so every one it reports is open and fails the build.
 *
 * @param base   the OSV API, ending in a slash
 * @param client the HTTP client every request of one scan shares
 */
record OsvScan(String base, HttpClient client) {

    /**
     * The public OSV API.
     */
    static final String PUBLIC = "https://api.osv.dev/";

    private static final int BATCH = 1000;
    private static final int OK = 200;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * A scan against one OSV API.
     *
     * @param base the API, ending in a slash
     * @return the scan
     */
    static OsvScan of(String base) {
        return new OsvScan(
            base, HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build()
        );
    }

    /**
     * The open advisories of the given packages.
     *
     * @param purls  the package URLs of the resolved packages
     * @param module the module the packages belong to
     * @return one advisory per vulnerability and package, most severe first
     * @throws IllegalStateException when OSV cannot be read
     */
    List<ReportAdvisory> advisories(Collection<String> purls, String module) {
        List<String> queried = purls.stream().distinct().sorted().toList();
        List<Map.Entry<String, String>> found = IntStream.range(0, (queried.size() + BATCH - 1) / BATCH)
            .mapToObj(batch -> queried.subList(batch * BATCH, Math.min(queried.size(), (batch + 1) * BATCH)))
            .flatMap(this::batch).toList();
        Map<String, JsonNode> details = found.stream().map(Map.Entry::getValue).distinct()
            .collect(Collectors.toUnmodifiableMap(id -> id, this::vulnerability));
        return found.stream()
            .map(entry -> Map.entry(entry.getKey(), Objects.requireNonNull(details.get(entry.getValue()))))
            .filter(entry -> !entry.getValue().has("withdrawn"))
            .map(entry -> advisory(entry.getValue(), entry.getKey(), module))
            .sorted(ReportAdvisory.TRIAGE).toList();
    }

    // Each package with the identifier of every vulnerability OSV lists for it.
    private Stream<Map.Entry<String, String>> batch(List<String> purls) {
        Map<String, Object> body = Map.of(
            "queries", purls.stream().map(purl -> Map.of("package", Map.of("purl", purl))).toList()
        );
        JsonNode results = this.send(
            HttpRequest.newBuilder(URI.create(this.base + "v1/querybatch")).timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ReportJson.encode(body))).build()
        ).path("results");
        if (!results.isArray() || results.size() != purls.size()) {
            throw new IllegalStateException("OSV answered " + results.size() + " result(s) for " + purls.size());
        }
        return IntStream.range(0, purls.size()).boxed().flatMap(
            index -> results.get(index).path("vulns").valueStream()
                .map(vulnerability -> Map.entry(purls.get(index), vulnerability.path("id").asText()))
        );
    }

    private JsonNode vulnerability(String id) {
        return this.send(
            HttpRequest.newBuilder(URI.create(this.base + "v1/vulns/" + id)).timeout(TIMEOUT).GET().build()
        );
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != OK) {
                throw new IllegalStateException(
                    "OSV answered " + response.statusCode() + " for " + request.uri()
                        + ", so the npm vulnerability scan"
                        + " fails rather than passing without it"
                );
            }
            return parsed(request.uri(), response.body());
        } catch (IOException exception) {
            throw new IllegalStateException(
                "OSV could not be read at " + request.uri() + ", so the npm vulnerability scan fails rather than"
                    + " passing without it",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading OSV", exception);
        }
    }

    private static JsonNode parsed(URI address, String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonMappingException exception) {
            throw new IllegalStateException("OSV returned an invalid JSON tree at " + address, exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OSV could not be read at " + address, exception);
        }
    }

    private static ReportAdvisory advisory(JsonNode vulnerability, String purl, String module) {
        String summary = vulnerability.path("summary").asText();
        String description = summary.isBlank()
            ? vulnerability.path("details").asText().lines().findFirst().orElse("") : summary;
        String severity = Optional.of(vulnerability.path("database_specific").path("severity").asText())
            .filter(text -> !text.isBlank()).orElse("Unknown");
        return new ReportAdvisory(
            vulnerability.path("id").asText(), severity, purl, List.of(module), description, Optional.empty()
        );
    }
}
