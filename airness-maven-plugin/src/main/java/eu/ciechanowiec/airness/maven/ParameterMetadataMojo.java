package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ModuleOutput;
import eu.ciechanowiec.airness.governance.ParameterMetadataCheck;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Requires compiler parameter metadata even when report-only or skipped-test modes are active.
 *
 * <p>This is a compiler-output invariant rather than a quality finding. Letting
 * {@code airness.enforce=false} or {@code skipTests=true} bypass it would allow those modes to produce
 * bytecode with a different reflection contract from an ordinary build.
 */
@Mojo(name = "compiler-parameters", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class ParameterMetadataMojo extends AbstractMojo {

    private static final String POM = "pom";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private @Nullable MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    private @Nullable String mainOutput;

    @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true, required = true)
    private @Nullable String testOutput;

    @Override
    public void execute() throws MojoFailureException {
        if (compilationBound(this.project().getPackaging())) {
            this.verifyMetadata();
        } else {
            this.getLog().debug("POM packaging binds no Java compilation to inspect");
        }
    }

    static boolean compilationBound(String packaging) {
        return !POM.equals(packaging);
    }

    private void verifyMetadata() throws MojoFailureException {
        Findings findings = new ParameterMetadataCheck(
            this.project().getCompileSourceRoots().stream().map(Path::of).toList(),
            this.project().getTestCompileSourceRoots().stream().map(Path::of).toList(),
            new ModuleOutput(Path.of(this.mainOutput()), Path.of(this.testOutput()))
        ).findings();
        if (!findings.clean()) {
            this.getLog().error(findings.report());
            throw new MojoFailureException(
                findings.offences().size() + " compiled method(s) lack formal parameter metadata"
            );
        }
    }

    private MavenProject project() {
        return Objects.requireNonNull(this.project, "Maven did not inject the current project");
    }

    private String mainOutput() {
        return Objects.requireNonNull(this.mainOutput, "Maven did not inject the main output path");
    }

    private String testOutput() {
        return Objects.requireNonNull(this.testOutput, "Maven did not inject the test output path");
    }
}
