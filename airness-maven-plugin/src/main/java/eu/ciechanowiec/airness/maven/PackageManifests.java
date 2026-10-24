package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.PackageManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Reads what a {@code package.json} declares that an instruction file may name.
 */
@UtilityClass
final class PackageManifests {

    private static final String MANIFEST = "package.json";
    private static final List<String> DEPENDENCIES = List.of(
        "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
    );

    /**
     * The manifest in a directory.
     *
     * @param directory the directory
     * @return its scripts and dependencies, or empty where it holds no {@code package.json}
     */
    static Optional<PackageManifest> in(Path directory) {
        Path file = directory.resolve(MANIFEST);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        JsonNode document = read(file)
            .filter(JsonNode::isObject)
            .orElseThrow(
                () -> new IllegalStateException(file + " is not a JSON object, so its scripts cannot be read")
            );
        return Optional.of(
            new PackageManifest(
                keys(document.path("scripts")),
                DEPENDENCIES.stream()
                    .map(document::path)
                    .flatMap(node -> keys(node).stream())
                    .collect(Collectors.toUnmodifiableSet())
            )
        );
    }

    private static Optional<JsonNode> read(Path file) {
        try {
            return Optional.ofNullable(new ObjectMapper().readTree(file.toFile()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static Set<String> keys(JsonNode node) {
        return node.isObject()
            ? node.propertyStream().map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet())
            : Set.of();
    }
}
