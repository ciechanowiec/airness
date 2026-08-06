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
        return OncePerSession.firstRun(this.session(), this.getClass(), this.scope());
    }

    @Override
    List<String> problems() {
        TreeState.snapshot(this.session(), this.repositoryRoot(), this.scope());
        return List.of();
    }

    private String scope() {
        return TreeState.scope(this.project().getFile().toPath());
    }
}
