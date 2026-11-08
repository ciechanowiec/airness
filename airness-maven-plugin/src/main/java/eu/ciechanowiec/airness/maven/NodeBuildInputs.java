package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.BuildInput;
import eu.ciechanowiec.airness.maven.NodeProjects.NodeProject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The scripts and stylesheets under the checked source roots of a module's declared node projects, as the
 * build inputs the node toolchain reads.
 *
 * <p>The type check, the tests and the node build read these files from disk whatever Git makes of them,
 * while every node gate reads only the files Git does not ignore. So a file an ignore pattern happens to
 * match, such as {@code src/server/http.logger.ts} under a {@code *.log*} line or {@code src/app/build/page.tsx}
 * under {@code build/}, passes every local build, is judged by no gate, and is absent from the commit.
 *
 * <p>Installed packages are passed over wherever they lie, and so is what a node tool writes at the top of a
 * project, which only a source root of {@code .} reaches. The files a project names in its
 * {@code generatedArtefacts} are a generator's output, and are passed over too.
 */
@UtilityClass
final class NodeBuildInputs {

    private static final List<String> READ = List.of(
        ".js", ".mjs", ".cjs", ".jsx", ".ts", ".mts", ".cts", ".tsx", ".css", ".scss"
    );
    private static final Set<String> UNSEARCHED = Set.of("node_modules", ".git");
    private static final Set<String> OUTPUTS = Set.of(
        ".next", ".turbo", ".type-coverage", ".vercel", "blob-report", "build", "coverage", "dist", "out",
        "playwright-report", "target", "test-results"
    );
    private static final String NEXT_TYPES = "next-env.d.ts";

    /**
     * The node build inputs of one module.
     *
     * @param module   the module's artifact id, which names the inputs in a finding
     * @param declared the node projects the module declares
     * @return one input per existing checked source root of each project inside the module with a manifest
     */
    static List<BuildInput> of(String module, NodeProjects declared) {
        return declared.projects().stream()
            .filter(project -> !project.outside() && project.hasManifest())
            .map(NodeProject::directory)
            .flatMap(project -> inputs(module, project.toAbsolutePath().normalize()))
            .toList();
    }

    private static Stream<BuildInput> inputs(String module, Path project) {
        NodeMember member = NodeMember.at(project);
        return member.checkedRoots().stream()
            .map(root -> project.resolve(root).normalize())
            .distinct()
            .filter(Files::isDirectory)
            .map(root -> new BuildInput(root, module + " node source", read(project, member, root)));
    }

    private static Set<String> read(Path project, NodeMember member, Path root) {
        Selection selection = new Selection(project, member, root);
        try {
            Files.walkFileTree(root, selection);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list the node sources under " + root, exception);
        }
        return Set.copyOf(selection.names);
    }

    private static final class Selection implements FileVisitor<Path> {

        private final Path project;
        private final NodeMember member;
        private final Path root;
        private final List<String> names;

        Selection(Path project, NodeMember member, Path root) {
            this.project = project;
            this.member = member;
            this.root = root;
            this.names = new ArrayList<>();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            String name = String.valueOf(directory.getFileName());
            boolean output = OUTPUTS.contains(name) && this.project.equals(directory.getParent());
            return UNSEARCHED.contains(name) || output ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile() && this.read(file)) {
                this.names.add(this.root.relativize(file).toString().replace('\\', '/'));
            }
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

        private boolean read(Path file) {
            String name = String.valueOf(file.getFileName());
            Path relative = this.project.relativize(file);
            boolean typesOfNext = NEXT_TYPES.equals(name) && this.project.equals(file.getParent());
            return READ.stream().anyMatch(name::endsWith) && !typesOfNext
                && this.member.generated().stream().noneMatch(glob -> glob.matches(relative));
        }
    }
}
