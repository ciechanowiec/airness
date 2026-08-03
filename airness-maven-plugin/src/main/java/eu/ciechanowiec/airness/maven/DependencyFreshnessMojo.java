package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredDependency;
import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * No declared dependency trails the latest stable release by two major versions or more.
 *
 * <p>This runs per module, because a declared dependency belongs to the pom that declares it. It reaches
 * the network, which is why it belongs to the slow verification profile, and it fails rather than passes
 * when the registry cannot be read: a dependency whose latest release is unknown is not a dependency
 * known to be current, so an outage that read as a green build would be the worst of both.
 */
@Mojo(name = "dependency-freshness", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class DependencyFreshnessMojo extends AbstractGovernanceMojo {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final String SEPARATOR = ":";

    @Override
    List<Findings> findings() {
        DependencyFreshnessCheck check = new DependencyFreshnessCheck(
            this.dependencies(), MAVEN_CENTRAL
        );
        this.getLog().info(
            "Dependency freshness asked Maven Central about " + check.scanned() + " dependency(ies)"
        );
        return check.findings();
    }

    private List<DeclaredDependency> dependencies() {
        Set<String> reactor = this.session().getAllProjects().stream()
            .map(DependencyFreshnessMojo::versionedKey)
            .collect(Collectors.toUnmodifiableSet());
        Map<String, Dependency> effective = this.project().getDependencies().stream().collect(
            Collectors.toMap(
                DependencyFreshnessMojo::key, Function.identity(), (first, second) -> first
            )
        );
        return this.project().getOriginalModel().getDependencies().stream()
            .map(declared -> effective.getOrDefault(key(declared), declared))
            .filter(dependency -> !reactor.contains(versionedKey(dependency)))
            .map(
                dependency -> Optional.ofNullable(dependency.getVersion()).map(
                    version -> new DeclaredDependency(
                        dependency.getGroupId(), dependency.getArtifactId(), version
                    )
                )
            )
            .flatMap(Optional::stream)
            .toList();
    }

    private static String key(Dependency dependency) {
        return dependency.getGroupId() + SEPARATOR + dependency.getArtifactId() + SEPARATOR
            + dependency.getType() + SEPARATOR + dependency.getClassifier();
    }

    private static String versionedKey(Dependency dependency) {
        return dependency.getGroupId() + SEPARATOR + dependency.getArtifactId() + SEPARATOR
            + dependency.getVersion();
    }

    private static String versionedKey(MavenProject project) {
        return project.getGroupId() + SEPARATOR + project.getArtifactId() + SEPARATOR + project.getVersion();
    }
}
