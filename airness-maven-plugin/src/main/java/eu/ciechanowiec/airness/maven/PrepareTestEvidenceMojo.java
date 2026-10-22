package eu.ciechanowiec.airness.maven;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Removes previous-invocation evidence before Surefire and Failsafe append the current tests' evidence.
 */
@Mojo(name = "prepare-test-evidence", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class PrepareTestEvidenceMojo extends AbstractPreflightMojo {

    @Parameter(property = "jacoco.destFile", defaultValue = "${project.build.directory}/jacoco.exec", required = true)
    private @Nullable File coverage;

    @Override
    boolean applies() {
        return true;
    }

    @Override
    List<String> problems() {
        InvocationEvidence.prepare(
            this.session().getRepositorySession().getData(), Path.of(this.project().getBuild().getDirectory()),
            Objects.requireNonNull(this.coverage, "Maven did not inject the JaCoCo destination").toPath()
        );
        return List.of();
    }
}
