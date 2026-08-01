package eu.ciechanowiec.airness.governance;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;

/**
 * A real classloader over a real directory, standing in for the assets artifact.
 *
 * <p>What is being tested is that the catalogue reads a manifest and canonical bytes off a classpath,
 * so the classpath has to be one. A directory on a {@link URLClassLoader} is a classpath by every
 * mechanism the code under test uses, and building one costs a temp directory.
 */
final class AssetFixture {

    private static final String SUFFIX = ".asset";

    private final Path directory;

    @SneakyThrows
    AssetFixture(Path directory, String manifest) {
        this.directory = directory;
        Files.createDirectories(directory.resolve("airness/files"));
        Files.writeString(directory.resolve("airness/manifest.tsv"), manifest);
    }

    /**
     * Ships canonical bytes for a path.
     *
     * @return this fixture, so calls chain
     */
    @SneakyThrows
    AssetFixture ship(String path, String content) {
        Path file = this.directory.resolve("airness/files").resolve(path + SUFFIX);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    /**
     * @return a catalogue reading this fixture and nothing else
     */
    @SneakyThrows
    AssetCatalogue catalogue() {
        URL[] classpath = {this.directory.toUri().toURL()};
        return new AssetCatalogue(new URLClassLoader(classpath, null));
    }
}
