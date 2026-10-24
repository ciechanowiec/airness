package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;

/**
 * Reads the permission rules of an agent runtime settings file.
 *
 * <p>The runtime accepts comments and trailing commas in its settings, so they are accepted here too. A
 * file that is absent, or that the runtime could not read either, carries no rule, which is what the
 * runtime then enforces: a denial written into a file it cannot parse protects nothing.
 */
@UtilityClass
final class ClaudeSettings {

    /**
     * The shared settings file at the repository root.
     */
    static final String SHARED = ".claude/settings.json";

    /**
     * The machine-local settings file beside it, which git ignores.
     */
    static final String LOCAL = ".claude/settings.local.json";

    private static final ObjectMapper JSON = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build();

    /**
     * The string rules of one permission list.
     *
     * @param file the settings file
     * @param list the list under {@code permissions}, such as {@code deny} or {@code allow}
     * @return the rules, empty when the file or the list is absent or unreadable
     */
    static List<String> permissions(Path file, String list) {
        return read(file)
            .map(settings -> settings.path("permissions").path(list))
            .filter(JsonNode::isArray)
            .map(
                rules -> StreamSupport.stream(rules.spliterator(), false)
                    .filter(JsonNode::isTextual)
                    .map(JsonNode::asText)
                    .toList()
            ).orElseGet(List::of);
    }

    private static Optional<JsonNode> read(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.ofNullable(JSON.readTree(file.toFile())) : Optional.empty();
        } catch (IOException _) {
            return Optional.empty();
        }
    }
}
