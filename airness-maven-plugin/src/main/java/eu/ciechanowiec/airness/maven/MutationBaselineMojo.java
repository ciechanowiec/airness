package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.MutationBaselineCheck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The mutants a run left alive are exactly the ones this repository has accepted, and the run produced
 * mutants at all.
 *
 * <p>Whether the report belongs to this build is checked here rather than in the library, because only
 * the build knows when it started. A report left behind by an earlier run reads as current, so a build
 * without {@code clean} would otherwise compare the baseline against yesterday's analysis and report on
 * code that has since changed.
 */
@Mojo(name = "mutation-baseline", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class MutationBaselineMojo extends GovernanceMojo {

    /**
     * The report the mutation analysis wrote.
     */
    @Parameter(
        property = "airness.mutation.report",
        defaultValue = "${project.build.directory}/pit-reports/mutations.xml"
    )
    private String report;

    /**
     * The file listing the survivors this project accepts, one per line with its reason.
     */
    @Parameter(property = "airness.mutation.baseline", defaultValue = "mutation-baseline.tsv")
    private String baseline;

    /**
     * Whether this module is one the mutation analysis runs on.
     *
     * <p>The condition is that the module has Java sources, not that a report is sitting there. Skipping
     * on a missing report would turn the one failure worth having into a silent pass: an analysis
     * configured to mutate nothing writes no report, and so does an analysis that never ran, and both
     * would then read as a module the gate had nothing to say about.
     *
     * @return whether the module has sources for the analysis to mutate
     */
    @Override
    protected boolean applies() {
        return !this.moduleSourceRoots().isEmpty();
    }

    @Override
    protected List<Findings> findings() {
        Path analysis = Path.of(this.report);
        this.requireCurrent(analysis);
        MutationBaselineCheck check = new MutationBaselineCheck(
            analysis, this.project().getBasedir().toPath().resolve(this.baseline)
        );
        this.getLog().info("Mutation analysis produced " + check.mutants() + " mutant(s)");
        return check.findings();
    }

    private void requireCurrent(Path analysis) {
        if (modified(analysis) < this.session().getStartTime().getTime()) {
            throw new IllegalStateException(
                analysis + " predates this build, so it describes code that may since have changed. Run with clean"
            );
        }
    }

    private static long modified(Path analysis) {
        try {
            return Files.getLastModifiedTime(analysis).toMillis();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the age of " + analysis, exception);
        }
    }
}
