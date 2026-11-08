package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.IgnoredBuildInputs;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Refuses ignored repository inputs that would make the build depend on unsubmitted files: the sources and
 * resources the compiler and resources plugins select, and the scripts and stylesheets under the checked source
 * roots of the module's declared node projects.
 */
@Mojo(name = "build-inputs", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class BuildInputsMojo extends AbstractPreflightMojo {

    /**
     * The module's node project directories, comma-separated and relative to the module.
     */
    @Parameter(property = NodeProjects.PROPERTY)
    private @Nullable String declared;

    private final DefaultLifecycles lifecycles;

    /**
     * Uses Maven's registered lifecycle phases to resolve active input executions.
     *
     * @param lifecycles the lifecycle definitions supplied by Maven
     */
    @Inject
    public BuildInputsMojo(DefaultLifecycles lifecycles) {
        this.lifecycles = lifecycles;
    }

    @Override
    boolean applies() {
        return true;
    }

    @Override
    List<String> problems() {
        List<Path> outputs = this.session().getAllProjects().stream()
            .map(project -> project.getBasedir().toPath().resolve(project.getBuild().getDirectory()))
            .map(path -> path.toAbsolutePath().normalize()).distinct().toList();
        ProjectBuildInputs model = new ProjectBuildInputs(
            this.project(), outputs, this.repositoryRoot(), this.lifecycles.getPhaseToLifecycleMap().keySet()
        );
        NodeProjects projects = NodeProjects.of(this.project().getBasedir().toPath(), this.declared);
        return new IgnoredBuildInputs(
            this.repositoryRoot(),
            Stream.concat(
                model.selections().stream(), NodeBuildInputs.of(this.project().getArtifactId(), projects).stream()
            ).toList(),
            outputs
        ).problems();
    }
}
