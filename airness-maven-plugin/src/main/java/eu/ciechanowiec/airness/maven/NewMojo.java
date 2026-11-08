package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

/**
 * Makes a new application from an Airness starter, ready for its first verification.
 *
 * <p>It runs outside any project. It writes the starter into a new directory under the names it is given,
 * starts a Git repository there, runs assets-init, so the managed files come from this same harness, writes the
 * first lockfile against the installed harness package, and commits the result as the repository's first commit.
 * Its own logs stay under the application's ignored target directory.
 *
 * <p>Outside a project a parameter can come from the command line alone, so each is read there:
 * {@code -Dairness.groupId} and {@code -Dairness.artifactId}, and optionally {@code -Dairness.package},
 * {@code -Dairness.name}, {@code -Dairness.directory} and {@code -Dairness.starter}, which is
 * {@code spring-boot} unless it is set.
 */
@Mojo(name = "new", aggregator = true, requiresProject = false, threadSafe = true)
public final class NewMojo extends AbstractMojo {

    private static final Duration COMMAND_LIMIT = Duration.ofMinutes(30);
    private static final Duration REGISTRY_LIMIT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(100);
    private static final String HARNESS_PACKAGE = "airness-node";
    private static final String LOGS = "target/airness-new";

    // The id of this plugin as Maven resolved it, group, artifact and version, so assets-init runs from the same
    // harness that wrote the starter.
    @Parameter(defaultValue = "${plugin.id}", readonly = true, required = true)
    private @Nullable String plugin;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private @Nullable MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        StarterRequest request = StarterRequest.of(this.session().getUserProperties());
        List<String> problems = request.problems();
        if (!problems.isEmpty()) {
            throw new MojoFailureException(String.join(System.lineSeparator(), problems));
        }
        Path destination = this.destination(request);
        try {
            this.write(request, destination);
            // The harness reads a project through Git, so the repository exists before assets-init reads it.
            this.run(List.of("git", "init", "--quiet", "--initial-branch=main"), destination, "git-init.log");
            this.run(this.maven("assets-init"), destination, "assets-init.log");
            this.lock(destination);
            this.commit(request, destination);
        } catch (IOException exception) {
            throw new MojoExecutionException(
                "Could not make " + destination + " from the " + this.starter() + " starter: "
                    + exception.getMessage(), exception
            );
        }
        this.getLog().info(
            "Made " + request.name() + " in " + destination + ". Run mvn clean verify there for its first verdict."
        );
    }

    private Path destination(StarterRequest request) throws MojoFailureException {
        Path root = Path.of(this.session().getExecutionRootDirectory());
        Path chosen = StarterRequest.given(this.session().getUserProperties(), "directory")
            .map(root::resolve)
            .orElseGet(() -> root.resolve(request.artifactId()))
            .toAbsolutePath()
            .normalize();
        if (Files.exists(chosen) && !empty(chosen)) {
            throw new MojoFailureException(
                chosen + " already exists and is not empty. Name a new directory with -Dairness.directory."
            );
        }
        return chosen;
    }

    private void write(StarterRequest request, Path destination) throws IOException {
        try (StarterTemplate template = StarterTemplate.open(NewMojo.class.getClassLoader(), this.starter())) {
            StarterCopy.write(template.root(), destination, request, this.version());
        }
        // The starter's imports are ordered for its own package, which the application's package can sort
        // elsewhere, and a longer package can wrap a line otherwise, so the copy is normalized as it is judged.
        SourceFormattingMojo.normalize(
            Stream.of("src/main/java", "src/test/java").map(destination::resolve).filter(Files::isDirectory).toList(),
            this.getLog(), destination.resolve("target").toString()
        );
        this.getLog().info("Wrote the " + this.starter() + " starter into " + destination);
    }

    // The application's pom starts the same registry for every build, but a build installs from a lockfile, and
    // this writes the first one. The registry serves the harness package installed in the local repository.
    private void lock(Path destination) throws IOException {
        Path logs = destination.resolve(LOGS);
        Path tarball = LocalRepository.of(this.session()).head()
            .resolve(Path.of("eu", "ciechanowiec", HARNESS_PACKAGE, this.version()))
            .resolve(HARNESS_PACKAGE + "-" + this.version() + ".tgz");
        if (!Files.isRegularFile(tarball)) {
            throw new IOException(tarball + " is missing. Install the local Airness artifacts first.");
        }
        Path port = Files.createDirectories(logs).resolve("registry.port");
        Process registry = new ProcessBuilder(
            "python3", destination.resolve(".airness/npm-registry.py").toString(), "--port-file", port.toString(),
            "--npmrc", destination.resolve(".npmrc").toString(), "--scope", "@airness", "--fresh", tarball.toString()
        ).redirectErrorStream(true).redirectOutput(logs.resolve("registry.log").toFile()).start();
        try {
            awaitPort(registry, port);
            NodeTools tools = AbstractNodeMojo.install(this.session(), this.getLog());
            Path log = logs.resolve("install.log");
            int exit = tools.pnpm(
                List.of("install", "--no-frozen-lockfile", "--ignore-scripts", "--ignore-pnpmfile"), destination, log,
                Map.of("pnpm_config_cache_dir", logs.resolve("pnpm-cache").toString())
            );
            if (exit != 0) {
                AbstractNodeMojo.replay(log, this.getLog());
                throw new IOException("pnpm install failed with exit " + exit + "; see " + log);
            }
        } finally {
            registry.destroy();
        }
    }

    private void commit(StarterRequest request, Path destination) throws IOException {
        this.run(List.of("git", "add", "--all"), destination, "git-add.log");
        this.run(
            List.of(
                "git", "commit", "--quiet", "--message",
                "feat: start " + request.name() + " from the Airness " + this.starter() + " starter"
            ),
            destination, "git-commit.log"
        );
    }

    private List<String> maven(String goal) {
        LocalRepository repository = LocalRepository.of(this.session());
        Stream<String> tails = repository.tails().isEmpty()
            ? Stream.empty()
            : Stream.of(
                "-D" + LocalRepository.TAIL + "=" + String.join(
                    ",", repository.tails().stream()
                        .map(Path::toString).toList()
                )
            );
        return Stream.of(
            Stream.of(
                Path.of(System.getProperty("maven.home"), "bin", "mvn").toString(), "--batch-mode",
                "--no-transfer-progress", "-Dmaven.repo.local=" + repository.head()
            ),
            tails,
            Stream.of(this.pluginId() + ":" + goal)
        ).flatMap(stream -> stream).toList();
    }

    private void run(List<String> command, Path destination, String logName) throws IOException {
        Path log = destination.resolve(LOGS).resolve(logName);
        Files.createDirectories(Objects.requireNonNull(log.getParent(), "a log lives in a directory"));
        Process process = new ProcessBuilder(command)
            .directory(destination.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
        int exit = finish(process, String.join(" ", command));
        if (exit != 0) {
            AbstractNodeMojo.replay(log, this.getLog());
            throw new IOException(String.join(" ", command) + " failed with exit " + exit + "; see " + log);
        }
    }

    private static int finish(Process process, String command) throws IOException {
        try {
            if (!process.waitFor(COMMAND_LIMIT)) {
                process.destroyForcibly();
                throw new IOException(command + " ran past " + COMMAND_LIMIT);
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command, exception);
        }
    }

    private static void awaitPort(Process registry, Path port) throws IOException {
        Instant deadline = Instant.now().plus(REGISTRY_LIMIT);
        while (!ready(port)) {
            if (!registry.isAlive() || Instant.now().isAfter(deadline)) {
                throw new IOException("The local registry did not start; see " + port.resolveSibling("registry.log"));
            }
            pause();
        }
    }

    private static boolean ready(Path port) throws IOException {
        return Files.exists(port) && Files.size(port) > 0;
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep(POLL);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the local registry", exception);
        }
    }

    private static boolean empty(Path chosen) throws MojoFailureException {
        try (Stream<Path> entries = Files.list(chosen)) {
            return entries.findAny().isEmpty();
        } catch (IOException exception) {
            throw new MojoFailureException(chosen + " exists and cannot be read as a directory", exception);
        }
    }

    private String starter() {
        return StarterRequest.given(this.session().getUserProperties(), "starter").orElse("spring-boot");
    }

    private String pluginId() {
        return Objects.requireNonNull(this.plugin, "Maven did not inject the plugin id");
    }

    private String version() {
        return this.pluginId().substring(this.pluginId().lastIndexOf(':') + 1);
    }

    private MavenSession session() {
        return Objects.requireNonNull(this.session, "Maven did not inject the current session");
    }
}
