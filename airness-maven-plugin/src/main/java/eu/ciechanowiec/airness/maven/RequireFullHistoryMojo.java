package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The clone carries its whole history, so the checks that read it have something to read.
 *
 * <p>A truncated clone disarms the commit-history check, the commit-typography check, and any
 * history-wide secret scan all at once, and it disarms them silently: the commits that were never
 * fetched pass by not existing, and three gates report clean over a few dozen commits. This runs at
 * {@code validate} so the build stops before any of them has the chance.
 *
 * <p>A repository with no commits at all is a different thing and passes. There is nothing truncated
 * about a history that has not started, and failing on it would stop the first build of every new
 * project.
 */
@Mojo(name = "require-full-history", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class RequireFullHistoryMojo extends AbstractPreflightMojo {

    private static final String TRUNCATED
        = "This is a shallow clone, so every check that reads history would pass by reading almost none of "
            + "it. Fetch the whole history (fetch-depth: 0 on actions/checkout, or git fetch --unshallow)";

    @Override
    List<String> problems() {
        Path root = this.repositoryRoot();
        this.report(root);
        return Repository.isShallow(root) ? List.of(TRUNCATED) : List.of();
    }

    private void report(Path root) {
        if (!Repository.hasCommits(root)) {
            this.getLog().info("The repository has no commits yet, so there is no history to require");
        }
    }
}
