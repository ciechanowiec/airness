package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AgentInstructions;
import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetSync;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

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
    private @Nullable MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    /**
     * Whether tests and Airness-managed operations are bypassed.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    /**
     * Repository-relative paths this project has taken over, comma-separated. These are left alone.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private @Nullable String unmanaged;

    /**
     * Writes the managed files once, from the project that owns the repository.
     *
     * <p>Ownership is decided by {@link RepositoryProjects}, the same answer {@code assets-check}
     * uses. Asking only whether this is the top-level project left the two goals disagreeing about
     * which project owns the assets, so the harness could check its own files and never sync them.
     *
     * <p>Every branch says something. A goal a project is told to run and then review has to
     * distinguish "nothing needed writing" from "this project was not the one to write it", and a
     * silent exit with an empty diff reads identically to both.
     */
    @Override
    public void execute() {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else if (RepositoryProjects.owns(this.session().getTopLevelProject(), this.project())) {
            this.write();
        } else {
            this.getLog().info(
                "Skipping Airness because " + this.project().getArtifactId()
                    + " does not own the repository files; run this goal from "
                    + this.session().getTopLevelProject().getArtifactId()
            );
        }
    }

    private void write() {
        Path root = Repository.rootFrom(this.project().getBasedir().toPath());
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

    private MavenProject project() {
        return Objects.requireNonNull(this.project, "Maven did not inject the current project");
    }

    private MavenSession session() {
        return Objects.requireNonNull(this.session, "Maven did not inject the current session");
    }
}
