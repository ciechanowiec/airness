package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.CoverageReport;
import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Requires current-build coverage evidence whenever a module has production Java sources, and requires
 * every declared coverage exclusion to name something that evidence measured.
 */
@Mojo(name = "coverage-evidence", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class CoverageEvidenceMojo extends AbstractGovernanceMojo {

    @Parameter(property = "jacoco.dataFile", defaultValue = "${project.build.directory}/jacoco.exec")
    private @Nullable String dataFile;

    /**
     * The report the coverage tool wrote, which names every class it measured before any exclusion is
     * applied. That is what makes it the place to ask whether an exclusion reached anything.
     */
    @Parameter(
        property = "jacoco.reportFile",
        defaultValue = "${project.reporting.outputDirectory}/jacoco/jacoco.xml"
    )
    private @Nullable String reportFile;

    /**
     * The exclusions this project declared, read here to check that each one still names a class.
     */
    @Parameter(property = "airness.coverage.excluded.classes")
    private @Nullable String excluded;

    @Override
    boolean applies() {
        return this.hasProductionJava();
    }

    @Override
    List<Findings> findings() {
        Path evidence = Path.of(this.dataFile());
        boolean current = Files.isRegularFile(evidence)
            && modified(evidence) >= this.session().getStartTime().toInstant().toEpochMilli();
        List<String> offences = current ? List.of() : List.of(
            evidence + " is missing or predates this build; production code requires tests from this run"
        );
        return List.of(
            new Findings("Missing current-build JaCoCo evidence", offences),
            new Findings("Coverage exclusions that name no class the report measured", this.unreached())
        );
    }

    /**
     * Read only when an exclusion was declared and the report is there to read it against.
     *
     * <p>The report is a different file from the evidence the rule above requires, so its absence is
     * not that finding restated. Nothing reports it, which is why {@code jacoco.reportFile} is a name
     * a project file may not declare: pointing it somewhere empty would leave every exclusion
     * unexamined and the rule reading clean.
     *
     * @return one entry per declared pattern that names no class the report measured
     */
    private List<String> unreached() {
        List<String> declared = Sentinel.optional(this.excluded);
        Path report = Path.of(this.reportFile());
        return declared.isEmpty() || !Files.isRegularFile(report)
            ? List.of()
            : new CoverageReport(report).unreached(declared).stream()
                .map(pattern -> pattern + " excludes nothing, so it names a class that moved or went")
                .toList();
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the age of " + file, exception);
        }
    }

    private String dataFile() {
        return Objects.requireNonNull(this.dataFile, "Maven did not inject jacoco.dataFile");
    }

    private String reportFile() {
        return Objects.requireNonNull(this.reportFile, "Maven did not inject jacoco.reportFile");
    }
}
