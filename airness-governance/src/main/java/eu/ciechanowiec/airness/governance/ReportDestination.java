package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Guards an output destination without letting a report write into the authored tree.
 */
@UtilityClass
public final class ReportDestination {

    /**
     * Validates the directory and each planned filename before any write.
     *
     * @param root      repository root
     * @param directory requested output directory
     * @param names     report and staging filenames
     */
    public static void verify(Path root, Path directory, List<String> names) {
        Path output = directory.toAbsolutePath().normalize();
        rejectLinks(output);
        names.forEach(name -> rejectLinks(output.resolve(name)));
        if (output.startsWith(root.toAbsolutePath().normalize())) {
            names.forEach(name -> ignored(root, output.resolve(name)));
        }
        inspectExisting(root, output);
    }

    private static void ignored(Path root, Path file) {
        String relative = root.toAbsolutePath().normalize().relativize(file).toString();
        boolean ignored = GitPlumbing.attempt(root, List.of("check-ignore", "--", relative)).isPresent();
        String tracked = GitPlumbing.run(root, List.of("ls-files", "--", relative));
        if (!ignored || !tracked.isBlank()) {
            throw new IllegalArgumentException("Compliance output must be ignored by Git: " + relative);
        }
    }

    private static void rejectLinks(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Compliance output must not follow a symbolic link: " + path);
        }
        Optional.ofNullable(path.getParent()).ifPresent(ReportDestination::rejectLinks);
    }

    private static void inspectExisting(Path root, Path directory) {
        if (Files.exists(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().startsWith("oss-compliance-report-"))
                    .forEach(path -> existing(root, path));
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not inspect report destination " + directory, exception);
            }
        }
    }

    private static void existing(Path root, Path file) {
        rejectLinks(file);
        if (file.startsWith(root.toAbsolutePath().normalize())) {
            ignored(root, file);
        }
    }
}
