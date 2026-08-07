package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AgentInstructions;
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
 * Writes the files the harness owns into this project when explicitly requested.
 *
 * <p>The parent does not bind this goal to the lifecycle. An ordinary build checks the files without
 * changing them, so its verdict describes the tree that started the build. A project opts into repair
 * by calling this goal directly.
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
        boolean instructions = new AgentInstructions(
            root, new AgentMaterials(AssetsSyncMojo.class.getClassLoader()).instructions()
        ).write();
        if (instructions) {
            this.getLog().info("Wrote the Airness-managed section in AGENTS.md");
        }
        List<String> written = new AssetSync(
            root, new AssetCatalogue(AssetsSyncMojo.class.getClassLoader()), Sentinel.optional(this.unmanaged)
        ).write();
        written.forEach(path -> this.getLog().info("Wrote " + path));
        this.summarize(written.size(), instructions);
    }

    private void summarize(int written, boolean instructions) {
        if (written == 0 && !instructions) {
            this.getLog().info("Every file the harness owns was already in place");
        }
    }
}
