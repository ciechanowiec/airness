package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.AssetCatalogue;
import eu.ciechanowiec.airness.governance.AssetCheck;
import eu.ciechanowiec.airness.governance.AssetPolicy;
import eu.ciechanowiec.airness.governance.ManagedAsset;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.settings.Mirror;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * The startup files and effective Maven mirror that confine dependency downloads to Maven Central.
 *
 * <p>User settings still supply credentials and proxies. Their mirrors may not replace or precede
 * the harness mirror. The active resolver is checked as well as the request from which Maven built it.
 * Checking files alone cannot detect an alternate settings argument or an effective mirror override.
 *
 * @param root      the repository owning the startup files
 * @param request   the effective Maven execution request
 * @param resolver  the active dependency resolver's mirror selection
 * @param catalogue the canonical startup files supplied by Airness
 */
record MavenRepositoryPolicy(
    Path root, MavenExecutionRequest request, MirrorSelector resolver, AssetCatalogue catalogue
) {

    private static final String SETTINGS = ".mvn/settings.xml";
    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final String MIRROR = "airness-central";
    private static final List<String> FILES = List.of(".mvn/maven.config", SETTINGS);

    List<String> problems() {
        return Stream.of(this.files(), this.settings(), this.mirrors(), this.routing())
            .flatMap(Function.identity())
            .toList();
    }

    private Stream<String> files() {
        AssetCheck check = new AssetCheck(this.root, this.catalogue, List.of());
        return FILES.stream()
            .filter(path -> !check.matches(new ManagedAsset(path, AssetPolicy.PINNED)))
            .map(
                path -> path + " must match Airness. Remove any airness.assets.unmanaged entry for this file,"
                    + " run mvn airness:assets-sync and restart Maven"
            );
    }

    private Stream<String> settings() {
        Path expected = this.root.resolve(SETTINGS).toAbsolutePath().normalize();
        boolean active = Optional.ofNullable(this.request.getGlobalSettingsFile())
            .map(File::toPath)
            .filter(path -> sameFile(expected, path))
            .isPresent();
        return active ? Stream.empty() : Stream.of(
            "Maven must load the Airness global settings from " + expected
                + "; remove the -gs/--global-settings override and restart Maven"
        );
    }

    private Stream<String> mirrors() {
        List<Mirror> mirrors = this.request.getMirrors();
        boolean canonical = mirrors.size() == 1 && canonical(mirrors.getFirst());
        return canonical ? Stream.empty() : Stream.of(
            "Effective Maven settings must contain only the Airness catch-all Maven Central mirror;"
                + " remove mirror overrides from user or alternate settings"
        );
    }

    private Stream<String> routing() {
        return Stream.of("central", MIRROR, "dependency-repository")
            .filter(id -> !this.routed(id))
            .map(
                id -> "The active Maven resolver does not route " + id
                    + " through the Airness Maven Central mirror; remove resolver overrides"
            );
    }

    private boolean routed(String id) {
        RemoteRepository repository = new RemoteRepository.Builder(
            id, "default", "https://unapproved.invalid/maven/"
        ).build();
        return Optional.ofNullable(this.resolver.getMirror(repository))
            .filter(mirror -> MIRROR.equals(mirror.getId()) && CENTRAL.equals(mirror.getUrl()))
            .filter(mirror -> !mirror.isBlocked() && "default".equals(mirror.getContentType()))
            .isPresent();
    }

    private static boolean canonical(Mirror mirror) {
        boolean destination = MIRROR.equals(mirror.getId()) && CENTRAL.equals(mirror.getUrl());
        boolean wildcard = "*".equals(mirror.getMirrorOf()) && "*".equals(mirror.getMirrorOfLayouts());
        boolean usable = "default".equals(mirror.getLayout()) && !mirror.isBlocked();
        return destination && wildcard && usable;
    }

    @SneakyThrows
    private static boolean sameFile(Path expected, Path actual) {
        return Files.isRegularFile(expected) && Files.isRegularFile(actual) && Files.isSameFile(expected, actual);
    }
}
