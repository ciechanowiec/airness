package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reports newer stable tags and changed tag digests for Airness-owned container images.
 *
 * <p>Both outcomes are informational. A container update has no failure threshold: failure remains
 * reserved for a malformed declaration or a registry response that cannot establish what is current.
 */
public final class ContainerFreshnessCheck {

    private static final String DOCKER_HUB = "https://hub.docker.com";

    private final int scanned;
    private final List<String> updates;
    private final List<String> drifts;

    /**
     * Reads every declared image from Docker Hub.
     *
     * @param declarations digest-pinned image declarations
     */
    public ContainerFreshnessCheck(Collection<DeclaredContainerImage> declarations) {
        this(declarations, DOCKER_HUB);
    }

    ContainerFreshnessCheck(Collection<DeclaredContainerImage> declarations, String registry) {
        DockerHub hub = new DockerHub(registry);
        List<Inspection> inspected = declarations.stream()
            .map(declared -> inspect(declared, hub))
            .toList();
        this.scanned = inspected.size();
        this.updates = inspected.stream().map(Inspection::update).flatMap(Optional::stream).toList();
        this.drifts = inspected.stream().map(Inspection::drift).flatMap(Optional::stream).toList();
    }

    /**
     * How many image declarations were checked.
     *
     * @return checked image count
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every newer stable tag, with the digest that can replace the current pin.
     *
     * @return informational update lines
     */
    public List<String> updates() {
        return List.copyOf(this.updates);
    }

    /**
     * Every declared tag whose registry digest differs from the immutable pin.
     *
     * @return informational tag-drift lines
     */
    public List<String> drifts() {
        return List.copyOf(this.drifts);
    }

    private static Inspection inspect(DeclaredContainerImage declared, DockerHub hub) {
        DockerReference image = DockerReference.from(declared);
        DockerTag current = DockerTag.from(image.tag()).orElseThrow(
            () -> new IllegalStateException("Container image tag is not a stable numeric version: " + image.tag())
        );
        List<DockerTag> candidates = hub.tags(image).stream()
            .map(DockerTag::from)
            .flatMap(Optional::stream)
            .filter(current::sameScheme)
            .toList();
        DockerTag latest = candidates.stream()
            .max(DockerTag::compareVersion)
            .orElseThrow(() -> new IllegalStateException("Docker Hub returned no stable tags for " + image.name()));
        String currentDigest = hub.digest(image, image.tag());
        return new Inspection(
            update(image, current, latest, hub),
            drift(image, currentDigest)
        );
    }

    private static Optional<String> update(
        DockerReference image, DockerTag current, DockerTag latest, DockerHub hub
    ) {
        if (current.compareVersion(latest) >= 0) {
            return Optional.empty();
        }
        String latestDigest = hub.digest(image, latest.raw());
        return Optional.of(
            image.property() + " " + image.pinned(image.tag(), image.digest())
                + " -> " + image.pinned(latest.raw(), latestDigest)
        );
    }

    private static Optional<String> drift(DockerReference image, String currentDigest) {
        if (image.digest().equals(currentDigest)) {
            return Optional.empty();
        }
        return Optional.of(
            image.property() + " " + image.tagged() + " is pinned to " + image.digest()
                + " but now resolves to " + currentDigest
        );
    }

    private record Inspection(Optional<String> update, Optional<String> drift) {
    }
}
