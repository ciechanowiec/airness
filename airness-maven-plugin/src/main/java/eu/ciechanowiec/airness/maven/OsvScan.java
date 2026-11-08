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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Reads the known vulnerabilities of npm packages from the OSV database, by their package URLs.
 *
 * <p>The npm SBOM names every resolved package by its package URL, which is what OSV's batch query takes, so the
 * scan needs no Docker, no credential and no lockfile reader of its own. It runs under the extended profile with
 * the compliance report, as the Maven scan does. A query or an advisory that cannot be read fails the scan: an
 * unreachable database is never a clean result. A query OSV answers in part, with a page token in place of the
 * rest of its vulnerabilities, is asked again with that token until no token comes back: OSV cuts a query short
 * when it runs long, and reading the first page alone would pass the vulnerabilities on the pages it never asked
 * for. A withdrawn advisory is passed over. The findings retain the database's aliases so project mitigations
 * can identify the exact advisory they address.
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
    private static final String NEXT_PAGE = "next_page_token";

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
     * The findings and database-supplied advisory aliases of the given packages.
     *
     * @param purls  the package URLs of the resolved packages
     * @param module the module the packages belong to
     * @return one advisory per vulnerability and package, most severe first
     * @throws IllegalStateException when OSV cannot be read
     */
    List<OsvAdvisory> findings(Collection<String> purls, String module) {
        List<String> queried = purls.stream().distinct().sorted().toList();
        List<Map.Entry<String, String>> found = IntStream.range(0, (queried.size() + BATCH - 1) / BATCH)
            .mapToObj(batch -> queried.subList(batch * BATCH, Math.min(queried.size(), (batch + 1) * BATCH)))
            .flatMap(this::batch).toList();
        Map<String, JsonNode> details = found.stream().map(Map.Entry::getValue).distinct()
            .collect(Collectors.toUnmodifiableMap(id -> id, this::vulnerability));
        return found.stream()
            .map(entry -> Map.entry(entry.getKey(), Objects.requireNonNull(details.get(entry.getValue()))))
            .filter(entry -> !entry.getValue().has("withdrawn"))
            .map(entry -> OsvAdvisory.of(entry.getValue(), entry.getKey(), module))
            .sorted(Comparator.comparing(OsvAdvisory::advisory, ReportAdvisory.TRIAGE)).toList();
    }

    // Each package with the identifier of every vulnerability OSV lists for it.
    private Stream<Map.Entry<String, String>> batch(List<String> purls) {
        return this.pages(purls.stream().map(purl -> new Query(purl, "")).toList());
    }

    // One page of every query, followed by the pages OSV still holds for the queries it answered in part.
    private Stream<Map.Entry<String, String>> pages(List<Query> queries) {
        Map<String, Object> body = Map.of("queries", queries.stream().map(Query::body).toList());
        JsonNode results = this.send(
            HttpRequest.newBuilder(URI.create(this.base + "v1/querybatch")).timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ReportJson.encode(body))).build()
        ).path("results");
        if (!results.isArray() || results.size() != queries.size()) {
            throw new IllegalStateException("OSV answered " + results.size() + " result(s) for " + queries.size());
        }
        List<Map.Entry<String, String>> found = IntStream.range(0, queries.size()).boxed().flatMap(
            index -> results.get(index).path("vulns").valueStream()
                .map(vulnerability -> Map.entry(queries.get(index).purl(), vulnerability.path("id").asText()))
        ).toList();
        List<Query> unfinished = IntStream.range(0, queries.size())
            .filter(index -> !results.get(index).path(NEXT_PAGE).asText().isEmpty())
            .mapToObj(index -> new Query(queries.get(index).purl(), results.get(index).path(NEXT_PAGE).asText()))
            .toList();
        return Stream.concat(found.stream(), unfinished.isEmpty() ? Stream.empty() : this.pages(unfinished));
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

    /**
     * One package query of a batch.
     *
     * @param purl  the package URL
     * @param token the page token OSV returned for the package, empty for its first page
     */
    private record Query(String purl, String token) {

        Map<String, Object> body() {
            Map<String, String> pkg = Map.of("purl", this.purl);
            return this.token.isEmpty() ? Map.of("package", pkg) : Map.of("package", pkg, "page_token", this.token);
        }
    }
}
