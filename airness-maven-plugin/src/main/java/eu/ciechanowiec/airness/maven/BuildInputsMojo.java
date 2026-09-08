package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.IgnoredBuildInputs;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Refuses ignored repository inputs that would make the build depend on unsubmitted files.
 */
@Mojo(name = "build-inputs", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class BuildInputsMojo extends AbstractPreflightMojo {

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
        return new IgnoredBuildInputs(this.repositoryRoot(), model.selections(), outputs).problems();
    }
}
