package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.jspecify.annotations.Nullable;

/**
 * The node projects one module declares in {@code airness.node.projects}.
 *
 * <p>Each entry is a directory relative to the module, and its key is that path normalized, with {@code .}
 * for the module itself. The key also names where the project's gate results go: the module itself under
 * {@code target/airness/node/module/}, any other project under {@code target/airness/node/projects/<key>/},
 * and the output of every command run there in the same place beneath {@code target/airness/node-logs/},
 * so no two projects share a result directory and no log shares a name with a result.
 *
 * @param projects the declared projects, in declaration order and without repeats
 */
record NodeProjects(List<NodeProject> projects) {

    private static final String PLUGIN_GROUP = "eu.ciechanowiec";
    private static final String PLUGIN = "airness-maven-plugin";
    private static final String NODE_RUN = "node-run";
    private static final String PROPERTY = "airness.node.projects";

    /**
     * Copies the list, so it cannot change after the declaration was read.
     *
     * @param projects the declared projects
     */
    NodeProjects {
        projects = List.copyOf(projects);
    }

    /**
     * Reads a module's declaration.
     *
     * @param basedir  the module directory
     * @param declared the raw {@code airness.node.projects} value, absent when the module declares none
     * @return the declared projects
     */
    static NodeProjects of(Path basedir, @Nullable String declared) {
        Path module = basedir.toAbsolutePath().normalize();
        return new NodeProjects(
            Sentinel.optional(declared).stream()
                .map(entry -> NodeProject.of(module, entry))
                .distinct()
                .toList()
        );
    }

    /**
     * Whether the module declares no node project, which leaves every node goal but node-projects idle.
     *
     * @return whether nothing is declared
     */
    boolean none() {
        return this.projects.isEmpty();
    }

    /**
     * The declared project an entry names, written however the declaration wrote it.
     *
     * @param basedir the module directory
     * @param entry   a directory relative to the module
     * @return the declared project, or nothing when the module does not declare it
     */
    Optional<NodeProject> find(Path basedir, String entry) {
        NodeProject wanted = NodeProject.of(basedir.toAbsolutePath().normalize(), entry.strip());
        return this.projects.stream().filter(wanted::equals).findFirst();
    }

    /**
     * The declared project node-run may run a script in, refusing what it may not run.
     *
     * <p>Only a declared project, so a frontend that gets built is always checked too, and only a script its
     * {@code package.json} declares, so the command stays where the gates and the agents read it.
     *
     * @param basedir the module directory
     * @param entry   the project directory as the execution names it
     * @param script  the script name
     * @return the declared project
     * @throws IllegalArgumentException when the directory is not declared, holds no {@code package.json}, or
     *                                  declares no such script
     */
    NodeProject runnable(Path basedir, String entry, String script) {
        NodeProject target = this.find(basedir, entry).orElseThrow(
            () -> new IllegalArgumentException(
                entry + " is not listed in " + PROPERTY + ", and node-run builds only a declared node project, "
                    + "so every frontend it builds is checked too"
            )
        );
        if (!target.hasManifest()) {
            throw new IllegalArgumentException(target.key() + " has no package.json; fix " + PROPERTY);
        }
        if (!target.declaresScript(script)) {
            throw new IllegalArgumentException(
                "The package.json of " + target.key() + " declares no script " + script + " for node-run to run"
            );
        }
        return target;
    }

    /**
     * Whether a module binds airness:node-run, which is what keeps Node installed under {@code -DskipTests}:
     * that goal builds the frontend into the artifact, so skipping its prerequisites would ship it without one.
     *
     * @param plugins the module's build plugins
     * @return whether any execution of the Airness plugin runs node-run
     */
    static boolean buildsWithNodeRun(Collection<Plugin> plugins) {
        return plugins.stream()
            .filter(plugin -> PLUGIN_GROUP.equals(plugin.getGroupId()) && PLUGIN.equals(plugin.getArtifactId()))
            .flatMap(plugin -> plugin.getExecutions().stream())
            .map(PluginExecution::getGoals)
            .anyMatch(goals -> goals.contains(NODE_RUN));
    }

    /**
     * One declared node project.
     *
     * @param key       the directory relative to the module, normalized, or {@code .} for the module itself
     * @param directory the absolute directory
     */
    record NodeProject(String key, Path directory) {

        private static final String MODULE = ".";
        private static final String PARENT = "..";
        private static final String MANIFEST = "package.json";

        static NodeProject of(Path module, String entry) {
            Path directory = module.resolve(entry).normalize();
            String relative = module.relativize(directory).toString().replace('\\', '/');
            return new NodeProject(relative.isEmpty() ? MODULE : relative, directory);
        }

        /**
         * Whether the declaration points outside the module, which no module-level walk can stand behind.
         *
         * @return whether the directory lies outside the module
         */
        boolean outside() {
            return PARENT.equals(this.key) || this.key.startsWith(PARENT + '/');
        }

        /**
         * Whether the directory holds a {@code package.json}.
         *
         * @return whether it is a node project at all
         */
        boolean hasManifest() {
            return Files.isRegularFile(this.directory.resolve(MANIFEST));
        }

        /**
         * Whether the directory holds its own pnpm lockfile, which is what node-dependencies installs from.
         *
         * @return whether a lockfile is present
         */
        boolean hasLockfile() {
            return Files.isRegularFile(this.directory.resolve("pnpm-lock.yaml"));
        }

        /**
         * Whether the project's {@code package.json} declares a script, which is all node-run will run.
         *
         * @param script the script name
         * @return whether the manifest names it among its scripts
         */
        boolean declaresScript(String script) {
            try {
                return new ObjectMapper().readTree(this.directory.resolve(MANIFEST).toFile())
                    .path("scripts")
                    .path(script)
                    .isTextual();
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not read the package.json of " + this.key, exception);
            }
        }

        /**
         * Where this project's gate results and evidence go.
         *
         * @param buildDirectory the module's build directory
         * @return the result directory beneath {@code airness/node} in the build directory
         */
        Path results(Path buildDirectory) {
            return this.placed(buildDirectory.resolve("airness").resolve("node"));
        }

        /**
         * Where the output of the commands run in this project goes.
         *
         * @param buildDirectory the module's build directory
         * @return the log directory beneath {@code airness/node-logs} in the build directory
         */
        Path logs(Path buildDirectory) {
            return this.placed(buildDirectory.resolve("airness").resolve("node-logs"));
        }

        private Path placed(Path base) {
            return MODULE.equals(this.key) ? base.resolve("module") : base.resolve("projects").resolve(this.key);
        }
    }
}
