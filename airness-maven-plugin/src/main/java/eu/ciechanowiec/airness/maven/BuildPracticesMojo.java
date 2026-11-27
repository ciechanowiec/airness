package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.BuildPracticesCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.FirstCommit;
import eu.ciechanowiec.airness.governance.InheritedMetadataCheck;
import eu.ciechanowiec.airness.governance.MetadataBounds;
import eu.ciechanowiec.airness.governance.ModuleIdentityCheck;
import eu.ciechanowiec.airness.governance.PomLayoutCheck;
import eu.ciechanowiec.airness.governance.PomPortabilityCheck;
import eu.ciechanowiec.airness.governance.RootMetadataCheck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Holds the project file, and at the repository root the workflow files, to current build practices, and the
 * project file to the pom layout, to portable declarations and to an identity of the expected shape.
 *
 * <p>The root project states the project's own metadata, and a module below it inherits none from Airness. A module
 * of Airness's own tree is exempt from the second rule, since there the Airness poms are the project's own.
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
        Path repository = this.repositoryRoot();
        boolean root = this.project().getBasedir().toPath().toAbsolutePath().normalize()
            .equals(repository.toAbsolutePath().normalize());
        return Stream.of(
            new BuildPracticesCheck(pom, root ? workflows(repository) : List.of()).findings(),
            new PomLayoutCheck(pom).findings(),
            new PomPortabilityCheck(pom, repository).findings(),
            new ModuleIdentityCheck(ProjectFacts.identity(this.project(), root)).findings(),
            root ? this.rootMetadata(repository) : this.inheritedMetadata(repository)
        ).flatMap(List::stream).toList();
    }

    private List<Findings> rootMetadata(Path repository) {
        return new RootMetadataCheck(
            ProjectFacts.metadata(this.project().getOriginalModel()), ProjectFacts.metadata(this.project().getModel()),
            new MetadataBounds(
                SpdxLicenses.current(), Year.now(ZoneId.systemDefault()), FirstCommit.year(repository),
                ProjectFacts.harness(this.project())
            )
        ).findings();
    }

    private List<Findings> inheritedMetadata(Path repository) {
        return RepositoryProjects.selfTree(this.project(), repository)
            ? List.of()
            : new InheritedMetadataCheck(ProjectFacts.sources(this.project())).findings();
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
