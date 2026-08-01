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
 * Writes the files the harness owns into this project. The one goal here that writes anything.
 *
 * <p>It is bound to no phase, and that is the whole arrangement rather than an oversight. A build that
 * repaired the tree while verifying it would make a green result a statement about a tree the build had
 * reshaped, and no one afterwards could tell which of the two had been committed. So the repair is a
 * thing a person runs, by name, and the verifying build only ever reads.
 */
@Mojo(name = "assets-sync", threadSafe = true)
public class AssetsSyncMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Repository-relative paths this project has taken over, comma-separated. These are left alone.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private String unmanaged;

    @Override
    public void execute() {
        if (this.session.getTopLevelProject().equals(this.project)) {
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
