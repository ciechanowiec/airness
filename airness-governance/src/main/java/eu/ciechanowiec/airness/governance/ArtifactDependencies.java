package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Exact entry identities supplied by the resolved dependency archives.
 *
 * <p>A directory entry is supplied too when a dependency supplies an entry beneath it. A packaging plugin
 * that copies a dependency's file writes the directories above it into the archive it builds, whether or
 * not the dependency's own archive lists them, so such a directory comes from the dependency as much as
 * the file does.
 */
final class ArtifactDependencies {

    private static final String SEPARATOR = "/";

    private final Set<ArtifactOrigin> origins;

    private final Set<String> directories;

    ArtifactDependencies(Collection<Path> archives) {
        this.origins = archives.stream().flatMap(archive -> entries(archive).stream())
            .collect(Collectors.toUnmodifiableSet());
        this.directories = this.origins.stream()
            .flatMap(origin -> parents(origin.name()))
            .collect(Collectors.toUnmodifiableSet());
    }

    boolean supplies(String name, String content) {
        boolean directory = name.endsWith(SEPARATOR) && this.directories.contains(name);
        return directory || this.origins.contains(origin(name, content.getBytes(StandardCharsets.ISO_8859_1)));
    }

    // Every directory above an entry, each with its trailing separator, as an archive names directories.
    private static Stream<String> parents(String name) {
        return IntStream.range(0, name.length())
            .filter(index -> name.startsWith(SEPARATOR, index) && index < name.length() - 1)
            .mapToObj(index -> name.substring(0, index + 1));
    }

    private static List<ArtifactOrigin> entries(Path archive) {
        try (JarFile jar = new JarFile(archive.toFile())) {
            List<ArtifactOrigin> entries = new ArrayList<>();
            for (JarEntry entry : jar.stream().toList()) {
                entries.add(entry(jar, entry));
            }
            return List.copyOf(entries);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read dependency archive " + archive, exception);
        }
    }

    private static ArtifactOrigin entry(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream input = jar.getInputStream(entry)) {
            return origin(entry.getName(), input.readAllBytes());
        }
    }

    private static ArtifactOrigin origin(String name, byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return new ArtifactOrigin(name, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
