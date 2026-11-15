package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;

/**
 * An OSV API that answers each batch by what each query asks: a package URL, or a source package of an ecosystem at
 * a version, so one test can hold an image, its base and the releases of its base apart.
 */
final class OsvStub implements AutoCloseable {

    private static final String QUERIES = "\"queries\"";

    private final ArchiveServer server;
    private final Map<String, List<String>> answers;

    OsvStub() {
        this.server = new ArchiveServer();
        this.answers = new ConcurrentHashMap<>();
        this.server.beforeAnswer(this::batch);
    }

    /**
     * Lists an advisory for a query.
     *
     * @param query  a package URL, or {@code ecosystem name version}
     * @param id     the advisory's identifier
     * @param detail the advisory OSV returns for it
     * @return this stub
     */
    OsvStub lists(String query, String id, String detail) {
        this.answers.merge(query, List.of(id), (left, right) -> Stream.concat(left.stream(), right.stream()).toList());
        this.server.serve("/v1/vulns/" + id, detail.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    OsvScan scan() {
        return OsvScan.of(this.server.uri("/").toString());
    }

    // The queries the batches asked, each as a package URL or as ecosystem, name and version.
    List<String> asked() {
        return this.server.bodies().stream().filter(body -> body.contains(QUERIES)).flatMap(OsvStub::queries)
            .toList();
    }

    private static Stream<String> queries(String body) {
        try {
            return new ObjectMapper().readTree(body).path("queries").valueStream().map(OsvStub::key);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void close() {
        this.server.close();
    }

    @SneakyThrows
    private void batch() {
        String body = this.server.bodies().getLast();
        if (!body.contains(QUERIES)) {
            return;
        }
        String results = new ObjectMapper().readTree(body).path("queries").valueStream()
            .map(this::answer).collect(Collectors.joining(", ", "{\"results\": [", "]}"));
        this.server.serve("/v1/querybatch", results.getBytes(StandardCharsets.UTF_8));
    }

    private String answer(JsonNode query) {
        return this.answers.getOrDefault(key(query), List.of()).stream().map(id -> "{\"id\": \"" + id + "\"}")
            .collect(Collectors.joining(", ", "{\"vulns\": [", "]}"));
    }

    private static String key(JsonNode query) {
        JsonNode queried = query.path("package");
        return queried.has("purl") ? queried.path("purl").asText()
            : queried.path("ecosystem").asText() + ' ' + queried.path("name").asText() + ' '
                + query.path("version").asText();
    }
}
