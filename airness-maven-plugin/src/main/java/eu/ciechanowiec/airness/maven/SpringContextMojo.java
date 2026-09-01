package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringContextCheck;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Requires current-build evidence that the production Spring Boot application reached ready.
 *
 * <p>Bound after tests by {@code airness-parent-spring-boot}. The test JVM produces the evidence from
 * Spring Boot's run lifecycle, so this goal neither starts a second context nor prescribes a test
 * annotation.
 */
@Mojo(name = "spring-context", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class SpringContextMojo extends AbstractGovernanceMojo {

    @Parameter(
        defaultValue = "${project.build.directory}/airness/spring-context.evidence",
        readonly = true,
        required = true
    )
    private @Nullable String evidenceFile;

    @Override
    boolean applies() {
        return this.hasProductionJava();
    }

    @Override
    List<Findings> findings() {
        SpringContextCheck check = new SpringContextCheck(
            this.repositoryRoot(),
            this.moduleProductionSourceRoots(),
            Path.of(this.evidenceFile()),
            this.session().getStartTime().toInstant().toEpochMilli()
        );
        this.getLog().info(
            "Spring context evidence read " + check.scanned() + " production Java source(s) declaring "
                + check.applications() + " application class(es)"
        );
        return check.findings();
    }

    private String evidenceFile() {
        return Objects.requireNonNull(
            this.evidenceFile, "Maven did not inject the Spring context evidence path"
        );
    }
}
