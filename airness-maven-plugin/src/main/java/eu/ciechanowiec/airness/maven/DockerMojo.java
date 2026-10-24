package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ContainerDefinitions;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
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
 * Every Dockerfile passes hadolint under the pinned image at its default threshold, info, and every compose
 * project loads through {@code docker compose config}.
 *
 * <p>Both read the staged copy of the committable tree, so an untracked {@code .env} beside a compose file
 * cannot make a project load here that fails on a clean checkout. Hadolint reads each Dockerfile on its
 * standard input, which leaves its own configuration files unread. The inventory refuses them and every
 * inline ignore directive anyway, because a suppression the harness does not own is a check turned off.
 */
@Mojo(name = "docker", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public final class DockerMojo extends AbstractRepositoryMojo {

    private static final String HADOLINT = "hadolint";
    private static final List<String> HADOLINT_OPTIONS = List.of(
        "--no-color", "--format", "json", "--failure-threshold", "info", "-"
    );
    // Compose ships as a docker subcommand and as a standalone binary, and which one a machine has is no
    // property of the project. The subcommand is tried first.
    private static final List<List<String>> COMPOSE = List.of(
        List.of("docker", "compose"), List.of("docker-compose")
    );
    private static final String LINT = "Dockerfiles that break the hadolint rules (repair each line it names)";
    private static final String UNLOADABLE = "Compose projects that docker compose cannot load (repair each file)";

    @Parameter(property = "hadolint.image", required = true)
    private @Nullable String image;

    @Override
    List<Findings> findings() {
        Path root = this.repositoryRoot();
        ContainerDefinitions definitions = new ContainerDefinitions(root);
        List<Path> dockerfiles = definitions.dockerfiles();
        List<Path> compose = definitions.composeFiles();
        this.getLog().info(
            "Docker: " + dockerfiles.size() + " Dockerfile(s) and " + compose.size() + " compose project(s); "
                + this.image()
        );
        if (dockerfiles.isEmpty() && compose.isEmpty()) {
            return List.of(new Findings(LINT, List.of()), new Findings(UNLOADABLE, List.of()));
        }
        try {
            Path output = Path.of(this.project().getBuild().getDirectory(), "airness", "docker");
            ScannerTree tree = ScannerTree.create(output, new ScannerInventory(root));
            this.getLog().info("Scanner evidence: " + tree.directory());
            return List.of(
                new Findings(LINT, this.lint(new ScannerProcess(this.image(), tree), root, dockerfiles)),
                new Findings(UNLOADABLE, validate(tree, root, compose))
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("The container definition check did not complete", exception);
        }
    }

    private List<String> lint(ScannerProcess process, Path root, List<Path> dockerfiles) throws IOException {
        process.validate(HADOLINT);
        List<String> findings = new ArrayList<>();
        for (int index = 0; index < dockerfiles.size(); index++) {
            Path relative = root.relativize(dockerfiles.get(index));
            Path report = process.tree().directory().resolve("hadolint-" + index + ".json");
            int exit = ScannerCommand.executeWithInput(
                process.standardInputCommand(HADOLINT, HADOLINT_OPTIONS), process.tree().input().resolve(relative),
                report
            );
            List<String> found = HadolintReport.read(report, relative.toString());
            ScannerJson.exit(exit, !found.isEmpty(), report);
            findings.addAll(found);
        }
        return List.copyOf(findings);
    }

    private static List<String> validate(ScannerTree tree, Path root, List<Path> compose) throws IOException {
        if (compose.isEmpty()) {
            return List.of();
        }
        ComposeLoading loading = ComposeLoading.choose(COMPOSE, tree.input(), tree.directory());
        List<String> findings = new ArrayList<>();
        for (int index = 0; index < compose.size(); index++) {
            Path report = tree.directory().resolve("compose-" + index + ".txt");
            findings.addAll(loading.load(tree.input(), root.relativize(compose.get(index)), report));
        }
        return List.copyOf(findings);
    }

    private String image() {
        return Objects.requireNonNull(this.image, "Maven did not inject hadolint.image");
    }
}
