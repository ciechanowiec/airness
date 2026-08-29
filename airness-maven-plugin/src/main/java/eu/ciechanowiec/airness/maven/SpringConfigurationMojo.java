package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringConfigurationCheck;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The runtime settings a Spring Boot application ships with, and the ones it ships without.
 *
 * <p>A module carrying no configuration file has nothing for this to read, so the goal passes over it
 * rather than refusing an empty scope. That is the opposite of what the source goals do, and it is right
 * here for a reason they do not share: a Spring Boot project legitimately holds modules that configure
 * nothing, while a module with a source root and no Java in it is a mistyped path.
 */
@Mojo(name = "spring-configuration", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class SpringConfigurationMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.resourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        SpringConfigurationCheck check = new SpringConfigurationCheck(
            this.repositoryRoot(), this.resourceRoots()
        );
        this.getLog().info("Spring configuration read " + check.scanned() + " file(s)");
        return check.findings();
    }

    private List<Path> resourceRoots() {
        return this.project().getResources().stream()
            .map(Resource::getDirectory)
            .map(Path::of)
            .distinct()
            .toList();
    }
}
