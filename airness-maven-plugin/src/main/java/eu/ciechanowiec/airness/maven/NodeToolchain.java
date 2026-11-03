package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.DeclaredCoordinate;
import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.VersionUpdate;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
record NodeToolchain(PinnedArchive node, PinnedArchive pnpm, Map<String, String> pins) {

    private static final String RESOURCE = "/eu/ciechanowiec/airness/node/toolchain.properties";
    private static final String NODE = "node";
    private static final String PNPM = "pnpm";
    private static final String VERSION = "version";
    private static final String OWNER = "Airness toolchain pins";
    private static final String NODE_INDEX = "nodejs.org";
    private static final int LTS_BOUND = 2;
    private static final String PNPM_PACKAGE = "%40pnpm/exe.";
    private static final Map<String, String> SYSTEMS = Map.of("mac os x", "darwin", "linux", "linux");
    private static final Map<String, String> ARCHITECTURES = Map.of(
        "aarch64", "arm64", "arm64", "arm64", "amd64", "x64", "x86_64", "x64"
    );

    NodeToolchain {
        pins = Map.copyOf(pins);
    }

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
    static NodeToolchain of(Map<String, String> pins, String platform) {
        return new NodeToolchain(archive(pins, NODE, platform), archive(pins, PNPM, platform), pins);
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
     * @return the description, such as {@code Node 26.13.0 and pnpm 12.16.1}
     */
    String description() {
        return "Node " + this.pin(NODE, "version") + " and pnpm " + this.pin(PNPM, "version");
    }

    /**
     * The pinned Node and pnpm as the build tools the compliance report lists, each named by a package address
     * that carries the SHA-256 of the archive this platform installs.
     *
     * @param module the module whose node projects they ran in
     * @return Node, then pnpm
     */
    List<ReportPackage> packages(String module) {
        return Stream.of(
            this.tool("pkg:generic/node@" + this.pin(NODE, VERSION), this.node, NODE),
            this.tool("pkg:npm/" + PNPM_PACKAGE + this.pnpmPlatform() + '@' + this.pin(PNPM, VERSION), this.pnpm, PNPM)
        ).map(
            entry -> new ReportPackage(
                entry.getKey(), List.of(entry.getValue()), List.of(NpmReportInputs.tool(module)), Map.of()
            )
        ).toList();
    }

    /**
     * The pinned versions, as the declarations the freshness bound holds against the latest releases: Node as
     * the Node release index lists its long-term-support releases, and pnpm as the npm registry lists its
     * native executable.
     *
     * @return Node, then pnpm
     */
    List<OwnedCoordinate> declarations() {
        return List.of(
            new OwnedCoordinate(OWNER, new DeclaredCoordinate(NODE_INDEX, NODE, this.pin(NODE, VERSION))),
            new OwnedCoordinate(OWNER, new DeclaredCoordinate("npm", "@pnpm/exe", this.pin(PNPM, VERSION)))
        );
    }

    /**
     * The freshness of the pins: Node against its long-term-support releases, and pnpm against the npm registry.
     *
     * @param index    the Node release index
     * @param registry the npm registries, which list pnpm's native executable
     * @return the result of both
     */
    NpmFreshness freshness(String index, NpmRegistries registry) {
        List<OwnedCoordinate> declared = this.declarations();
        NpmFreshness runtime = NpmFreshness.of(
            List.of(declared.getFirst()), _ -> registry.nodeLongTermReleases(index), NodeToolchain::longTermTrailing
        );
        NpmFreshness manager = NpmFreshness.of(
            List.of(declared.getLast()), pin -> registry.versions(pin.coordinate().artifactId())
        );
        return new NpmFreshness(
            runtime.scanned() + manager.scanned(), concat(runtime.updates(), manager.updates()),
            concat(runtime.trailing(), manager.trailing()), concat(runtime.unread(), manager.unread())
        );
    }

    /**
     * The Node bound, counted in long-term-support lines: the pin fails once two newer lines than its own exist.
     * Node publishes a major every six months and only the even ones become long-term-support releases, so a
     * bound of two majors would fail every consumer a year after a release, on a pin no consumer can change.
     *
     * @param update the pinned version and the newest long-term-support release
     * @return the violation, or nothing within the bound
     */
    static Optional<String> longTermTrailing(VersionUpdate update) {
        int pinned = major(update.declared().coordinate().version());
        int latest = major(update.latest());
        int lines = (latest - pinned + 1) / 2;
        return Optional.of(lines).filter(behind -> behind >= LTS_BOUND).map(
            behind -> "[%s] node %s is %d long-term-support lines behind %s".formatted(
                update.declared().owner(), update.declared().coordinate().version(), behind, update.latest()
            )
        );
    }

    private static int major(String version) {
        return Integer.parseInt(
            version.substring(0, version.indexOf('.') < 0 ? version.length() : version.indexOf('.'))
        );
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private Map.Entry<String, String> tool(String purl, PinnedArchive archive, String tool) {
        return Map.entry(purl + "?checksum=sha256:" + archive.sha256(), this.pin(tool, "license"));
    }

    private String pnpmPlatform() {
        String name = this.pnpm.name();
        String prefix = PNPM + '-' + this.pin(PNPM, VERSION) + '-';
        return name.substring(prefix.length());
    }

    private String pin(String tool, String key) {
        return required(this.pins, tool + '.' + key);
    }

    private static PinnedArchive archive(Map<String, String> pins, String tool, String platform) {
        String version = required(pins, tool + ".version");
        String source = required(pins, tool + ".source").replace("{version}", version).replace("{platform}", platform);
        String sha256 = Optional.ofNullable(pins.get(tool + ".sha256." + platform)).orElseThrow(
            () -> new IllegalStateException("Airness pins no " + tool + " archive for " + platform)
        );
        return new PinnedArchive(tool + '-' + version + '-' + platform, URI.create(source), sha256);
    }

    private static String required(Map<String, String> pins, String key) {
        return Optional.ofNullable(pins.get(key)).orElseThrow(
            () -> new IllegalStateException("The Node toolchain pins carry no " + key)
        );
    }

    private static Map<String, String> packaged() {
        URL resource = Optional.ofNullable(NodeToolchain.class.getResource(RESOURCE)).orElseThrow(
            () -> new IllegalStateException("The plugin jar carries no Node toolchain pins at " + RESOURCE)
        );
        try (InputStream input = resource.openStream()) {
            Properties pins = new Properties();
            pins.load(input);
            return pins.stringPropertyNames().stream().collect(
                Collectors.toUnmodifiableMap(key -> key, pins::getProperty)
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the Node toolchain pins", exception);
        }
    }
}
