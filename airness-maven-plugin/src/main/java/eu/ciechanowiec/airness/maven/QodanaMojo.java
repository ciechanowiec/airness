package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Runs Qodana once per reactor from a writable copy of a read-only repository mount.
 */
@Mojo(name = "qodana", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public final class QodanaMojo extends AbstractDockerCheckMojo {

    private static final String PROFILE = "qodana/profile.xml";
    private static final int FINDINGS = 255;

    @Parameter(property = "qodana.image", required = true)
    private String image;

    @Override
    List<String> command() throws IOException {
        Path root = Repository.rootFrom(this.project().getBasedir().toPath());
        Path output = root.resolve("target/qodana");
        Files.createDirectories(output);
        Path profile = output.resolve("inspection-profile.xml");
        try (InputStream resource = profile()) {
            Files.copy(resource, profile, StandardCopyOption.REPLACE_EXISTING);
        }
        Path roots = output.resolve("trusted-roots.pem");
        this.trustedRoots(roots);
        return List.of(
            "docker", "run", "--rm",
            "-v", root + ":/opt/project:ro",
            "-v", output + ":/data/results",
            "-v", profile + ":/opt/inspection-profile.xml:ro",
            "-v", roots + ":/opt/hostca.pem:ro",
            "--entrypoint", "/bin/sh", this.image, "-c",
            "set -eu; "
                + "mkdir -p /data/project; "
                + "cp -a /opt/project/. /data/project/; "
                + "test \"$(git -C /data/project rev-parse --show-toplevel)\" = /data/project; "
                + "git -C /data/project clean -dXff; "
                + "bundle=\"$(readlink -f /etc/ssl/certs/ca-certificates.crt)\"; "
                + "[ -s /opt/hostca.pem ] && cat /opt/hostca.pem >> \"$bundle\"; "
                + "exec /opt/idea/bin/qodana scan --disable-update-checks "
                + "--profile-path /opt/inspection-profile.xml"
        );
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
    boolean findingsExit(int exit) {
        return exit == FINDINGS;
    }
}
