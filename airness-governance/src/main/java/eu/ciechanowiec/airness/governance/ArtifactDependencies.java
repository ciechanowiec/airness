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

/**
 * Exact entry identities supplied by the resolved dependency archives.
 */
final class ArtifactDependencies {

    private final Set<ArtifactOrigin> origins;

    ArtifactDependencies(Collection<Path> archives) {
        this.origins = archives.stream().flatMap(archive -> entries(archive).stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    boolean supplies(String name, String content) {
        return this.origins.contains(origin(name, content.getBytes(StandardCharsets.ISO_8859_1)));
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
