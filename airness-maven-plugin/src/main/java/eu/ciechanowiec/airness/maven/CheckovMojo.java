package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Enforces the portable IaC policy for the whole committable repository.
 */
@Mojo(name = "checkov", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class CheckovMojo extends AbstractRepositoryMojo {

    @Parameter(property = "checkov.image", required = true)
    private @Nullable String image;

    @Override
    List<Findings> findings() {
        ScannerInventory inventory = new ScannerInventory(this.repositoryRoot());
        try {
            Path output = Path.of(this.project().getBuild().getDirectory(), "airness", "checkov");
            ScannerTree tree = ScannerTree.create(output, inventory);
            ScannerProcess process = new ScannerProcess(Objects.requireNonNull(this.image), tree);
            this.getLog().info("Checkov: " + inventory.files().size() + " repository file(s); " + process.image());
            this.getLog().info("Scanner evidence: " + tree.directory());
            return List.of(new Findings("Checkov", this.scan(process, inventory)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Checkov did not complete", exception);
        }
    }

    private List<String> scan(ScannerProcess process, ScannerInventory inventory) throws IOException {
        Path report = process.tree().directory().resolve("report.json");
        int listed = AbstractDockerCheckMojo.executeScanner(
            process, "checkov", List.of("--list", "--skip-download")
        );
        ScannerJson.exit(listed, false, report);
        CheckovPolicy.verify(Files.readString(report));
        Files.move(report, report.resolveSibling("rule-registry.txt"));
        this.verifyPolicy(process);
        List<String> findings = new ArrayList<>();
        for (String framework : CheckovPolicy.frameworks()) {
            List<Path> selected = CheckovInputs.select(framework, inventory.root(), inventory.files());
            if (!selected.isEmpty()) {
                findings.addAll(this.scanFramework(process, inventory, framework, selected));
            }
        }
        return List.copyOf(findings);
    }

    private void verifyPolicy(ScannerProcess process) throws IOException {
        Path output = process.tree().directory();
        ScannerResources.copy("checkov-policy.py", output);
        Path expected = ScannerResources.copy("checkov-policy.tsv", output);
        int exit = AbstractDockerCheckMojo.executeScanner(
            process, "python", List.of(
                "/output/checkov-policy.py", "/output/rule-registry.txt", "/output/regenerated.tsv"
            )
        );
        ScannerJson.exit(exit, false, output);
        Path regenerated = output.resolve("regenerated.tsv");
        if (Files.mismatch(expected, regenerated) != -1) {
            throw new IOException("The packaged Checkov policy differs from its declared generator");
        }
    }

    private List<String> scanFramework(
        ScannerProcess parent, ScannerInventory inventory, String framework, List<Path> selected
    ) throws IOException {
        ScannerTree tree = ScannerTree.empty(parent.tree().directory().resolve(framework));
        Path input = CheckovScan.input(tree);
        for (Path file : selected) {
            ScannerTree.copy(file, input.resolve(inventory.root().relativize(file)));
        }
        ScannerProcess process = new ScannerProcess(parent.image(), tree);
        this.getLog().info("Checkov " + framework + ": " + selected.size() + " selected input(s)");
        int exit = AbstractDockerCheckMojo.executeScanner(process, "checkov", CheckovScan.arguments(framework));
        Path report = tree.directory().resolve("report.json");
        CheckovReport result = CheckovReport.read(report, framework);
        List<Path> required = selected.stream().filter(CheckovInputs::candidate)
            .filter(file -> framework.equals(CheckovInputs.required(inventory.root().relativize(file), text(file))))
            .map(file -> input.resolve(inventory.root().relativize(file))).toList();
        CheckovInputs.verify(input, required, result.frameworks());
        ScannerJson.exit(exit, !result.findings().isEmpty(), report);
        return result.findings();
    }

    private static String text(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read IaC input " + file, exception);
        }
    }
}
