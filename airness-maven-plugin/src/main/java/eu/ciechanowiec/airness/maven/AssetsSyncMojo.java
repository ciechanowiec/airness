package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetSync;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Writes the files the harness owns into this project before the lifecycle snapshots and checks the
 * working tree.
 *
 * <p>The parent binds this goal first at {@code validate}. The snapshot therefore records the repaired
 * tree, and the later tree check still detects any plugin that writes after validation. The goal remains
 * callable by name when a project wants to sync without running a lifecycle.
 */
@Mojo(name = "assets-sync", threadSafe = true)
public final class AssetsSyncMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Whether tests and the complete Airness harness are bypassed.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    /**
     * Repository-relative paths this project has taken over, comma-separated. These are left alone.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private String unmanaged;

    @Override
    public void execute() {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else if (this.session.getTopLevelProject().equals(this.project)) {
            this.write();
        }
    }

    private void write() {
        Path root = Repository.rootFrom(this.project.getBasedir().toPath());
        List<String> written = new AssetSync(
            root, new AssetCatalogue(AssetsSyncMojo.class.getClassLoader()), Sentinel.optional(this.unmanaged)
        ).write();
        written.forEach(path -> this.getLog().info("Wrote " + path));
        this.summarize(written.size());
    }

    private void summarize(int written) {
        if (written == 0) {
            this.getLog().info("Every file the harness owns was already in place");
        }
    }
}
