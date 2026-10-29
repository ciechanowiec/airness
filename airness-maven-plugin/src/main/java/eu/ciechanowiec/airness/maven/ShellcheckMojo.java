package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import eu.ciechanowiec.airness.governance.ShellSources;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Checks supported repository shell scripts with the pinned fleet policy.
 */
@Mojo(name = "shellcheck", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class ShellcheckMojo extends AbstractRepositoryMojo {

    @Parameter(property = "shellcheck.image", required = true)
    private @Nullable String image;

    @Override
    List<Findings> findings() {
        ScannerInventory inventory = new ScannerInventory(this.repositoryRoot());
        try {
            ScannerTree tree = ScannerTree.create(ScannerTree.cache(), "shellcheck", inventory);
            return tree.conclude(this.getLog()::info, () -> this.verdicts(tree, inventory));
        } catch (IOException exception) {
            throw new UncheckedIOException("ShellCheck did not complete", exception);
        }
    }

    private List<Findings> verdicts(ScannerTree tree, ScannerInventory inventory) throws IOException {
        ScannerProcess process = new ScannerProcess(Objects.requireNonNull(this.image), tree);
        process.validate("shellcheck");
        this.getLog().info("ShellCheck: " + inventory.scripts().size() + " script(s); " + process.image());
        return List.of(new Findings("ShellCheck", this.scan(process, inventory)));
    }

    private List<String> scan(ScannerProcess process, ScannerInventory inventory) throws IOException {
        List<Path> scripts = ShellSources.entryPoints(inventory.root(), inventory.scripts());
        if (scripts.isEmpty()) {
            return List.of();
        }
        List<String> arguments = new ArrayList<>(ShellcheckScan.options());
        scripts.stream().map(inventory.root()::relativize).map(path -> "/input/" + path).forEach(arguments::add);
        int exit = AbstractDockerCheckMojo.executeScanner(process, "shellcheck", arguments);
        Path report = process.tree().directory().resolve("report.json");
        List<String> findings = ShellcheckScan.read(report);
        ScannerJson.exit(exit, !findings.isEmpty(), report);
        return findings;
    }
}
