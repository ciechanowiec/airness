package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.Maven;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

/**
 * Requires every profile named by the build to exist.
 *
 * <p>A missing profile is a different build rather than a quality finding. In {@link Maven} 3, a misspelled
 * {@code -Pextended} prints a warning and continues without the checks that profile binds. Neither
 * {@code airness.enforce=false} nor {@code skipTests=true} may turn that command into a successful
 * Default build that reads as Extended verification.
 *
 * <p>{@link Maven} 4 owns this validation before lifecycle execution and distinguishes a required
 * selector from an optional {@code ?profile}. Airness therefore supplies the missing {@link Maven} 3
 * verdict and leaves {@link Maven} 4 and later to their native rule.
 */
@Mojo(name = "required-profiles", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class RequiredProfilesMojo extends AbstractMojo {

    private static final int MAVEN_FOUR_MAJOR = 4;
    private static final Pattern VERSION_MAJOR = Pattern.compile("^(?<major>[0-9]+)");

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    @Override
    public void execute() throws MojoFailureException {
        MavenSession current = this.session();
        boolean first = OncePerSession.firstRun(
            current.getRepositorySession().getData(), this.getClass()
        );
        if (first) {
            this.verifyOnce(current);
        } else {
            this.getLog().debug("Required Maven profiles were already checked in this session");
        }
    }

    static List<String> missingProfiles(
        Collection<MavenProject> projects,
        Collection<Profile> external,
        Collection<String> active,
        Collection<String> inactive
    ) {
        Set<String> available = availableProfiles(projects, external);
        Set<String> requested = Stream.concat(active.stream(), inactive.stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        requested.removeAll(available);
        return List.copyOf(requested);
    }

    static boolean nativeValidation(String version) {
        Matcher major = VERSION_MAJOR.matcher(version);
        if (!major.find()) {
            throw new IllegalStateException("Maven supplied an unreadable runtime version: " + version);
        }
        return Integer.parseInt(major.group("major")) >= MAVEN_FOUR_MAJOR;
    }

    private static Set<String> availableProfiles(
        Collection<MavenProject> projects, Collection<Profile> external
    ) {
        Stream<String> declared = projects.stream()
            .flatMap(RequiredProfilesMojo::lineage)
            .map(MavenProject::getOriginalModel)
            .filter(Objects::nonNull)
            .flatMap(model -> model.getProfiles().stream())
            .map(Profile::getId);
        Stream<String> injected = projects.stream()
            .flatMap(project -> project.getInjectedProfileIds().values().stream())
            .flatMap(Collection::stream);
        Stream<String> settings = external.stream().map(Profile::getId);
        return Stream.of(declared, injected, settings)
            .flatMap(Function.identity())
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<MavenProject> lineage(MavenProject project) {
        return Stream.iterate(project, Objects::nonNull, MavenProject::getParent);
    }

    private static String mavenVersion() {
        return Objects.requireNonNull(
            Maven.class.getPackage().getImplementationVersion(),
            "Maven core carries no implementation version in its manifest"
        );
    }

    private void verifyOnce(MavenSession current) throws MojoFailureException {
        if (nativeValidation(mavenVersion())) {
            this.getLog().debug("Maven 4 or later owns required profile validation");
        } else {
            this.verify(current);
        }
    }

    private void verify(MavenSession current) throws MojoFailureException {
        MavenExecutionRequest request = current.getRequest();
        List<String> missing = missingProfiles(
            current.getProjects(), request.getProfiles(),
            request.getActiveProfiles(), request.getInactiveProfiles()
        );
        if (!missing.isEmpty()) {
            String message = "The requested profiles [" + String.join(", ", missing)
                + "] could not be activated or deactivated because they do not exist; correct the -P"
                + " selector or declare the profile in a selected project, its parent, or Maven settings";
            this.getLog().error(message);
            throw new MojoFailureException(message);
        }
    }

    private MavenSession session() {
        return Objects.requireNonNull(this.session, "Maven did not inject the active session");
    }
}
