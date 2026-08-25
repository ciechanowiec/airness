package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.Justification;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * The findings a SARIF report names.
 *
 * <p>A check that fails on an exit code alone leaves the reader to open the report themselves, so the
 * verdict and what produced it live in two places. Reading the report here puts them in one message.
 *
 * @param report the SARIF file the check writes
 */
record SarifFindings(Path report) {

    /**
     * How many findings the message names before it counts the rest. A failing run can report hundreds,
     * and a message that long is scrolled past rather than read.
     */
    private static final int LISTED = 20;

    private static final int NO_LINE = 0;

    List<String> listed() {
        return Files.isReadable(this.report) ? this.parsed() : List.of();
    }

    private List<String> parsed() {
        try {
            List<String> found = this.read();
            return found.size() <= LISTED ? found : this.counted(found);
        } catch (IOException exception) {
            return List.of("%s could not be read: %s".formatted(this.report, exception.getMessage()));
        }
    }

    private List<String> read() throws IOException {
        JsonNode root = new ObjectMapper().readTree(this.report.toFile());
        return root.path("runs")
            .valueStream()
            .flatMap(run -> run.path("results").valueStream())
            .map(SarifFindings::described)
            .toList();
    }

    private List<String> counted(Collection<String> found) {
        return Stream.concat(
            found.stream().limit(LISTED),
            Stream.of("and %d more in %s".formatted(found.size() - LISTED, this.report))
        ).toList();
    }

    private static String described(JsonNode result) {
        return "%s %s: %s".formatted(
            located(result),
            result.path("ruleId").asText("unknown rule"),
            flattened(result.path("message").path("text").asText("no message"))
        );
    }

    @Justification(
        "TreeNode, the weaker type proposed, returns TreeNode from path, and TreeNode declares neither "
            + "asText nor asInt, so the weakened signature does not compile"
    )
    @SuppressWarnings("TypeMayBeWeakened")
    private static String located(JsonNode result) {
        JsonNode physical = result.path("locations").path(0).path("physicalLocation");
        String file = physical.path("artifactLocation").path("uri").asText("unknown file");
        int line = physical.path("region").path("startLine").asInt(NO_LINE);
        return line == NO_LINE ? file : "%s:%d".formatted(file, line);
    }

    /*
     * A SARIF message may run to several lines, and one finding per line is what makes the list readable.
     */
    private static String flattened(String message) {
        return message.replaceAll("\\s+", " ").strip();
    }
}
