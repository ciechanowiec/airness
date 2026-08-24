package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetCheck;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.RootLicenseCheck;
import eu.ciechanowiec.airness.governance.SecretScanConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The files the harness owns are where their policy says, with the bytes it ships.
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
    private String unmanaged;

    @Override
    List<Findings> findings() {
        List<String> exempt = Sentinel.optional(this.unmanaged);
        exempt.forEach(path -> this.getLog().info("This project owns " + path + " rather than the harness"));
        Path root = this.repositoryRoot();
        List<Findings> assets = new AssetCheck(
            root, new AssetCatalogue(AssetsCheckMojo.class.getClassLoader()), exempt
        ).findings();
        List<Findings> licenses = new RootLicenseCheck(root).findings();
        return Stream.of(assets, licenses, this.secretScan(root)).flatMap(List::stream).toList();
    }

    /**
     * Read here rather than beside the scan itself, because the scan runs only under Extended
     * verification while this goal runs on every build. A configuration that switches the scan off
     * should fail the command that a change is checked with, not the one it is released with.
     */
    @SneakyThrows
    private List<Findings> secretScan(Path root) {
        Path configuration = root.resolve(".gitleaks.toml");
        return Files.isRegularFile(configuration)
            ? new SecretScanConfiguration(Files.readString(configuration)).findings()
            : List.of();
    }
}
