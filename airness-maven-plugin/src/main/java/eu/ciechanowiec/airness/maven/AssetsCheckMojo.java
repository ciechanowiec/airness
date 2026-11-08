package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.RootLicenseCheck;
import eu.ciechanowiec.airness.governance.SecretScanConfiguration;
import eu.ciechanowiec.airness.governance.ToolConfigurationFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The files the harness owns are where their policy says, with the bytes it ships.
 *
 * <p>Which files apply follows the layers of the whole repository, read from the model of every
 * project file in it, so a build started inside one member checks what a build started at the root checks.
 *
 * <p>The tool configuration a pinned tool would read from the tree on its own is refused here too, for the
 * reason the secret scan configuration is read here: the scanners run under Extended verification, and a
 * file that switches one off should fail the build a change is checked with.
 *
 * <p>The parent binds this read-only goal to {@code validate}. A disagreement therefore fails an
 * ordinary build without repairing the file, while {@code airness:assets-sync} remains the explicit
 * repair command.
 */
@Mojo(name = "assets-check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public final class AssetsCheckMojo extends AbstractRepositoryMojo {

    /**
     * Repository-relative paths this project has taken over, comma-separated.
     *
     * <p>Say why beside each one. Nothing checks that you did, which is worth stating rather than
     * leaving to be assumed, and an opt-out whose reason nobody wrote down is one nobody can retire.
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
    public AssetsCheckMojo(ProjectBuilder builder) {
        this.builder = builder;
    }

    @Override
    List<Findings> findings() {
        List<String> exempt = Sentinel.optional(this.unmanaged);
        exempt.forEach(path -> this.getLog().info("This project owns " + path + " rather than the harness"));
        Path root = this.repositoryRoot();
        List<Findings> assets = new AssetCheck(
            root,
            TreeCatalogue.of(this.project(), root),
            exempt,
            RepositorySites.read(this.builder, this.session().getProjectBuildingRequest(), root)
        ).findings();
        List<Findings> licenses = new RootLicenseCheck(root).findings();
        List<Findings> tools = new ToolConfigurationFiles(root).findings();
        return Stream.of(assets, licenses, secretScan(root), tools).flatMap(List::stream).toList();
    }

    /**
     * Read here rather than beside the scan itself, because the scan runs only under Extended
     * verification while this goal runs on every build. A configuration that switches the scan off
     * should fail the command that a change is checked with, not the one it is released with.
     *
     * <p>Deleting the file is the plainest way to switch the scan off, so its absence is a finding
     * rather than a clean answer. Nothing else reports it on this path: the file is seeded rather than
     * pinned, so {@link AssetCheck} does not require it, and the goal that does refuse it runs only
     * under the profile this method stands in for.
     *
     * @param root the working tree root
     * @return the configuration verdicts, or the one finding that says there is no configuration
     */
    private static List<Findings> secretScan(Path root) {
        Path configuration = root.resolve(".gitleaks.toml");
        if (!Files.isRegularFile(configuration)) {
            return List.of(
                new Findings(
                    "Missing secret scan configuration",
                    List.of(configuration + " does not exist, so the secret scan has nothing to run")
                )
            );
        }
        return new SecretScanConfiguration(read(configuration)).findings();
    }

    /**
     * Reads a file that was a regular file a moment ago, so a failure here is a fault rather than an
     * answer and carries the name of what could not be read.
     *
     * @param file the file to read
     * @return its text
     */
    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
