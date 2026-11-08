package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Runs Qodana once per reactor from a writable copy of a read-only repository mount.
 *
 * <p>Git selects the present committable working files before copying, including tracked files that
 * match ignore patterns and ordinary untracked work. Ignored output is never traversed by the copy.
 *
 * <p>A worktree made by {@code git worktree add} keeps its history in the main clone, so the container
 * mounts that clone's Git directories beside it and the copy becomes a standalone repository at the
 * worktree's commit, which the scan reads as it reads a clone.
 */
@Mojo(name = "qodana", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class QodanaMojo extends AbstractDockerCheckMojo {

    private static final String PROFILE = "qodana/profile.xml";
    private static final String OUTPUT = "target/qodana";
    private static final String REPORT = "qodana.sarif.json";
    private static final int FINDINGS = 255;
    private static final String REPOSITORY = "/opt/maven-repository";

    @Parameter(property = "qodana.image", required = true)
    private @Nullable String image;

    @Override
    List<String> command() throws IOException {
        Path root = this.repositoryRoot();
        Path output = root.resolve(OUTPUT);
        Files.createDirectories(output);
        QodanaSnapshot.prepare(root, output);
        Path profile = output.resolve("inspection-profile.xml");
        try (InputStream resource = profile()) {
            Files.copy(resource, profile, StandardCopyOption.REPLACE_EXISTING);
        }
        Path roots = output.resolve("trusted-roots.pem");
        this.trustedRoots(roots);
        QodanaPaths.Environment environment = new QodanaPaths.Environment(roots, this.localRepository());
        return dockerCommand(
            new QodanaPaths(root, output, profile, environment, QodanaSnapshot.volumes(root)), this.image()
        );
    }

    static List<String> dockerCommand(QodanaPaths paths, String image) {
        String linked = paths.metadata().isEmpty() ? "" : " " + QodanaSnapshot.OWN + " " + QodanaSnapshot.COMMON;
        List<String> mounts = List.of(
            "-v", paths.root() + ":/opt/project:ro",
            "-v", paths.output() + ":/data/results",
            "-v", paths.profile() + ":/opt/inspection-profile.xml:ro",
            "-v", paths.roots() + ":/opt/hostca.pem:ro"
        );
        List<Path> layers = paths.localRepository().layers();
        return Stream.of(
            Stream.of("docker", "run", "--rm"), mounts.stream(), repositoryMounts(layers), paths.metadata().stream(),
            Stream.of("--entrypoint", "/bin/sh", image, "-c", script(linked, layers.size()))
        ).flatMap(Function.identity()).toList();
    }

    private static Stream<String> repositoryMounts(List<Path> layers) {
        return IntStream.range(0, layers.size())
            .boxed()
            .flatMap(layer -> Stream.of("-v", layers.get(layer) + ":" + mountPoint(layer) + ":ro"));
    }

    // The head keeps the mount point it always had, and each tail is mounted beside it under its position.
    private static String mountPoint(int layer) {
        return layer == 0 ? REPOSITORY : REPOSITORY + "-tail-" + layer;
    }

    // The scan reads one repository, so the chain is merged into it. The last tail is linked first and every
    // repository before it replaces what it also holds, which leaves each file from the first repository of
    // the chain that holds it, the one Maven itself resolves.
    private static String script(String linked, int layers) {
        String merged = IntStream.iterate(layers - 1, layer -> layer >= 0, layer -> layer - 1)
            .mapToObj(layer -> "cp -as --remove-destination " + mountPoint(layer) + "/. /data/cache/.m2/; ")
            .collect(Collectors.joining());
        return "set -- /opt/project /data/project /data/results/source-files" + linked + "; "
            + QodanaSnapshot.SCRIPT
            + "mkdir -p /data/cache/.m2; "
            + merged
            + "find /data/cache/.m2 -type l \\( "
            + "-name _remote.repositories -o -name resolver-status.properties "
            + "-o -name '*.lastUpdated' -o -name 'maven-metadata*.xml' "
            + "\\) -delete; "
            + "bundle=\"$(readlink -f /etc/ssl/certs/ca-certificates.crt)\"; "
            + "[ -s /opt/hostca.pem ] && cat /opt/hostca.pem >> \"$bundle\"; "
            + "exec /opt/idea/bin/qodana scan --disable-update-checks "
            + "--profile-path /opt/inspection-profile.xml";
    }

    /**
     * The scan resolves every library from these mounts alone. Arriving empty, it leaves the IDE without the
     * project's dependencies and on a fallback JDK and language level, so valid code is reported by the
     * thousand instead of the scan failing.
     */
    @Override
    List<MountProbe> probes() throws MojoExecutionException {
        try {
            return Stream.concat(
                this.localRepository().layers().stream(), QodanaSnapshot.linked(this.repositoryRoot()).stream()
            ).map(input -> MountProbe.input(input, this.image())).toList();
        } catch (IOException exception) {
            throw new MojoExecutionException(
                "Could not resolve the local Maven repository or the Git directories of the repository", exception
            );
        }
    }

    private LocalRepository localRepository() throws IOException {
        LocalRepository declared = LocalRepository.of(this.session());
        List<Path> tails = new ArrayList<>();
        for (Path tail : declared.tails()) {
            tails.add(tail.toRealPath());
        }
        return new LocalRepository(declared.head().toRealPath(), tails);
    }

    private void trustedRoots(Path destination) throws IOException {
        Files.writeString(destination, "");
        if (!this.securityAvailable()) {
            return;
        }
        this.export(destination, "/System/Library/Keychains/SystemRootCertificates.keychain");
        this.export(destination, "/Library/Keychains/System.keychain");
    }

    private static InputStream profile() throws IOException {
        return Optional.ofNullable(QodanaMojo.class.getClassLoader().getResourceAsStream(PROFILE))
            .orElseThrow(() -> new IOException("Qodana profile is missing from Airness assets: " + PROFILE));
    }

    private boolean securityAvailable() throws IOException {
        try {
            return new ProcessBuilder("sh", "-c", "command -v security")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while locating security", exception);
        }
    }

    private void export(Path destination, String keychain) throws IOException {
        try {
            new ProcessBuilder("security", "find-certificate", "-a", "-p", keychain)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(destination.toFile()))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while exporting trusted roots", exception);
        }
    }

    @Override
    List<String> findings() {
        Path root = this.repositoryRoot();
        return new SarifFindings(root.resolve(OUTPUT).resolve(REPORT)).listed();
    }

    @Override
    boolean findingsExit(int exit) {
        return exit == FINDINGS;
    }

    @Override
    String image() {
        return Objects.requireNonNull(this.image, "Maven did not inject qodana.image");
    }
}
