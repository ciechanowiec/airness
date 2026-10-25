package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The node project member a source belongs to, with the settings its {@code package.json} declares about which
 * of its files are first-party source.
 *
 * @param root      the member's directory, the nearest one at or above the source holding a {@code package.json}
 * @param roots     the member's source roots: {@code src}, {@code scripts} and the {@code sourceRoots} it adds
 * @param generated the globs of its {@code generatedArtefacts}, relative to its directory
 */
record NodeMember(Path root, List<String> roots, List<PathMatcher> generated) {

    private static final String MANIFEST = "package.json";
    private static final List<String> DEFAULT_ROOTS = List.of("src", "scripts");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Copies both lists, so the member cannot change after it was read.
     *
     * @param root      the member's directory
     * @param roots     its source roots
     * @param generated its generated-file globs
     */
    NodeMember {
        roots = List.copyOf(roots);
        generated = List.copyOf(generated);
    }

    /**
     * The member a source of a node project belongs to.
     *
     * @param project the node project directory
     * @param source  a file below it
     * @return the member, or the project itself when no nearer directory holds a {@code package.json}
     */
    static NodeMember of(Path project, Path source) {
        Path member = Stream.iterate(source.getParent(), Objects::nonNull, Path::getParent)
            .takeWhile(directory -> directory.startsWith(project))
            .filter(directory -> Files.isRegularFile(directory.resolve(MANIFEST)))
            .findFirst().orElse(project);
        JsonNode settings = settings(member.resolve(MANIFEST));
        return new NodeMember(
            member,
            Stream.concat(DEFAULT_ROOTS.stream(), settings.path("sourceRoots").valueStream().map(JsonNode::asText))
                .toList(),
            settings.path("generatedArtefacts").valueStream().map(entry -> entry.path("pattern").asText())
                .filter(pattern -> !pattern.isBlank())
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .toList()
        );
    }

    /**
     * Whether a source is the member's own code: below one of its source roots and no generated file.
     *
     * @param source a file of the member
     * @return whether the source is first-party code
     */
    boolean authored(Path source) {
        Path relative = this.root.relativize(source);
        String text = relative.toString().replace('\\', '/');
        boolean rooted = this.roots.stream().anyMatch(sourceRoot -> text.startsWith(sourceRoot + '/'));
        return rooted && this.generated.stream().noneMatch(glob -> glob.matches(relative));
    }

    private static JsonNode settings(Path manifest) {
        try {
            return Files.isRegularFile(manifest) ? JSON.readTree(manifest.toFile()).path("airness") : JSON
                .missingNode();
        } catch (IOException _) {
            return JSON.missingNode();
        }
    }
}
