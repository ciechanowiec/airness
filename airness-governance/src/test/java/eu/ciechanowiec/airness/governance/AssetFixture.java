package eu.ciechanowiec.airness.governance;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;

/**
 * A real classloader over a real directory, standing in for the assets artifact.
 *
 * <p>What is being tested is that the catalogue reads manifests and canonical bytes off a classpath,
 * so the classpath has to be one. A directory on a {@link URLClassLoader} is a classpath by every
 * mechanism the code under test uses, and building one costs a temp directory.
 */
final class AssetFixture {

    private static final String SUFFIX = ".asset";

    private final Path directory;

    /**
     * Starts a distribution whose generic manifest is the one given.
     *
     * @param directory the classpath directory
     * @param manifest  the generic manifest
     */
    AssetFixture(Path directory, CharSequence manifest) {
        this.directory = directory;
        this.manifest(Layer.GENERIC, manifest);
    }

    /**
     * Adds the manifest of one layer.
     *
     * @return this fixture, so calls chain
     */
    @SneakyThrows
    AssetFixture manifest(Layer layer, CharSequence manifest) {
        Path file = this.directory.resolve("airness/manifest/" + layer.id() + ".tsv");
        Files.createDirectories(file.getParent());
        Files.writeString(file, manifest);
        return this;
    }

    /**
     * Ships canonical bytes for a generic path.
     *
     * @return this fixture, so calls chain
     */
    AssetFixture ship(String path, CharSequence content) {
        return this.ship(Layer.GENERIC, path, content);
    }

    /**
     * Ships canonical bytes, or a block body, for a path one layer owns.
     *
     * @return this fixture, so calls chain
     */
    @SneakyThrows
    AssetFixture ship(Layer owner, String path, CharSequence content) {
        Path file = this.directory.resolve("airness/files/" + owner.id()).resolve(path + SUFFIX);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    /**
     * Ships the body a layer places in each node project.
     *
     * @return this fixture, so calls chain
     */
    @SneakyThrows
    AssetFixture shipProject(Layer owner, String path, CharSequence content) {
        Path file = this.directory.resolve("airness/project-files/" + owner.id()).resolve(path + SUFFIX);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return this;
    }

    /**
     * Builds a catalogue from the fixture.
     *
     * @return a catalogue reading this fixture and nothing else
     */
    @SneakyThrows
    AssetCatalogue catalogue() {
        URL[] classpath = {this.directory.toUri().toURL()};
        return new AssetCatalogue(new URLClassLoader(classpath, null));
    }
}
