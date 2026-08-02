package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Scans the full Git history with gitleaks in a read-only repository mount. */
@Mojo(name = "scan-secrets", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class SecretScanMojo extends DockerCheckMojo {

    @Parameter(property = "gitleaks.image", required = true)
    private String image;

    @Override
    protected List<String> command() throws IOException {
        Path root = Repository.rootFrom(this.project().getBasedir().toPath());
        Path config = root.resolve(".gitleaks.toml");
        if (!Files.isRegularFile(config)) {
            throw new IOException("Secret scan configuration is missing: " + config);
        }
        return List.of(
            "docker", "run", "--rm", "-v", root + ":/repo:ro", this.image,
            "git", "/repo", "--no-banner", "--redact", "--config", "/repo/.gitleaks.toml"
        );
    }

    @Override
    protected boolean findingsExit(int exit) {
        return exit == 1;
    }
}
