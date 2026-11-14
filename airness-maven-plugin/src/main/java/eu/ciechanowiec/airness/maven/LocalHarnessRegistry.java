package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The loopback registry that serves a SNAPSHOT harness's npm packages to a new application while its first
 * lockfile is written, because a SNAPSHOT package is never published.
 *
 * <p>It serves the tarball of each harness package the application installs, from the local Maven repository,
 * through the managed {@code .airness/npm-registry.py}, and writes the scope line that routes the packages to it
 * into the application's ignored {@code .npmrc}. The registry stops when it is closed.
 */
final class LocalHarnessRegistry implements AutoCloseable {

    private static final Duration LIMIT = Duration.ofSeconds(30);
    private static final Duration POLL = Duration.ofMillis(100);

    private final Process registry;

    private LocalHarnessRegistry(Process registry) {
        this.registry = registry;
    }

    /**
     * The tarball of each harness package an application installs, where a SNAPSHOT harness installs it in the
     * local Maven repository.
     *
     * @param repository the local Maven repository
     * @param installed  the packages the application's {@code package.json} installs
     * @param version    the harness version
     * @return the tarballs, ordered by package name
     */
    static List<Path> tarballs(Path repository, Set<String> installed, String version) {
        return installed.stream()
            .sorted()
            .map(HarnessVersions::artifact)
            .flatMap(Optional::stream)
            .map(artifact -> tarball(repository, artifact, version))
            .toList();
    }

    private static Path tarball(Path repository, String artifact, String version) {
        Path directory = repository.resolve(Path.of("eu", "ciechanowiec", artifact, version));
        return directory.resolve(artifact + "-" + version + ".tgz");
    }

    /**
     * Starts the registry for an application and waits until it serves.
     *
     * @param application the application's directory, which holds the managed registry script
     * @param tarballs    the tarballs to serve
     * @param logs        the directory the registry's port file and log are written to
     * @return the running registry
     * @throws IOException when a tarball is missing or the registry does not start
     */
    static LocalHarnessRegistry start(Path application, List<Path> tarballs, Path logs) throws IOException {
        return start("python3", application, tarballs, logs);
    }

    /**
     * Starts the registry with a given interpreter for its script, which a test replaces.
     *
     * @param interpreter the program that runs the registry script
     * @param application the application's directory, which holds the managed registry script
     * @param tarballs    the tarballs to serve
     * @param logs        the directory the registry's port file and log are written to
     * @return the running registry
     * @throws IOException when a tarball is missing or the registry does not start
     */
    static LocalHarnessRegistry start(
        String interpreter, Path application, List<Path> tarballs, Path logs
    ) throws IOException {
        for (Path tarball : tarballs) {
            if (!Files.isRegularFile(tarball)) {
                throw new IOException(tarball + " is missing. Install the local Airness artifacts first.");
            }
        }
        Path port = Files.createDirectories(logs).resolve("registry.port");
        Process process = new ProcessBuilder(
            Stream.concat(
                Stream.of(
                    interpreter, application.resolve(".airness/npm-registry.py").toString(), "--port-file",
                    port.toString(), "--npmrc", application.resolve(".npmrc").toString(), "--scope", "@airness",
                    "--fresh"
                ),
                tarballs.stream().map(Path::toString)
            ).toList()
        ).redirectErrorStream(true).redirectOutput(logs.resolve("registry.log").toFile()).start();
        LocalHarnessRegistry started = new LocalHarnessRegistry(process);
        try {
            awaitPort(process, port);
        } catch (IOException exception) {
            started.close();
            throw exception;
        }
        return started;
    }

    @Override
    public void close() {
        this.registry.destroy();
    }

    private static void awaitPort(Process registry, Path port) throws IOException {
        Instant deadline = Instant.now().plus(LIMIT);
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
}
