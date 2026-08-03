package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The parameters the harness cannot default are set and the inherited Airness artifacts agree.
 *
 * <p>Each of these has the same failure mode, which is why they are checked before any analyzer runs
 * rather than being left to fail where they are read. Left at {@code UNSET}, the package root makes the
 * null-checker treat every class as unannotated and makes the
 * mutation analysis find no mutants, which reports as a perfect kill rate.
 *
 * <p>A derived default would be worse than none. Deriving the package root from the coordinates is right
 * for a project whose artifactId is one word and wrong for every hyphenated one, and a default that is
 * usually right is a default nobody checks.
 */
@Mojo(name = "check-parameters", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class CheckParametersMojo extends PreflightMojo {

    private static final String UNSET = "UNSET";

    /**
     * The package every class of this project lives under, unescaped.
     */
    @Parameter(property = "airness.package.root", defaultValue = UNSET)
    private String packageRoot;

    /**
     * The release every inherited Airness artifact must use.
     */
    @Parameter(property = "airness.version", required = true)
    private String airnessVersion;

    @Override
    protected List<String> problems() {
        this.record();
        return Stream.of(
            set(this.packageRoot, "airness.package.root", "the package every class lives under"),
            this.versionAgreement()
        ).flatMap(Optional::stream).toList();
    }

    private void record() {
        this.getLog().info("airness.package.root = " + this.packageRoot);
        this.getLog().info("airness.version = " + this.airnessVersion);
    }

    /**
     * Whether the artifact versions used by the inherited parent and its plugins agree.
     *
     * @return the problem, if the two disagree
     */
    private Optional<String> versionAgreement() {
        String parent = this.project().getParent() == null ? "" : this.project().getParent().getVersion();
        return Optional.of("airness.version (" + this.airnessVersion + ") does not match the inherited "
            + "airness-parent version (" + parent + ")")
            .filter(problem -> !Objects.equals(this.airnessVersion, parent));
    }

    private static Optional<String> set(String value, String property, String what) {
        return Optional.of("Set " + property + " to " + what + ", which the harness cannot guess")
            .filter(problem -> UNSET.equals(value));
    }
}
