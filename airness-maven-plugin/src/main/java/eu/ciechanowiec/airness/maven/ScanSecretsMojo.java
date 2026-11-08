package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Scans the full Git history with gitleaks in a read-only repository mount.
 */
@Mojo(name = "scan-secrets", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class ScanSecretsMojo extends AbstractDockerCheckMojo {

    private static final String CONFIGURATION = ".gitleaks.toml";
    private static final String IGNORE = ".gitleaksignore";

    @Parameter(property = "gitleaks.image", required = true)
    private @Nullable String image;

    @Override
    List<String> command() throws IOException {
        Path root = this.repositoryRoot();
        Path config = root.resolve(CONFIGURATION);
        if (!Files.isRegularFile(config)) {
            throw new IOException("Secret scan configuration is missing: " + config);
        }
        refuseIgnoreFile(root);
        return dockerCommand(HistoryMount.of(root), this.image());
    }

    /**
     * Refuses the file gitleaks reads from the scanned tree whatever its flags say, which drops every finding it
     * lists. Assets-check refuses it on every build, and this repeats the refusal for a scan run on its own.
     *
     * @param root the working tree root
     * @throws IOException when the file is present
     */
    static void refuseIgnoreFile(Path root) throws IOException {
        Path ignore = root.resolve(IGNORE);
        if (Files.exists(ignore, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                "Remove " + ignore + "; gitleaks drops every finding it lists, so an exception belongs in "
                    + CONFIGURATION + " instead"
            );
        }
    }

    static List<String> dockerCommand(HistoryMount mount, String image) {
        // Fixture exceptions belong in the governed configuration, never in an inline allow comment.
        return Stream.of(
            Stream.of("docker", "run", "--rm"), mount.volumes().stream(),
            Stream.of(
                image, "git", mount.path(), "--no-banner", "--redact", "--ignore-gitleaks-allow",
                "--config", mount.inside(CONFIGURATION)
            )
        ).flatMap(Function.identity()).toList();
    }

    /**
     * The main clone's directories a linked worktree's history lives in are read like the repository is, so
     * a folder Docker does not share fails with its path. Then Git in the same image reads the history over
     * the same mounts, because gitleaks reports a history it could not read as a clean one: Git fails inside
     * it, it scans no commit, and it exits as it does when it found nothing.
     */
    @Override
    List<MountProbe> probes() throws MojoExecutionException {
        Path root = this.repositoryRoot();
        HistoryMount mount = this.mount();
        return Stream.concat(
            mount.linked().stream().map(directory -> MountProbe.input(directory, this.image())),
            Stream.of(
                new MountProbe(
                    MountProbe.historyCommand(mount, this.image(), Repository.hasCommits(root)), root,
                    MountProbe.unreadableHistory(root)
                )
            )
        ).toList();
    }

    private HistoryMount mount() throws MojoExecutionException {
        try {
            return HistoryMount.of(this.repositoryRoot());
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not resolve the Git directories of the repository", exception);
        }
    }

    @Override
    boolean findingsExit(int exit) {
        return exit == 1;
    }

    @Override
    String image() {
        return Objects.requireNonNull(this.image, "Maven did not inject gitleaks.image");
    }
}
