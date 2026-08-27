package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ArtifactContentCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ModuleOutput;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Inspects the completed JAR for content that source-tree checks cannot see.
 *
 * <p>The goal runs at verify rather than at package, because package is where the archive is produced
 * and not where it is finished. Maven merges an inherited plugin ahead of one the project declares, so
 * within the package phase every goal this parent binds runs before every goal the project binds. A
 * project that repackages, through the shade plugin, the assembly plugin, or Spring Boot, therefore had
 * this goal read the thin archive the jar plugin had just written and never the archive that ships. The
 * verify phase is the first point Maven guarantees is past the whole of package, whichever plugin did
 * the repackaging, and Airness cannot know which one a project binds.
 *
 * <p>What is read is the file Maven holds against the project artifact rather than a path assembled
 * from the final name. A repackaging plugin replaces that file, so the artifact is the archive Maven
 * goes on to install and deploy, while the assembled path only happens to name the same file in a build
 * that does not repackage. The assembled path remains the fallback for the goal invoked on its own,
 * where no packaging plugin has run and the project artifact carries no file yet.
 */
@Mojo(name = "artifact-content", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class ArtifactContentMojo extends AbstractGovernanceMojo {

    private static final String ARCHIVE = ".jar";

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar", readonly = true)
    private String artifact;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private String mainOutput;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
    private String testOutput;

    @Override
    boolean applies() {
        return JarPackaging.produced(this.project().getPackaging());
    }

    @Override
    List<Findings> findings() {
        Path jar = this.shipped();
        if (!Files.isRegularFile(jar)) {
            return List.of(new Findings("Missing packaged JAR", List.of(jar + " does not exist")));
        }
        return new ArtifactContentCheck(
            jar,
            new ModuleOutput(Path.of(this.mainOutput), Path.of(this.testOutput)),
            this.repositoryRoot(),
            this.vendored()
        ).findings();
    }

    private Path shipped() {
        return Optional.ofNullable(this.project().getArtifact())
            .map(Artifact::getFile)
            .map(File::toPath)
            .orElseGet(() -> Path.of(this.artifact));
    }

    private List<Path> vendored() {
        return this.project().getArtifacts().stream()
            .map(Artifact::getFile)
            .filter(Objects::nonNull)
            .map(File::toPath)
            .filter(archive -> archive.getFileName().toString().endsWith(ARCHIVE))
            .filter(Files::isRegularFile)
            .toList();
    }
}
