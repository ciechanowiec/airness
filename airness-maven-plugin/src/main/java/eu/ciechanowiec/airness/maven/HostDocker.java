package eu.ciechanowiec.airness.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;

/**
 * Runs the host commands the image check asks of Docker, through the host's own client, so its credentials, its
 * registry mirrors and its proxy decide what is pulled, as they do for every image the build runs.
 */
interface HostDocker {

    /**
     * Runs a command and returns what it printed on its standard output.
     *
     * @param command the command
     * @param input   a file fed to its standard input, if any
     * @return the standard output
     * @throws IllegalStateException when the command does not start, does not finish, or exits other than zero,
     *                               naming what it printed on its standard error
     */
    String run(List<String> command, Optional<Path> input);

    /**
     * Whether a command exits with zero, with nothing it printed read.
     *
     * @param command the command
     * @return whether it succeeded
     */
    boolean succeeds(List<String> command);

    /**
     * The host's Docker client.
     *
     * @return the commands, run as processes of their own
     */
    static HostDocker system() {
        return new SystemDocker();
    }

    /**
     * The host's processes, each with its output in a file of its own.
     */
    final class SystemDocker implements HostDocker {

        private static final int NAMED_WORDS = 3;

        @Override
        @SneakyThrows
        public String run(List<String> command, Optional<Path> input) {
            Path output = Files.createTempFile("airness-image-", ".out");
            Path log = output.resolveSibling(output.getFileName() + ".log");
            try {
                int exit = input.isPresent() ? ScannerCommand.executeWithInput(command, input.orElseThrow(), output)
                    : ScannerCommand.execute(command, output);
                if (exit != 0) {
                    throw new IllegalStateException(
                        String.join(" ", command.subList(0, Math.min(NAMED_WORDS, command.size()))) + " exited " + exit
                            + ": " + Files.readString(log).strip()
                    );
                }
                return Files.readString(output);
            } finally {
                Files.deleteIfExists(output);
                Files.deleteIfExists(log);
            }
        }

        @Override
        @SneakyThrows
        public boolean succeeds(List<String> command) {
            Path output = Files.createTempFile("airness-image-", ".out");
            try {
                return ScannerCommand.execute(command, output) == 0;
            } finally {
                Files.deleteIfExists(output);
                Files.deleteIfExists(output.resolveSibling(output.getFileName() + ".log"));
            }
        }
    }
}
