package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.GeneratedDenial;
import eu.ciechanowiec.airness.governance.GeneratedFiles;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The agent runtime is denied write access to every generated file the stack layers present declare.
 *
 * <p>Which files are generated follows the capabilities of the whole repository, read the way the managed
 * files read them, so a build started inside one member denies what a build started at the root denies.
 * The generic layer declares no generated file of its own, and a stack layer adds its files to the plugin
 * classpath.
 */
@Mojo(name = "generated-denial", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class GeneratedDenialMojo extends AbstractRepositoryMojo {

    /**
     * Repository-relative paths of further files a generator owns, comma-separated. Each is denied to the
     * agent runtime beside the files the stack layers declare. A project can only add to that set.
     */
    @Parameter(property = "airness.generated.files")
    private @Nullable String declared;

    private final ProjectBuilder builder;

    /**
     * Receives the builder that reads the models of every module in the repository.
     *
     * @param builder the Maven project builder
     */
    @Inject
    public GeneratedDenialMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        List<String> generated = new GeneratedFiles(GeneratedDenialMojo.class.getClassLoader()).paths(
            RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root),
            Sentinel.optional(this.declared)
        );
        this.getLog().info(
            "Generated-file denial read " + generated.size() + " generated file(s) the stack layers and project declare"
        );
        return new GeneratedDenial(
            generated,
            ClaudeSettings.permissions(root.resolve(ClaudeSettings.SHARED), "deny"),
            ClaudeSettings.permissions(root.resolve(ClaudeSettings.LOCAL), "allow")
        ).findings();
    }
}
