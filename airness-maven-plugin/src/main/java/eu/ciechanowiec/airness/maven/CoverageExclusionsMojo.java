package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CoverageExclusions;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Requires a reason for every class a project leaves out of the coverage floor, and hands JaCoCo the patterns.
 *
 * <p>The project declares each exclusion with its reason in {@code airness.coverage.excluded.classes}, which
 * JaCoCo cannot read, so this derives the patterns alone into {@code airness.coverage.excluded.patterns} before
 * the coverage check runs. The parents declare no value for that setting, so Maven reads it when the check runs
 * rather than when it builds the project, and a check run without this goal is handed no exclusion at all.
 */
@Mojo(name = "coverage-exclusions", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public final class CoverageExclusionsMojo extends AbstractGovernanceMojo {

    /**
     * The exclusions this project declared, each a class pattern, then {@code --}, then its reason.
     */
    @Parameter(property = CoverageExclusions.PROPERTY)
    private @Nullable String excluded;

    @Override
    List<Findings> findings() {
        String declared = Objects.toString(this.excluded, "");
        this.project().getProperties().setProperty(
            CoverageExclusions.PATTERNS, CoverageExclusions.jacocoPatterns(declared)
        );
        return List.of(new Findings("Coverage exclusions without a reason", CoverageExclusions.unreasoned(declared)));
    }
}
