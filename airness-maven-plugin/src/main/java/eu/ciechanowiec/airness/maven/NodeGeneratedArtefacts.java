package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The files each declared node project lists under {@code airness.generatedArtefacts} in its {@code package.json},
 * as repository-relative globs.
 *
 * <p>The node gates already pass over them as a generator's output, so the agent runtime is denied them as it is
 * denied the files a layer or the {@code airness.generated.files} property declares. An entry is honoured as the
 * node gates honour it: an object naming a {@code pattern} and a {@code reason}. A manifest that cannot be read
 * adds nothing here, and the node gates report it.
 */
@UtilityClass
final class NodeGeneratedArtefacts {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATTERN = "pattern";

    /**
     * Every generated artefact the node projects among the sites declare.
     *
     * @param root  the repository root
     * @param sites the directories of the repository, the node projects among them
     * @return repository-relative globs, in site order
     */
    static List<String> paths(Path root, Collection<AssetSite> sites) {
        return sites.stream()
            .filter(site -> site.location() == AssetLocation.NODE_PROJECT)
            .flatMap(site -> declared(root.resolve(site.directory())).map(pattern -> placed(site, pattern)))
            .distinct()
            .toList();
    }

    private static Stream<String> declared(Path project) {
        Path manifest = project.resolve("package.json");
        return Files.isRegularFile(manifest) ? read(manifest).flatMap(NodeGeneratedArtefacts::honoured) : Stream
            .empty();
    }

    private static Stream<JsonNode> read(Path manifest) {
        try {
            return JSON.readTree(manifest.toFile()).path("airness").path("generatedArtefacts").valueStream();
        } catch (IOException _) {
            return Stream.empty();
        }
    }

    // A pattern with a reason, inside the project, as the node gates honour an entry.
    private static Stream<String> honoured(JsonNode entry) {
        String pattern = entry.path(PATTERN).asText("").strip();
        boolean reasoned = !entry.path("reason").asText("").isBlank();
        return reasoned && inside(pattern) ? Stream.of(pattern) : Stream.empty();
    }

    private static boolean inside(String pattern) {
        boolean escapes = pattern.startsWith("/") || pattern.contains("..");
        return !pattern.isEmpty() && !escapes;
    }

    private static String placed(AssetSite site, String pattern) {
        return site.directory().isEmpty() ? pattern : site.directory() + '/' + pattern;
    }
}
