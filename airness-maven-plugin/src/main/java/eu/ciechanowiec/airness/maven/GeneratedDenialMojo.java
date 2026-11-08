package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetSite;
import eu.ciechanowiec.airness.governance.AssetSync;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.GeneratedDenial;
import eu.ciechanowiec.airness.governance.GeneratedFiles;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The agent runtime is denied write access to every generated file the stack layers present declare, and to
 * every pinned file the harness places.
 *
 * <p>Which files are generated follows the layers of the whole repository, read the way the managed
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

    /**
     * Repository-relative paths this project has taken over from the harness, comma-separated. A pinned file
     * listed here belongs to the project, so it is not denied to the agent runtime.
     */
    @Parameter(property = "airness.assets.unmanaged")
    private @Nullable String unmanaged;

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
        List<String> generated = denied(
            root, RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root),
            CommaList.entries(this.declared), CommaList.entries(this.unmanaged)
        );
        this.getLog().info(
            "Generated-file denial read " + generated.size() + " generated and pinned file(s) the stack layers and"
                + " project declare"
        );
        return new GeneratedDenial(
            generated,
            ClaudeSettings.permissions(root.resolve(ClaudeSettings.SHARED), "deny"),
            ClaudeSettings.permissions(root.resolve(ClaudeSettings.LOCAL), "allow")
        ).findings();
    }

    /**
     * Every file the agent runtime is denied: the generated files the stack layers and the project declare (in the
     * {@code airness.generated.files} property and in each node project's {@code airness.generatedArtefacts}), and
     * the pinned files the harness places, which it writes from its shipped bytes as a generator writes its
     * output.
     *
     * @param root      the repository root
     * @param sites     the directories of the repository, with the layers present in each
     * @param declared  the generated files the project declares
     * @param unmanaged the managed files the project has taken over
     * @return the repository-relative paths, generated files first
     */
    static List<String> denied(
        Path root, Collection<AssetSite> sites, Collection<String> declared, Collection<String> unmanaged
    ) {
        ClassLoader classes = GeneratedDenialMojo.class.getClassLoader();
        return Stream.concat(
            generated(root, sites, declared).stream(),
            new AssetSync(root, new AssetCatalogue(classes), unmanaged, sites).pinned().stream()
        ).distinct().toList();
    }

    /**
     * Every file a generator owns: those the stack layers declare, those the {@code airness.generated.files}
     * property names and those each node project lists under {@code airness.generatedArtefacts}.
     *
     * @param root     the repository root
     * @param sites    the directories of the repository, with the layers present in each
     * @param declared the generated files the project declares in the property
     * @return repository-relative paths and globs, layer declarations first
     */
    static List<String> generated(Path root, Collection<AssetSite> sites, Collection<String> declared) {
        Stream<String> node = NodeGeneratedArtefacts.paths(root, sites).stream();
        List<String> projectDeclared = Stream.concat(declared.stream(), node).toList();
        return new GeneratedFiles(GeneratedDenialMojo.class.getClassLoader()).paths(sites, projectDeclared);
    }
}
