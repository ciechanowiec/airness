package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportDestination;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;

/**
 * Stages a complete report pair before replacing earlier report files.
 *
 * @param root      repository root
 * @param directory ignored or external destination
 * @param stem      common report basename
 */
record ReportPublication(Path root, Path directory, String stem) {

    ReportPublication {
        directory = directory.toAbsolutePath().normalize();
    }

    @SneakyThrows
    void publish(String html, byte[] pdf) {
        List<String> names = List.of(this.stem + ".html", this.stem + ".pdf");
        List<String> staging = names.stream().map(name -> name + ".tmp").toList();
        ReportDestination.verify(this.root, this.directory, Stream.concat(names.stream(), staging.stream()).toList());
        Files.createDirectories(this.directory);
        Path htmlStage = this.directory.resolve(staging.getFirst());
        Path pdfStage = this.directory.resolve(staging.getLast());
        try {
            Files.writeString(htmlStage, html);
            Files.write(pdfStage, pdf);
            this.replace(names);
            this.removeOlder(names);
        } finally {
            Files.deleteIfExists(htmlStage);
            Files.deleteIfExists(pdfStage);
        }
    }

    private void replace(List<String> names) throws IOException {
        Map<Path, byte[]> previous = names.stream().map(this.directory::resolve).filter(Files::isRegularFile)
            .collect(Collectors.toUnmodifiableMap(path -> path, ReportPublication::bytes));
        try {
            for (String name : names) {
                Files.move(
                    this.directory.resolve(name + ".tmp"), this.directory.resolve(name),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            this.restore(names, previous);
            throw exception;
        }
    }

    private void restore(List<String> names, Map<Path, byte[]> previous) throws IOException {
        for (String name : names) {
            Path file = this.directory.resolve(name);
            if (previous.containsKey(file)) {
                Files.write(file, Objects.requireNonNull(previous.get(file)));
            } else if (Files.isRegularFile(file)) {
                Files.delete(file);
            }
        }
    }

    @SneakyThrows
    private static byte[] bytes(Path file) {
        return Files.readAllBytes(file);
    }

    @SneakyThrows
    private void removeOlder(List<String> keep) {
        try (Stream<Path> files = Files.list(this.directory)) {
            for (Path file : files.filter(path -> this.old(path, keep)).toList()) {
                Files.delete(file);
            }
        }
    }

    private boolean old(Path file, List<String> keep) {
        String name = file.getFileName().toString();
        String prefix = this.stem.substring(0, this.stem.lastIndexOf('-') + 1);
        boolean report = name.matches(Pattern.quote(prefix) + "[0-9a-f]{12}\\.(html|pdf)");
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            && report && !keep.contains(name);
    }
}
