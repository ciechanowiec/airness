package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.AssetLocation;
import eu.ciechanowiec.airness.governance.AssetSite;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The files each declared node project, and each member of a pnpm workspace one roots, lists under
 * {@code airness.generatedArtefacts} in its {@code package.json}, as repository-relative globs.
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
    // Directories no workspace member lies in: installed packages and build output carry manifests of their own.
    private static final Set<String> UNSEARCHED = Set.of("node_modules", "target");

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
            .flatMap(site -> projectPaths(root.resolve(site.directory()), site))
            .distinct()
            .toList();
    }

    // The project's own declarations, then each workspace member's, placed in the member's directory: the node
    // gates judge a member as a package of its own, with the generated files its own manifest names.
    private static Stream<String> projectPaths(Path project, AssetSite site) {
        Stream<String> own = declared(project).map(pattern -> placed(site.directory(), pattern));
        Stream<String> members = PnpmWorkspace.at(project).stream()
            .flatMap(workspace -> members(project, workspace).stream())
            .flatMap(member -> memberPaths(project, site, member));
        return Stream.concat(own, members);
    }

    private static Stream<String> memberPaths(Path project, AssetSite site, Path member) {
        String relative = project.relativize(member).toString().replace('\\', '/');
        String directory = placed(site.directory(), relative);
        return declared(member).map(pattern -> directory + '/' + pattern);
    }

    private static List<Path> members(Path project, PnpmWorkspace workspace) {
        MemberSearch search = new MemberSearch(project, workspace);
        try {
            Files.walkFileTree(project, search);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not search the workspace members of " + project, exception);
        }
        return search.found().stream().sorted().toList();
    }

    private static Stream<String> declared(Path project) {
        Path manifest = project.resolve(NodeManifests.MANIFEST);
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

    private static String placed(String directory, String pattern) {
        return directory.isEmpty() ? pattern : directory + '/' + pattern;
    }

    /**
     * Finds the directories of a workspace's members below its root, passing over installed packages, build
     * output and hidden directories, which carry manifests of their own.
     */
    private static final class MemberSearch implements FileVisitor<Path> {

        private final Path project;
        private final PnpmWorkspace workspace;
        private final List<Path> members;

        MemberSearch(Path project, PnpmWorkspace workspace) {
            this.project = project;
            this.workspace = workspace;
            this.members = new ArrayList<>();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (this.unsearched(directory)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            if (this.member(directory)) {
                this.members.add(directory);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
            throw exception;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, @Nullable IOException exception) throws IOException {
            Optional<IOException> failure = Optional.ofNullable(exception);
            if (failure.isPresent()) {
                throw failure.get();
            }
            return FileVisitResult.CONTINUE;
        }

        List<Path> found() {
            return List.copyOf(this.members);
        }

        private boolean unsearched(Path directory) {
            String name = String.valueOf(directory.getFileName());
            return !directory.equals(this.project) && (UNSEARCHED.contains(name) || name.startsWith("."));
        }

        private boolean member(Path directory) {
            return Files.isRegularFile(directory.resolve(NodeManifests.MANIFEST)) && this.workspace.covers(directory);
        }
    }
}
