package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.BlocklistCheck;
import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ModuleCoordinates;
import eu.ciechanowiec.airness.governance.RepositoryBlocklistCheck;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Nothing the project reaches is software Airness refuses by name: no coordinate its poms declare or
 * Maven resolves, no image a Dockerfile, a compose file, a workflow, or a Testcontainers literal pulls,
 * no system package a Dockerfile installs, no build extension, and no JDK that is not an open build,
 * whether a workflow installs it, a version manager selects it, or this build runs on it.
 *
 * <p>The licence allowlist judges an artifact by what its own pom says, and this judges it by its name,
 * because the licence that decides is often one the pom never states. The module half runs once per
 * module, since each module resolves its own artifacts and holds its own sources. The repository half,
 * the files outside the poms and the running JDK, runs once per session.
 *
 * <p>Bound at {@code package} rather than {@code validate}, because the resolved set is asked of Maven
 * and a goal that asks for it at {@code validate} asks for a reactor sibling nothing has built yet. The
 * licence check resolves at the same phase for the same reason.
 */
@Mojo(
    name = "blocklist",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public final class BlocklistMojo extends AbstractGovernanceMojo {

    private static final String RUNTIME_NAME = "java.runtime.name";

    @Override
    boolean applies() {
        return !RepositoryProjects.harnessParent(this.project());
    }

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        BlocklistCheck module = new BlocklistCheck(
            root, this.project().getFile().toPath(), this.moduleSourceRoots(), this.coordinates()
        );
        this.getLog().info(
            "Blocklist read " + module.scanned() + " subject(s) of " + this.project().getArtifactId()
        );
        boolean first = OncePerSession.firstRun(
            this.session().getRepositorySession().getData(), RepositoryBlocklistCheck.class
        );
        if (!first) {
            return module.findings();
        }
        RepositoryBlocklistCheck repository = new RepositoryBlocklistCheck(root, System.getProperty(RUNTIME_NAME, ""));
        this.getLog().info("Blocklist read " + repository.scanned() + " repository file(s) and the build JDK");
        return Stream.concat(module.findings().stream(), repository.findings().stream()).toList();
    }

    private ModuleCoordinates coordinates() {
        Properties properties = this.project().getProperties();
        Map<String, String> effective = properties.stringPropertyNames().stream()
            .collect(Collectors.toMap(name -> name, properties::getProperty));
        List<DeclaredCoordinate> resolved = this.project().getArtifacts().stream()
            .map(BlocklistMojo::coordinate)
            .toList();
        return ModuleCoordinates.of(this.project().getFile().toPath(), effective, resolved);
    }

    private static DeclaredCoordinate coordinate(Artifact artifact) {
        return new DeclaredCoordinate(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }
}
