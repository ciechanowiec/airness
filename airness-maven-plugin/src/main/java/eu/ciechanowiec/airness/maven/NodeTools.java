package eu.ciechanowiec.airness.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The installed Node and pnpm executables, and the one way a build runs a command through them.
 *
 * <p>Every command is pnpm with arguments, started with the harness's Node and pnpm first on {@code PATH}, so
 * a script that calls {@code node} reaches the Node the gates run on rather than one the machine happens to
 * have. Its output goes to a log file rather than to the build log directly, so a process that never closes
 * its output still ends at the timeout, and the caller replays the file.
 *
 * @param node    the Node executable
 * @param pnpm    the pnpm executable
 * @param timeout how long one command may run before it is stopped and the build fails
 */
record NodeTools(Path node, Path pnpm, Duration timeout) {

    private static final Duration TIMEOUT = Duration.ofMinutes(60);
    private static final String PATH = "PATH";
    // Keeps pnpm from switching to the version a user-level configuration names. The project's own settings
    // to that effect are refused by node-projects.
    private static final String PACKAGE_MANAGER_VERSIONS = "npm_config_manage_package_manager_versions";

    /**
     * The executables, with the timeout every build command runs under.
     *
     * @param node the Node executable
     * @param pnpm the pnpm executable
     * @return the tools
     */
    static NodeTools of(Path node, Path pnpm) {
        return new NodeTools(node, pnpm, TIMEOUT);
    }

    /**
     * Runs pnpm with the given arguments in a directory.
     *
     * @param arguments the pnpm arguments, such as {@code install --frozen-lockfile}
     * @param directory the working directory
     * @param log       where the command's output and error streams go together
     * @return the exit code
     * @throws IOException when the command cannot start, outlives the timeout, or is interrupted
     */
    int pnpm(List<String> arguments, Path directory, Path log) throws IOException {
        Files.createDirectories(Objects.requireNonNull(log.getParent(), "a log file lives in a directory"));
        List<String> command = Stream.concat(Stream.of(this.pnpm.toString()), arguments.stream()).toList();
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
        builder.environment().put(PATH, this.path(builder.environment().getOrDefault(PATH, "")));
        builder.environment().put(PACKAGE_MANAGER_VERSIONS, "false");
        Process process = builder.start();
        try {
            if (!process.waitFor(this.timeout)) {
                process.destroyForcibly();
                throw new IOException(
                    "pnpm " + String.join(" ", arguments) + " ran past " + this.timeout + "; see " + log
                );
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running pnpm " + String.join(" ", arguments), exception);
        }
    }

    /**
     * The search path a command runs with: the harness's Node and pnpm, then what the build inherited.
     *
     * @param inherited the inherited search path, possibly empty
     * @return the search path
     */
    String path(String inherited) {
        String tools = this.node.resolveSibling("") + File.pathSeparator + this.pnpm.resolveSibling("");
        return inherited.isEmpty() ? tools : tools + File.pathSeparator + inherited;
    }
}
