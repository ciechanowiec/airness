package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredDependency;
import eu.ciechanowiec.airness.governance.DependencyFreshnessCheck;
import eu.ciechanowiec.airness.governance.Findings;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * No declared dependency trails the latest stable release by two major versions or more.
 *
 * <p>This runs per module, because a declared dependency belongs to the pom that declares it. It reaches
 * the network, which is why it belongs to the slow verification profile, and it fails rather than passes
 * when the registry cannot be read: a dependency whose latest release is unknown is not a dependency
 * known to be current, so an outage that read as a green build would be the worst of both.
 */
@Mojo(name = "dependency-freshness", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class DependencyFreshnessMojo extends GovernanceMojo {

    /**
     * The base URL of the registry asked about each dependency.
     *
     * <p>A parameter rather than a constant, so a project behind a mirror can point it elsewhere and so
     * the fail-closed behaviour above can be watched happening.
     */
    @Parameter(property = "airness.registry", defaultValue = DependencyFreshnessCheck.CENTRAL)
    private String registry;

    @Override
    protected List<Findings> findings() {
        DependencyFreshnessCheck check = new DependencyFreshnessCheck(this.dependencies(), this.registry);
        this.getLog().info(
            "Dependency freshness asked " + this.registry + " about " + check.scanned() + " dependency(ies)"
        );
        return check.findings();
    }

    private List<DeclaredDependency> dependencies() {
        Map<String, Dependency> effective = this.project().getDependencies().stream().collect(
            Collectors.toMap(DependencyFreshnessMojo::key, Function.identity(), (first, second) -> first)
        );
        return this.project().getOriginalModel().getDependencies().stream()
            .map(declared -> effective.getOrDefault(key(declared), declared))
            .filter(dependency -> dependency.getVersion() != null)
            .map(dependency -> new DeclaredDependency(
                dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion()
            ))
            .toList();
    }

    private static String key(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getType()
            + ":" + dependency.getClassifier();
    }
}
