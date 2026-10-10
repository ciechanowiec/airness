package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Requires the harness repository policy even when tests or ordinary enforcement are bypassed.
 *
 * <p>The managed global settings apply at Maven startup, before parent and plugin resolution. This
 * preflight rejects a build that replaced them. It cannot undo downloads made before the goal ran.
 * Direct repair goals remain available to restore the managed files, followed by a new invocation.
 */
@Mojo(name = "repository-policy", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class RepositoryPolicyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    @Override
    public void execute() throws MojoFailureException {
        MavenSession current = Objects.requireNonNull(this.session, "Maven did not inject the active session");
        if (OncePerSession.firstRun(current.getRepositorySession().getData(), this.getClass())) {
            this.verify(current);
        }
    }

    private void verify(MavenSession current) throws MojoFailureException {
        Path root = Repository.rootFrom(current.getTopLevelProject().getBasedir().toPath());
        List<String> problems = new MavenRepositoryPolicy(
            root, current.getRequest(), current.getRepositorySession().getMirrorSelector(),
            new AssetCatalogue(RepositoryPolicyMojo.class.getClassLoader())
        ).problems();
        problems.forEach(problem -> this.getLog().error(problem));
        if (!problems.isEmpty()) {
            throw new MojoFailureException("Airness repository policy is not active");
        }
    }
}
