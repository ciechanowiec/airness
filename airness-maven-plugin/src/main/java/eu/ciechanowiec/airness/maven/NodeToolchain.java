package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * The Node and pnpm one Airness release pins, for the machine running the build.
 *
 * <p>The pins are read from the plugin jar, so no consumer property can replace them, and each archive is
 * held to the SHA-256 the pins name for this platform. Both are unpacked under the local Maven repository,
 * where every later build on the machine finds them without downloading again. Only the platforms the pins
 * name are supported: a build elsewhere fails with the platform named rather than running another Node.
 *
 * @param node the pinned Node archive
 * @param pnpm the pinned pnpm archive
 * @param pins the pin file the archives were read from, which also names the executable inside each
 */
record NodeToolchain(PinnedArchive node, PinnedArchive pnpm, Properties pins) {

    private static final String RESOURCE = "/eu/ciechanowiec/airness/node/toolchain.properties";
    private static final String NODE = "node";
    private static final String PNPM = "pnpm";
    private static final Map<String, String> SYSTEMS = Map.of("mac os x", "darwin", "linux", "linux");
    private static final Map<String, String> ARCHITECTURES = Map.of(
        "aarch64", "arm64", "arm64", "arm64", "amd64", "x64", "x86_64", "x64"
    );

    /**
     * The toolchain the installed Airness release pins, for the platform this JVM runs on.
     *
     * @return the pinned toolchain
     */
    static NodeToolchain pinned() {
        return of(packaged(), platform(System.getProperty("os.name", ""), System.getProperty("os.arch", "")));
    }

    /**
     * The toolchain a pin file names for one platform.
     *
     * @param pins     the pin file
     * @param platform the platform, such as {@code darwin-arm64}
     * @return the toolchain
     */
    static NodeToolchain of(Properties pins, String platform) {
        Properties copy = new Properties();
        copy.putAll(pins);
        return new NodeToolchain(archive(copy, NODE, platform), archive(copy, PNPM, platform), copy);
    }

    /**
     * Names the platform the way Node and pnpm name their release archives.
     *
     * @param system       the {@code os.name} system property
     * @param architecture the {@code os.arch} system property
     * @return the platform, such as {@code linux-x64}
     */
    static String platform(String system, String architecture) {
        Optional<String> named = Optional.ofNullable(SYSTEMS.get(system.toLowerCase(Locale.ROOT)));
        Optional<String> machine = Optional.ofNullable(ARCHITECTURES.get(architecture.toLowerCase(Locale.ROOT)));
        return named.flatMap(os -> machine.map(arch -> os + '-' + arch)).orElseThrow(
            () -> new IllegalStateException(
                "Airness pins Node and pnpm for macOS and Linux on x64 and arm64, and this build runs on "
                    + system + ' ' + architecture
            )
        );
    }

    /**
     * Where every pinned archive is unpacked, beneath the local Maven repository.
     *
     * @param localRepository the local Maven repository
     * @return the cache directory
     */
    static Path cache(Path localRepository) {
        return localRepository.resolve(".airness").resolve("toolchain");
    }

    /**
     * Installs both archives into the cache when they are not there yet.
     *
     * @param cache the cache directory
     * @return the installed executables
     * @throws IOException when an archive cannot be downloaded, verified or unpacked
     */
    NodeTools installIn(Path cache) throws IOException {
        return NodeTools.of(
            this.node.unpackedIn(cache).resolve(this.pin(NODE, "executable")),
            this.pnpm.unpackedIn(cache).resolve(this.pin(PNPM, "executable"))
        );
    }

    /**
     * Names the pinned versions for the build log.
     *
     * @return the description, such as {@code Node 26.11.1 and pnpm 12.14.0}
     */
    String description() {
        return "Node " + this.pin(NODE, "version") + " and pnpm " + this.pin(PNPM, "version");
    }

    private String pin(String tool, String key) {
        return required(this.pins, tool + '.' + key);
    }

    private static PinnedArchive archive(Properties pins, String tool, String platform) {
        String version = required(pins, tool + ".version");
        String source = required(pins, tool + ".source").replace("{version}", version).replace("{platform}", platform);
        String sha256 = Optional.ofNullable(pins.getProperty(tool + ".sha256." + platform)).orElseThrow(
            () -> new IllegalStateException("Airness pins no " + tool + " archive for " + platform)
        );
        return new PinnedArchive(tool + '-' + version + '-' + platform, URI.create(source), sha256);
    }

    private static String required(Properties pins, String key) {
        return Optional.ofNullable(pins.getProperty(key)).orElseThrow(
            () -> new IllegalStateException("The Node toolchain pins carry no " + key)
        );
    }

    private static Properties packaged() {
        URL resource = Optional.ofNullable(NodeToolchain.class.getResource(RESOURCE)).orElseThrow(
            () -> new IllegalStateException("The plugin jar carries no Node toolchain pins at " + RESOURCE)
        );
        try (InputStream input = resource.openStream()) {
            Properties pins = new Properties();
            pins.load(input);
            return pins;
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the Node toolchain pins", exception);
        }
    }
}
