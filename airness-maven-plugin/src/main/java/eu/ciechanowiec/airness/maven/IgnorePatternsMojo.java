package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.IgnorePatternCheck;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * No {@code .gitignore} or Docker ignore file repeats a pattern that an earlier declaration in it, or in a
 * {@code .gitignore} of a directory above it, already applies.
 */
@Mojo(name = "ignore-patterns", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class IgnorePatternsMojo extends AbstractRepositoryMojo {

    @Override
    List<Findings> findings() {
        IgnorePatternCheck check = new IgnorePatternCheck(this.repositoryRoot());
        this.getLog().info("Ignore patterns read " + check.scanned() + " ignore file(s)");
        return check.findings();
    }
}
