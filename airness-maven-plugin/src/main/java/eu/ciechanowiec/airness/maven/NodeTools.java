package eu.ciechanowiec.airness.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    static final String PROGRESS_LOG = "AIRNESS_GATE_LOG";

    private static final Duration TIMEOUT = Duration.ofMinutes(60);
    private static final String PATH = "PATH";
    // Keeps pnpm from switching to the version a user-level configuration names. The project's own settings
    // to that effect are refused by node-projects. pnpm 12 reads its settings from pnpm_config_ variables and
    // still honours this one npm_config_ name, so both are set. The node domain of airness-it proves the pinned
    // pnpm keeps to its own version under them.
    private static final List<String> PACKAGE_MANAGER_VERSIONS = List.of(
        "npm_config_manage_package_manager_versions", "pnpm_config_manage_package_manager_versions"
    );
    private static final List<String> PREFIXES = List.of("npm_config_", "pnpm_config_");
    // Every setting with which pnpm picks a pnpm or a Node of its own, removed from the inherited environment in
    // any case, since pnpm reads a variable whatever its case and which of two spellings wins is unspecified.
    private static final List<String> SELECTING = List.of(
        "manage_package_manager_versions", "use_node_version", "node_version", "execution_env", "pm_on_fail"
    );

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
        return this.pnpm(arguments, directory, log, Map.of());
    }

    /**
     * Runs pnpm with the given arguments in a directory, with further environment variables.
     *
     * @param arguments   the pnpm arguments, such as {@code install --frozen-lockfile}
     * @param directory   the working directory
     * @param log         where the command's output and error streams go together
     * @param environment further variables the command runs with
     * @return the exit code
     * @throws IOException when the command cannot start, outlives the timeout, or is interrupted
     */
    int pnpm(
        List<String> arguments, Path directory, Path log, Map<String, String> environment
    ) throws IOException {
        Files.createDirectories(Objects.requireNonNull(log.getParent(), "a log file lives in a directory"));
        List<String> command = Stream.concat(Stream.of(this.pnpm.toString()), arguments.stream()).toList();
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true);
        if (environment.containsKey(PROGRESS_LOG)) {
            Files.writeString(log, "");
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        } else {
            builder.redirectOutput(log.toFile());
        }
        builder.environment().put(PATH, this.path(builder.environment().getOrDefault(PATH, "")));
        builder.environment().putAll(environment);
        guard(builder.environment());
        Process process = builder.start();
        try {
            if (!process.waitFor(this.timeout)) {
                stop(process);
                throw new IOException(
                    "pnpm " + String.join(" ", arguments) + " ran past " + this.timeout + "; see " + log
                );
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            stop(process);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running pnpm " + String.join(" ", arguments), exception);
        }
    }

    private static void stop(Process process) {
        process.descendants().toList().forEach(ProcessHandle::destroy);
        process.destroyForcibly();
    }

    /**
     * Removes every inherited setting with which pnpm would pick a pnpm or a Node of its own, then keeps pnpm to
     * its own version.
     *
     * @param environment the environment a command runs with, changed in place
     */
    static void guard(Map<String, String> environment) {
        environment.keySet().removeIf(NodeTools::selects);
        PACKAGE_MANAGER_VERSIONS.forEach(name -> environment.put(name, "false"));
    }

    private static boolean selects(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return PREFIXES.stream().anyMatch(
            prefix -> lower.startsWith(prefix) && SELECTING.contains(lower.substring(prefix.length()))
        );
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
