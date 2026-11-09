package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ContainerDefinitions;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Every GitHub Actions workflow passes actionlint under the pinned image, including the shell scripts of
 * its steps.
 *
 * <p>Actionlint reads the staged copy of the committable tree, so the local actions and reusable workflows a
 * workflow calls are validated beside it. It finds the project by a Git directory, so the staged tree gets
 * an empty one, and nothing of the history is read. A project's configuration file is refused by the inventory,
 * because it can ignore any finding, and the staged tree gets the {@link ActionlintConfiguration} instead.
 */
@Mojo(name = "actions", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class ActionsMojo extends AbstractRepositoryMojo {

    private static final String ACTIONLINT = "actionlint";
    private static final String HEADLINE = "Workflows that break the actionlint rules (repair each line it names)";

    @Parameter(property = "actionlint.image", required = true)
    private @Nullable String image;

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        List<Path> workflows = new ContainerDefinitions(root).workflows();
        String pinned = Objects.requireNonNull(this.image, "Maven did not inject actionlint.image");
        this.getLog().info("actionlint: " + workflows.size() + " workflow(s); " + pinned);
        if (workflows.isEmpty()) {
            return List.of(new Findings(HEADLINE, List.of()));
        }
        try {
            ScannerTree tree = ScannerTree.create(ScannerTree.cache(), ACTIONLINT, new ScannerInventory(root));
            return tree.conclude(this.getLog()::info, () -> lint(tree, pinned));
        } catch (IOException exception) {
            throw new UncheckedIOException("actionlint did not complete", exception);
        }
    }

    private static List<Findings> lint(ScannerTree tree, String pinned) throws IOException {
        Files.createDirectory(tree.input().resolve(".git"));
        ActionlintConfiguration.writeInto(tree.input());
        ScannerProcess process = new ScannerProcess(pinned, tree);
        process.validate(ACTIONLINT);
        int exit = AbstractDockerCheckMojo.executeScanner(
            process, ACTIONLINT, List.of("-no-color", "-format", ActionlintReport.FORMAT)
        );
        Path report = tree.directory().resolve("report.json");
        List<String> findings = ActionlintReport.read(report);
        ScannerJson.exit(exit, !findings.isEmpty(), report);
        return List.of(new Findings(HEADLINE, findings));
    }
}
