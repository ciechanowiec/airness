package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A starter read off the plugin classpath, where airness-starters packs each one under
 * {@code airness/starters/<name>}.
 *
 * <p>The classpath holds the starters jar when the plugin runs, and a classes directory when a build of this
 * repository tests the plugin before packaging, so a starter is opened as a directory of either. A jar opened
 * for it stays open until the starter is closed.
 */
final class StarterTemplate implements AutoCloseable {

    /**
     * The classpath directory every starter is packed under.
     */
    static final String LOCATION = "airness/starters/";

    private static final String MARKER = "pom.xml" + StarterCopy.FIXTURE;
    private static final String ENTRY = "!/";
    private static final String JAR = "jar";

    private final Path root;
    private final Optional<FileSystem> archive;

    private StarterTemplate(Path root, Optional<FileSystem> archive) {
        this.root = root;
        this.archive = archive;
    }

    /**
     * Opens the starter of the given name.
     *
     * @param loader the class loader whose classpath holds the starters
     * @param name   the name of the starter, such as spring-boot
     * @return the starter, to be closed once it is written
     * @throws IOException when no starter has that name or its jar cannot be opened
     */
    static StarterTemplate open(ClassLoader loader, String name) throws IOException {
        URL marker = Optional.ofNullable(loader.getResource(LOCATION + name + "/" + MARKER))
            .orElseThrow(() -> new IOException("Airness has no starter named " + name));
        URI address = address(marker);
        if (!JAR.equals(address.getScheme())) {
            return new StarterTemplate(parent(Path.of(address)), Optional.empty());
        }
        String raw = address.getRawSchemeSpecificPart();
        String decoded = address.getSchemeSpecificPart();
        Path jar = Path.of(URI.create(raw.substring(0, raw.indexOf(ENTRY))));
        FileSystem archive = FileSystems.newFileSystem(jar);
        Path entry = archive.getPath(decoded.substring(decoded.indexOf(ENTRY) + 1));
        return new StarterTemplate(parent(entry), Optional.of(archive));
    }

    /**
     * Answers the directory of the starter.
     *
     * @return the directory, inside the jar when the starter was read from one
     */
    Path root() {
        return this.root;
    }

    @Override
    public void close() throws IOException {
        if (this.archive.isPresent()) {
            this.archive.get().close();
        }
    }

    private static URI address(URL marker) throws IOException {
        try {
            return marker.toURI();
        } catch (URISyntaxException exception) {
            throw new IOException("The starter at " + marker + " has no readable address", exception);
        }
    }

    private static Path parent(Path marker) {
        return Objects.requireNonNull(marker.getParent(), "a starter's pom lives in the starter's directory");
    }
}
