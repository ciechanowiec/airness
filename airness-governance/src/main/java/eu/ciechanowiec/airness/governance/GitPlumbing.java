package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Runs read-only {@code git} plumbing commands in a repository working tree and returns their standard
 * output as text. The governance checks lean on this to read the commit history, the tracked file set,
 * and per-commit diffs without pulling in a third-party git library. Standard error is discarded so a
 * chatty command cannot deadlock on an unread pipe.
 */
@UtilityClass
final class GitPlumbing {

    static String run(Path repository, List<String> arguments) {
        Process process = start(repository, arguments);
        String output = read(process);
        awaitSuccess(process, arguments);
        return output;
    }

    /**
     * Runs a command whose failure is one of its answers rather than a fault.
     *
     * <p>Asking git whether something exists is the case this serves. Git answers no by exiting non-zero,
     * which {@link #run} raises, so a caller that wants the answer rather than the exception has to be
     * able to say so. Every other failure of such a command is indistinguishable from the no, which is why
     * this stays for questions whose wrong answer costs a caller nothing.
     *
     * @param repository the working tree to run in
     * @param arguments  the git arguments, without the command name
     * @return the standard output, or nothing when git exited non-zero
     */
    static Optional<String> attempt(Path repository, List<String> arguments) {
        Process process = start(repository, arguments);
        String output = read(process);
        return await(process) == 0 ? Optional.of(output) : Optional.empty();
    }

    private static Process start(Path repository, Collection<String> arguments) {
        List<String> command = Stream.concat(Stream.of("git"), arguments.stream()).toList();
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(repository.toFile());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        try {
            return builder.start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not start git " + arguments, exception);
        }
    }

    private static String read(Process process) {
        try (InputStream stream = process.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read git output", exception);
        }
    }

    private static void awaitSuccess(Process process, List<String> arguments) {
        int code = await(process);
        if (code != 0) {
            throw new IllegalStateException("git " + arguments + " exited with code " + code);
        }
    }

    private static int await(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for git", exception);
        }
    }
}
