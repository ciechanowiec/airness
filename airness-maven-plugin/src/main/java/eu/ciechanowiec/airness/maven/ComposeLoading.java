package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Loads compose projects with {@code config --quiet} through whichever compose command the machine has.
 *
 * <p>Any non-zero exit is a finding naming the compose file, with each line compose wrote to its error
 * stream, or the exit code when it wrote none. A failure that explains nothing is still a failure.
 *
 * @param command the compose command, such as {@code docker compose}
 */
record ComposeLoading(List<String> command) {

    /**
     * Copies the command, so it cannot change after it was chosen.
     *
     * @param command the compose command
     */
    ComposeLoading {
        command = List.copyOf(command);
    }

    /**
     * The first candidate whose {@code version} subcommand succeeds.
     *
     * @param candidates the compose commands, in preference order
     * @param directory  where to run the probe
     * @param evidence   where to leave the probe's output
     * @return the loading through the first available command
     * @throws IOException when no candidate is available
     */
    static ComposeLoading choose(List<List<String>> candidates, Path directory, Path evidence) throws IOException {
        Path probe = evidence.resolve("compose-version.txt");
        return candidates.stream()
            .filter(candidate -> available(candidate, directory, probe))
            .findFirst()
            .map(ComposeLoading::new)
            .orElseThrow(() -> new IOException("Docker Compose is required to load compose projects; see " + probe));
    }

    /**
     * Loads one project from its own directory, which decides the override and the .env it reads.
     *
     * @param input    the staged tree
     * @param relative the compose file, relative to the staged tree
     * @param report   where to leave compose's output, with its error stream beside it
     * @return the findings, empty when the project loads
     * @throws IOException when compose cannot be started or its output read
     */
    List<String> load(Path input, Path relative, Path report) throws IOException {
        Path directory = Optional.ofNullable(relative.getParent()).map(input::resolve).orElse(input);
        int exit = ScannerCommand.executeIn(this.with("config", "--quiet"), directory, report);
        if (exit == 0) {
            return List.of();
        }
        List<String> errors = Files.readAllLines(report.resolveSibling(report.getFileName() + ".log")).stream()
            .filter(line -> !line.isBlank())
            .map(line -> relative + ": " + line.strip())
            .toList();
        return errors.isEmpty() ? List.of(relative + ": compose config exited " + exit + " without a reason") : errors;
    }

    // A binary that is not installed cannot start, which answers the question as plainly as a failed exit.
    private static boolean available(List<String> candidate, Path directory, Path probe) {
        try {
            return ScannerCommand.executeIn(new ComposeLoading(candidate).with("version"), directory, probe) == 0;
        } catch (IOException _) {
            return false;
        }
    }

    private List<String> with(String... arguments) {
        return Stream.concat(this.command.stream(), Stream.of(arguments)).toList();
    }
}
