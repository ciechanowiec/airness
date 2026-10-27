package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
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
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Every {@code package.json} below a module that belongs to the module.
 *
 * <p>The files read are the ones a commit would carry, as the other repository checks read them: build output
 * that git ignores, such as a framework's {@code .next/package.json} or a bundler's {@code dist/cjs} stub, is
 * not a node project anybody wrote. Outside a working tree, or without git, a walk stands in that passes over
 * the usual output and installation directories instead. A directory a child module owns is left to that
 * module either way.
 */
@UtilityClass
final class NodeManifests {

    private static final String MANIFEST = "package.json";
    private static final Set<String> PRUNED = Set.of("node_modules", "target", ".git", "dist", ".next", "build");

    /**
     * Lists the manifests of one module.
     *
     * @param module  the module directory, absolute and normalized
     * @param skipped the directories that belong to something else: child modules and the build directory
     * @return the manifests, sorted
     */
    static List<Path> in(Path module, Collection<Path> skipped) {
        return Repository.committableFiles(module).orElseGet(() -> walked(module)).stream()
            .filter(file -> MANIFEST.equals(String.valueOf(file.getFileName())))
            .map(Path::normalize)
            .filter(file -> skipped.stream().noneMatch(file::startsWith))
            .sorted()
            .toList();
    }

    private static List<Path> walked(Path module) {
        ManifestWalk walk = new ManifestWalk(module);
        try {
            Files.walkFileTree(module, walk);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not walk " + module + " for package.json files", exception);
        }
        return walk.manifests();
    }

    /**
     * Collects every {@code package.json} below a module, without entering output or installation directories.
     */
    private static final class ManifestWalk implements FileVisitor<Path> {

        private final Path module;
        private final List<Path> manifests;

        ManifestWalk(Path module) {
            this.module = module;
            this.manifests = new ArrayList<>();
        }

        List<Path> manifests() {
            return List.copyOf(this.manifests);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            boolean pruned = !directory.equals(this.module) && PRUNED.contains(String.valueOf(directory.getFileName()));
            return pruned ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile() && MANIFEST.equals(String.valueOf(file.getFileName()))) {
                this.manifests.add(file);
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
    }
}
