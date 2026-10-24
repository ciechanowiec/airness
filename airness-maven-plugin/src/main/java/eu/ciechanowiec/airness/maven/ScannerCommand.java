package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

/**
 * Bounded host-side Docker operations. credentials remain with the host Docker client.
 */
@UtilityClass
final class ScannerCommand {

    private static final Duration TIMEOUT = Duration.ofMinutes(20);

    static void run(List<String> command, Path output) throws IOException {
        int exit = execute(command, output);
        if (exit != 0) {
            throw new IOException("Docker operation failed with exit " + exit + "; see " + output);
        }
    }

    static int execute(List<String> command, Path output) throws IOException {
        Process process = new ProcessBuilder(command)
            .redirectOutput(output.toFile())
            .redirectError(output.resolveSibling(output.getFileName() + ".log").toFile())
            .start();
        return await(process, output, TIMEOUT);
    }

    /**
     * Runs a host command with the given file as its standard input.
     *
     * @param command the command
     * @param input   the file fed to its standard input
     * @param output  where its standard output goes, with its standard error beside it
     * @return the exit code
     * @throws IOException when the command cannot start, times out or is interrupted
     */
    static int executeWithInput(List<String> command, Path input, Path output) throws IOException {
        Process process = new ProcessBuilder(command)
            .redirectInput(input.toFile())
            .redirectOutput(output.toFile())
            .redirectError(output.resolveSibling(output.getFileName() + ".log").toFile())
            .start();
        return await(process, output, TIMEOUT);
    }

    /**
     * Runs a host command from the given directory.
     *
     * @param command   the command
     * @param directory the working directory
     * @param output    where its standard output goes, with its standard error beside it
     * @return the exit code
     * @throws IOException when the command cannot start, times out or is interrupted
     */
    static int executeIn(List<String> command, Path directory, Path output) throws IOException {
        Process process = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(output.toFile())
            .redirectError(output.resolveSibling(output.getFileName() + ".log").toFile())
            .start();
        return await(process, output, TIMEOUT);
    }

    static int await(Process process, Path output, Duration timeout) throws IOException {
        try {
            if (!process.waitFor(Duration.of(timeout.toMillis(), TimeUnit.MILLISECONDS.toChronoUnit()))) {
                process.destroyForcibly();
                throw new IOException("Docker operation timed out; see " + output);
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted Docker operation; see " + output, exception);
        }
    }
}
