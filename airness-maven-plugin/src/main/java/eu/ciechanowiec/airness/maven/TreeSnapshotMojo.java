package eu.ciechanowiec.airness.maven;

import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Records the working tree before any build plugin can edit it. */
@Mojo(name = "tree-snapshot", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class TreeSnapshotMojo extends PreflightMojo {

    @Override
    protected List<String> problems() {
        TreeState.snapshot(this.session(), this.repositoryRoot());
        return List.of();
    }
}
