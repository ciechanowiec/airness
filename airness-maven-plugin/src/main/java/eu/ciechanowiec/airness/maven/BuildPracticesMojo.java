package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.BuildPracticesCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Holds the project file, and at the repository root the workflow files, to current build practices.
 *
 * <p>The parents Airness publishes are left alone: they are the harness rather than a project under it.
 */
@Mojo(name = "build-practices", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class BuildPracticesMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !RepositoryProjects.harnessParent(this.project());
    }

    @Override
    List<Findings> findings() {
        Path pom = this.project().getFile().toPath();
        boolean root = this.project().getBasedir().toPath().toAbsolutePath().normalize()
            .equals(this.repositoryRoot().toAbsolutePath().normalize());
        return new BuildPracticesCheck(pom, root ? workflows(this.repositoryRoot()) : List.of()).findings();
    }

    private static List<Path> workflows(Path repository) {
        Path directory = repository.resolve(".github").resolve("workflows");
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().matches(".*\\.ya?ml")).sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list the workflows in " + directory, exception);
        }
    }
}
