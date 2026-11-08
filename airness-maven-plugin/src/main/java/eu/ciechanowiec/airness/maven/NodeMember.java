package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
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

    private static final List<String> DEFAULT_ROOTS = List.of("src", "scripts");
    private static final String TESTS = "tests";
    private static final String WHOLE = ".";
    private static final String CURRENT = "./";
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
            .filter(directory -> Files.isRegularFile(directory.resolve(NodeManifests.MANIFEST)))
            .findFirst().orElse(project);
        return at(member);
    }

    /**
     * The member a directory is, with the settings its {@code package.json} declares.
     *
     * @param member the member's directory
     * @return the member, with no added roots or generated files when it holds no readable manifest
     */
    static NodeMember at(Path member) {
        JsonNode settings = settings(member.resolve(NodeManifests.MANIFEST));
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
        return rooted(this.roots, relative) && this.generated.stream().noneMatch(glob -> glob.matches(relative));
    }

    /**
     * The directories the node gates that read source roots select from: {@code src}, {@code tests},
     * {@code scripts} and the {@code sourceRoots} the member adds, as {@code settings.ts} reads them.
     *
     * <p>{@link #roots()} leaves {@code tests} out, because a suppression in a test is no production code. A
     * gate checks a test all the same, so whether a file is checked at all asks this wider set.
     *
     * @return the checked roots, relative to the member, sorted and each once
     */
    List<String> checkedRoots() {
        return Stream.concat(Stream.of(TESTS), this.roots.stream()).map(NodeMember::trimmed).distinct().sorted()
            .toList();
    }

    /**
     * Whether a file lies under one of the {@link #checkedRoots()}.
     *
     * @param source a file of the member
     * @return whether a gate reading source roots selects it
     */
    boolean checked(Path source) {
        return rooted(this.checkedRoots(), this.root.relativize(source));
    }

    // A root of "." is the whole member, as settings.ts reads it.
    private static boolean rooted(Collection<String> roots, Path relative) {
        String text = relative.toString().replace('\\', '/');
        return roots.stream()
            .map(NodeMember::trimmed)
            .anyMatch(sourceRoot -> WHOLE.equals(sourceRoot) || text.startsWith(sourceRoot + '/'));
    }

    private static String trimmed(String root) {
        String current = root.startsWith(CURRENT) ? root.substring(CURRENT.length()) : root;
        boolean slashed = current.length() > 1 && current.endsWith("/");
        return slashed ? current.substring(0, current.length() - 1) : current;
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
