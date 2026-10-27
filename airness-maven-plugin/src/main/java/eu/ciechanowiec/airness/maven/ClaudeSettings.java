package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
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

    private static final String PERMISSIONS = "permissions";
    // Two spaces a level, one rule a line, and a space after each colon only, as the file is ordinarily written.
    private static final DefaultIndenter INDENT = new DefaultIndenter("  ", "\n");
    private static final DefaultPrettyPrinter PRINTER = new DefaultPrettyPrinter()
        .withSeparators(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
        .withObjectIndenter(INDENT).withArrayIndenter(INDENT);
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
            .map(settings -> settings.path(PERMISSIONS).path(list))
            .filter(JsonNode::isArray)
            .map(
                rules -> StreamSupport.stream(rules.spliterator(), false)
                    .filter(JsonNode::isTextual)
                    .map(JsonNode::asText)
                    .toList()
            ).orElseGet(List::of);
    }

    /**
     * Adds every rule missing from a permission list, keeping every rule and key the file already holds.
     *
     * @param file  the settings file, created when absent
     * @param list  the list under {@code permissions}, such as {@code deny}
     * @param rules the rules the list must hold
     * @return whether the file was written
     * @throws IllegalStateException when the file exists but is no settings object the runtime could read
     */
    static boolean add(Path file, String list, List<String> rules) {
        ObjectNode settings = settings(file);
        JsonNode permissions = settings.path(PERMISSIONS);
        ObjectNode held = permissions.isObject() ? (ObjectNode) permissions : settings.putObject(PERMISSIONS);
        List<String> present = permissions(file, list);
        List<String> missing = rules.stream().distinct().filter(rule -> !present.contains(rule)).toList();
        if (missing.isEmpty()) {
            return false;
        }
        ArrayNode target = held.path(list).isArray() ? (ArrayNode) held.path(list) : held.putArray(list);
        missing.forEach(target::add);
        write(file, settings);
        return true;
    }

    private static ObjectNode settings(Path file) {
        if (!Files.isRegularFile(file)) {
            return JSON.createObjectNode();
        }
        return read(file).filter(JsonNode::isObject).map(ObjectNode.class::cast).orElseThrow(
            () -> new IllegalStateException(file + " is no JSON object the agent runtime can read, so repair it first")
        );
    }

    private static void write(Path file, ObjectNode settings) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON.writer(PRINTER).writeValueAsString(settings) + '\n');
        } catch (JsonProcessingException exception) {
            throw new UncheckedIOException("Could not serialize the settings for " + file, exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }

    private static Optional<JsonNode> read(Path file) {
        try {
            return Files.isRegularFile(file) ? Optional.ofNullable(JSON.readTree(file.toFile())) : Optional.empty();
        } catch (IOException _) {
            return Optional.empty();
        }
    }
}
