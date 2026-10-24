package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One node gate as a binding names it: the gate's id, and the command that runs it inside a node project.
 *
 * <p>The command runs through the harness's pnpm as {@code pnpm exec <command> --result <file>}, so the gate
 * comes from the project's own installed packages and runs on the harness's Node. The id names the result
 * file, which is why it is held to a plain lowercase name.
 *
 * @param id      the gate id, which the result file must repeat
 * @param command the command words, such as {@code airness-node gate eslint}
 */
record NodeGate(String id, List<String> command) {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Copies the command, so it cannot change after the binding was read.
     *
     * @param id      the gate id
     * @param command the command words
     */
    NodeGate {
        command = List.copyOf(command);
    }

    /**
     * Reads a binding's configuration.
     *
     * @param id      the configured gate id
     * @param command the configured command line, split on whitespace
     * @return the gate
     */
    static NodeGate of(String id, String command) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("A node gate id is lowercase letters, digits and hyphens, not " + id);
        }
        List<String> words = Arrays.stream(WHITESPACE.split(command.strip())).filter(word -> !word.isEmpty()).toList();
        if (words.isEmpty()) {
            throw new IllegalArgumentException("The node gate " + id + " names no command to run");
        }
        return new NodeGate(id, words);
    }

    /**
     * The result file this gate writes for a project.
     *
     * @param results the project's result directory
     * @return the result file
     */
    Path result(Path results) {
        return results.resolve(this.id + ".json");
    }

    /**
     * Where this gate's output goes for a project.
     *
     * @param logs the project's log directory
     * @return the log file
     */
    Path log(Path logs) {
        return logs.resolve("gate-" + this.id + ".log");
    }

    /**
     * The pnpm arguments that run this gate.
     *
     * @param result the result file the gate is told to write
     * @return the arguments, starting with {@code exec}
     */
    List<String> arguments(Path result) {
        return Stream.of(Stream.of("exec"), this.command.stream(), Stream.of("--result", result.toString()))
            .flatMap(words -> words)
            .toList();
    }
}
