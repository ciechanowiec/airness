package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ConfigurationProperty;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.SpringConfigurationCheck;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * The runtime settings a Spring Boot application ships with, and the ones it ships without.
 *
 * <p>A module carrying no configuration file has nothing for this to read, so the goal passes over it
 * rather than refusing an empty scope. That is the opposite of what the source goals do, and it is right
 * here for a reason they do not share: a Spring Boot project legitimately holds modules that configure
 * nothing, while a module with a source root and no Java in it is a mistyped path.
 *
 * <p>This is the one goal that asks for its dependencies to be resolved. It judges a written key against
 * the metadata the classpath publishes about itself, and an unresolved classpath publishes nothing,
 * which would leave every key unjudged and the goal green. The declaration is what makes the goal
 * answerable when it is invoked on its own rather than after a phase that resolved for its own reasons.
 */
@Mojo(
    name = "spring-configuration",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    threadSafe = true
)
public final class SpringConfigurationMojo extends AbstractGovernanceMojo {

    @Override
    boolean applies() {
        return !this.resourceRoots().isEmpty();
    }

    @Override
    List<Findings> findings() {
        List<ConfigurationProperty> published = new ConfigurationMetadata(this.classpath()).published();
        SpringConfigurationCheck check = new SpringConfigurationCheck(
            this.repositoryRoot(), this.resourceRoots(), this.moduleProductionSourceRoots(), published
        );
        this.getLog().info(
            "Spring configuration read %d file(s) against %d declared setting(s)"
                .formatted(check.scanned(), published.size())
        );
        return check.findings();
    }

    private List<Path> resourceRoots() {
        return this.project().getResources().stream()
            .map(Resource::getDirectory)
            .map(Path::of)
            .distinct()
            .toList();
    }

    /*
     * The output directory of the module is read beside its dependencies, because a project that
     * declares its own settings has the processor write their metadata there, and a key of its own is
     * then declared rather than unaccounted for.
     */
    private List<Path> classpath() {
        return Stream.concat(
            this.project().getArtifacts().stream()
                .map(Artifact::getFile)
                .filter(Objects::nonNull)
                .map(File::toPath),
            Stream.of(Path.of(this.project().getBuild().getOutputDirectory()))
        ).filter(Files::exists).toList();
    }
}
