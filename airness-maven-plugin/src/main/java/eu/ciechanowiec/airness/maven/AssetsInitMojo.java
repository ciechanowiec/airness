package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetSync;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Initializes managed files and optional project-owned scaffolds. Ordinary synchronization never writes scaffolds.
 */
@Mojo(name = "assets-init", threadSafe = true)
@Execute(goal = "assets-sync")
public final class AssetsInitMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "airness.assets.unmanaged")
    private @Nullable String unmanaged;

    private final ProjectBuilder builder;

    /**
     * Receives the builder that reads the models of the repository.
     *
     * @param builder the Maven project builder
     */
    @Inject
    public AssetsInitMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void execute() {
        if (this.skip) {
            this.getLog().info("Skipping Airness because skipTests is true");
        } else {
            this.initialize();
        }
    }

    private void initialize() {
        MavenSession current = Objects.requireNonNull(this.session, "Maven did not inject the active session");
        if (!RepositoryProjects.owns(current.getTopLevelProject(), current.getCurrentProject())) {
            this.getLog().info(
                "This module does not own the repository scaffolds; initialize from the repository root"
            );
            return;
        }
        Path root = Repository.rootFrom(current.getTopLevelProject().getBasedir().toPath());
        new AssetSync(
            root, new AssetCatalogue(AssetsInitMojo.class.getClassLoader()), Sentinel.optional(this.unmanaged),
            RepositorySites.read(this.builder, current.getProjectBuildingRequest(), root)
        ).scaffold().forEach(file -> this.getLog().info("Created project scaffold " + file));
    }
}
