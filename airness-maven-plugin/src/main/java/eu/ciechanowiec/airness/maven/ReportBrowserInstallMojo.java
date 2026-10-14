package eu.ciechanowiec.airness.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Installs the Chromium revision pinned by this Airness version into Playwright's user cache.
 */
@Mojo(name = "report-browser-install", aggregator = true, requiresProject = false, threadSafe = true)
public final class ReportBrowserInstallMojo extends AbstractMojo {

    private static final int INSTALL_MINUTES = 5;

    @Parameter(defaultValue = "${plugin.artifacts}", readonly = true, required = true)
    private @Nullable List<Artifact> artifacts;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            await(this.start());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Interrupted while installing the report browser", exception);
        } catch (IOException exception) {
            throw new MojoExecutionException("Could not install the pinned report browser", exception);
        }
    }

    private Process start() throws IOException {
        String classpath = Objects.requireNonNull(this.artifacts, "Maven did not inject plugin artifacts").stream()
            .map(Artifact::getFile).map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(java, "-cp", classpath, "com.microsoft.playwright.CLI", "install", "chromium")
            .inheritIO().start();
    }

    private static void await(Process process) throws InterruptedException, MojoExecutionException {
        if (!process.waitFor(Duration.ofMinutes(INSTALL_MINUTES))) {
            process.destroyForcibly();
            throw new MojoExecutionException("Timed out installing the pinned report browser");
        }
        if (process.exitValue() != 0) {
            throw new MojoExecutionException("Report browser installation failed with exit " + process.exitValue());
        }
    }
}
