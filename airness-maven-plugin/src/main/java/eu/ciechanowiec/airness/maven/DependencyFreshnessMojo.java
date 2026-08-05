package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import eu.ciechanowiec.airness.governance.DeclaredCoordinates;
import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * No declared dependency or plugin trails the latest stable release by two major versions or more.
 *
 * <p>This runs once over every pom in the reactor. Reading raw poms reaches management, reporting,
 * plugin classpaths, annotation processors, and inactive profiles instead of limiting the verdict to
 * coordinates active in the effective model. It reaches the network, which is why it belongs to the
 * slow verification profile, and it fails rather than passes when the registry cannot be read.
 */
@Mojo(name = "dependency-freshness", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class DependencyFreshnessMojo extends AbstractGovernanceMojo {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String SEPARATOR = ":";

    @Override
    List<Findings> findings() {
        DependencyFreshnessCheck check = new DependencyFreshnessCheck(
            this.coordinates(), MAVEN_CENTRAL
        );
        this.getLog().info(
            "Dependency freshness asked Maven Central about " + check.scanned() + " coordinate(s)"
        );
        return check.findings();
    }

    @Override
    boolean applies() {
        return OncePerSession.firstRun(this.session(), this.getClass());
    }

    private List<DeclaredCoordinate> coordinates() {
        Set<String> reactor = this.session().getAllProjects().stream()
            .map(DependencyFreshnessMojo::versionedKey)
            .collect(Collectors.toUnmodifiableSet());
        return this.session().getAllProjects().stream()
            .map(MavenProject::getFile)
            .map(Optional::ofNullable)
            .flatMap(Optional::stream)
            .map(File::toPath)
            .flatMap(pom -> DeclaredCoordinates.from(pom).stream())
            .filter(coordinate -> !reactor.contains(versionedKey(coordinate)))
            .distinct()
            .toList();
    }

    private static String versionedKey(DeclaredCoordinate coordinate) {
        return coordinate.groupId() + SEPARATOR + coordinate.artifactId() + SEPARATOR
            + coordinate.version();
    }

    private static String versionedKey(MavenProject project) {
        return project.getGroupId() + SEPARATOR + project.getArtifactId() + SEPARATOR + project.getVersion();
    }
}
