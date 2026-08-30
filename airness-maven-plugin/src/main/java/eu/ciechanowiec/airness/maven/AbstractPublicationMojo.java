package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * A publication check that remains mandatory when the already-verified release skips its tests.
 */
abstract class AbstractPublicationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    abstract List<Findings> findings();

    boolean applies() {
        return true;
    }

    @Override
    public final void execute() throws MojoFailureException {
        if (!this.applies()) {
            this.getLog().debug("Nothing for this publication goal to read here");
            return;
        }
        List<Findings> broken = this.findings().stream().filter(finding -> !finding.clean()).toList();
        broken.forEach(finding -> this.getLog().error(finding.report()));
        if (!broken.isEmpty()) {
            throw new MojoFailureException(broken.size() + " Maven publication rule(s) reported findings");
        }
    }

    protected final MavenProject project() {
        return Objects.requireNonNull(this.project, "Maven did not inject the current project");
    }

    protected final Path repositoryRoot() {
        return Repository.rootFrom(this.project().getBasedir().toPath());
    }
}
