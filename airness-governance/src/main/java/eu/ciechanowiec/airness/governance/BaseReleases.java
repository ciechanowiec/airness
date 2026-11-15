package eu.ciechanowiec.airness.governance;

import java.util.Optional;

/**
 * The newest release of a base image that can replace the one a Dockerfile names, read from Docker Hub.
 *
 * <p>Only a tag that names one release has a newer one: a moving tag such as {@code 26-alpine} is rebuilt in
 * place, which a fresh pull of the same tag finds. The newer release keeps the variant and the major version, so
 * the repair it names changes the image's contents and nothing its project decided. A reference to another
 * registry has no newer release here, and a fresh pull of its tag still finds a rebuild.
 */
public final class BaseReleases {

    private static final String OFFICIAL_NAMESPACE = "library";
    private static final char PATH_SEPARATOR = '/';
    private static final char TAG_SEPARATOR = ':';

    private final DockerHub hub;

    /**
     * Reads the public Docker Hub.
     */
    public BaseReleases() {
        this(RegistryTags.HUB);
    }

    BaseReleases(String registry) {
        this.hub = new DockerHub(registry);
    }

    /**
     * The newest newer release of the same variant and major version, written as the reference wrote its
     * repository.
     *
     * @param reference the base a Dockerfile names, such as {@code node:26.8.2-alpine@sha256:...}
     * @return the replacing reference without a digest, such as {@code node:26.13.1-alpine}, or empty when the
     *         reference names no Docker Hub release or none is newer
     * @throws IllegalStateException when Docker Hub cannot be read
     */
    public Optional<String> newer(String reference) {
        ImageReference image = ImageReference.parse(reference);
        Optional<VariantTag> current = image.tag().flatMap(VariantTag::from).filter(VariantTag::release);
        if (current.isEmpty() || image.unresolved() || hosted(image.repository())) {
            return Optional.empty();
        }
        VariantTag held = current.orElseThrow();
        int slash = image.repository().indexOf(PATH_SEPARATOR);
        DockerReference hubImage = new DockerReference(
            "", image.repository(), slash < 0 ? OFFICIAL_NAMESPACE : image.repository().substring(0, slash),
            image.repository().substring(slash + 1), held.raw(), image.digest().orElse("")
        );
        return this.hub.tags(hubImage).stream().map(VariantTag::from).flatMap(Optional::stream)
            .filter(held::replacedBy).max(VariantTag::compareVersion)
            .map(newest -> written(reference, held.raw()) + TAG_SEPARATOR + newest.raw());
    }

    // Docker Hub keeps no host in the normalised repository, and any other registry keeps its own.
    private static boolean hosted(String repository) {
        int slash = repository.indexOf(PATH_SEPARATOR);
        String first = slash < 0 ? "" : repository.substring(0, slash);
        return first.contains(".") || first.contains(":") || "localhost".equals(first);
    }

    // The repository as the Dockerfile wrote it: the reference up to the tag it names.
    private static String written(String reference, String tag) {
        int at = reference.indexOf('@');
        String named = at < 0 ? reference : reference.substring(0, at);
        return named.substring(0, named.length() - tag.length() - 1);
    }
}
