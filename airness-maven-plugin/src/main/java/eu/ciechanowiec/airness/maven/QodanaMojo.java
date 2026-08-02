package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Runs Qodana once per reactor from a writable copy of a read-only repository mount. */
@Mojo(name = "qodana", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class QodanaMojo extends DockerCheckMojo {

    private static final String PROFILE = "qodana/profile.xml";

    @Parameter(property = "qodana.image", required = true)
    private String image;

    @Override
    protected List<String> command() throws IOException {
        Path root = Repository.rootFrom(this.project().getBasedir().toPath());
        Path output = root.resolve("target/qodana");
        Files.createDirectories(output);
        Path profile = output.resolve("inspection-profile.xml");
        try (InputStream resource = QodanaMojo.class.getClassLoader().getResourceAsStream(PROFILE)) {
            if (resource == null) {
                throw new IOException("Qodana profile is missing from Airness assets: " + PROFILE);
            }
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
                + "bundle=\"$(readlink -f /etc/ssl/certs/ca-certificates.crt)\"; "
                + "[ -s /opt/hostca.pem ] && cat /opt/hostca.pem >> \"$bundle\"; "
                + "exec /opt/idea/bin/qodana scan --disable-update-checks "
                + "--profile-path /opt/inspection-profile.xml"
        );
    }

    private void trustedRoots(Path destination) throws IOException {
        Files.writeString(destination, "");
        if (!this.available("security")) {
            return;
        }
        this.export(destination, "/System/Library/Keychains/SystemRootCertificates.keychain");
        this.export(destination, "/Library/Keychains/System.keychain");
    }

    private boolean available(String command) throws IOException {
        try {
            return new ProcessBuilder("sh", "-c", "command -v " + command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor() == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while locating " + command, exception);
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
    protected boolean findingsExit(int exit) {
        return exit == 255;
    }
}
