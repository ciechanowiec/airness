package eu.ciechanowiec.airness.maven;

import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Records the working tree before any build plugin can edit it.
 */
@Mojo(name = "tree-snapshot", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class TreeSnapshotMojo extends AbstractPreflightMojo {

    @Override
    boolean applies() {
        return OncePerSession.firstRun(
            this.session().getRepositorySession().getData(), this.getClass(), this.scope()
        );
    }

    @Override
    List<String> problems() {
        TreeState.snapshot(
            this.session().getRepositorySession().getData(), this.repositoryRoot(), this.scope()
        );
        if (ReportProjects.extended(this.project())) {
            ReportSession.begin(this.session().getRepositorySession().getData(), this.repositoryRoot());
        }
        return List.of();
    }

    private String scope() {
        return TreeState.scope(this.project().getFile().toPath());
    }
}
