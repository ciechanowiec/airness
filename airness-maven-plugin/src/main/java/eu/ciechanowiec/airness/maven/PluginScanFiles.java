package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

/**
 * An invocation's resolved plugin archives, with stable names and no stale inputs.
 *
 * @param directory owned staging directory under the Maven build directory
 */
record PluginScanFiles(Path directory) {

    private static final String JAR = ".jar";

    void write(Map<String, Path> artifacts) {
        try {
            this.clear();
            Files.createDirectories(this.directory);
            for (Map.Entry<String, Path> artifact : artifacts.entrySet()) {
                this.copy(artifact);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not prepare plugin vulnerability inputs", exception);
        }
    }

    private void copy(Map.Entry<String, Path> artifact) throws IOException {
        Path source = artifact.getValue();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Plugin input is not a resolved archive: " + source);
        }
        Path destination = this.directory.resolve(identity(artifact.getKey()));
        Files.createDirectory(destination);
        Files.copy(source, destination.resolve(source.getFileName()));
        String filename = source.getFileName().toString();
        if (filename.endsWith(JAR)) {
            metadata(artifact.getKey(), filename, destination);
        }
    }

    private static void metadata(String coordinate, String filename, Path directory) throws IOException {
        String[] parts = coordinate.split(":", -1);
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(parts[0]);
        model.setArtifactId(parts[1]);
        model.setVersion(parts[parts.length - 1]);
        Path pom = directory.resolve(filename.substring(0, filename.lastIndexOf('.')) + ".pom");
        try (Writer writer = Files.newBufferedWriter(pom)) {
            new MavenXpp3Writer().write(writer, model);
        }
    }

    private void clear() throws IOException {
        if (Files.exists(this.directory)) {
            try (Stream<Path> files = Files.walk(this.directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private static String identity(String coordinate) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(coordinate.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
