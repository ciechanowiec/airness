package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * A container image reference split into the parts the blocklist reads, with the repository written
 * one way however the file spelled it.
 *
 * <p>The same image is written {@code mongo}, {@code library/mongo}, {@code docker.io/mongo}, and
 * {@code docker.io/library/mongo}, and a list keyed on the spelling would refuse one of them and pull
 * the other. So the Docker Hub host and the official namespace are dropped, and any other registry host
 * is kept, which is the rule the docker client itself applies before it asks a registry for anything.
 *
 * <p>This is not {@link DockerReference}, which reads the two digest-pinned images the harness owns and
 * refuses every other shape. A reference a project writes takes every shape, and what to do with the
 * unpinned ones is this rule's own verdict.
 *
 * @param raw        the reference as written
 * @param repository the repository in its one spelling
 * @param tag        the tag, when the reference carries one
 * @param digest     the digest, when the reference carries one
 */
record ImageReference(String raw, String repository, Optional<String> tag, Optional<String> digest) {

    private static final Set<String> DOCKER_HUB_HOSTS = Set.of("docker.io", "index.docker.io");
    private static final String OFFICIAL_NAMESPACE = "library/";
    private static final String LOCAL_HOST = "localhost";
    private static final String MUTABLE_TAG = "latest";
    private static final String VARIABLE = "$";
    private static final char DIGEST_SEPARATOR = '@';
    private static final char TAG_SEPARATOR = ':';
    private static final char PATH_SEPARATOR = '/';
    private static final String DOT = ".";
    private static final String PORT = ":";

    /**
     * Splits a reference as a Dockerfile, a compose file, a workflow, or a Java literal writes it.
     *
     * @param raw the reference, such as {@code docker.io/library/redis:7.4.1@sha256:...}
     * @return the reference read into its parts
     */
    static ImageReference parse(String raw) {
        int at = raw.indexOf(DIGEST_SEPARATOR);
        String named = at < 0 ? raw : raw.substring(0, at);
        Optional<String> digest = at < 0 ? Optional.empty() : Optional.of(raw.substring(at + 1));
        int colon = named.lastIndexOf(TAG_SEPARATOR);
        boolean tagged = colon > named.lastIndexOf(PATH_SEPARATOR);
        String name = tagged ? named.substring(0, colon) : named;
        Optional<String> tag = tagged ? Optional.of(named.substring(colon + 1)) : Optional.empty();
        return new ImageReference(raw, normalised(name), tag, digest);
    }

    /**
     * Whether what the reference pulls can change without the reference changing: no digest, and either
     * no tag or the one tag every registry moves.
     *
     * @return whether nothing in the reference pins what is pulled
     */
    boolean mutable() {
        return this.digest.isEmpty() && this.tag.filter(held -> !MUTABLE_TAG.equals(held)).isEmpty();
    }

    /**
     * Whether the reference still carries a variable nothing substituted, so no rule can say what it
     * pulls.
     *
     * @return whether a variable remains in the reference
     */
    boolean unresolved() {
        return this.raw.contains(VARIABLE);
    }

    // The first path segment is a registry host when it carries a dot or a port, or is localhost. A
    // Docker Hub namespace never does, which is how the docker client tells the two apart.
    private static String normalised(String name) {
        String lowered = name.toLowerCase(Locale.ROOT);
        int slash = lowered.indexOf(PATH_SEPARATOR);
        String first = slash < 0 ? "" : lowered.substring(0, slash);
        if (isHost(first)) {
            String rest = lowered.substring(slash + 1);
            return DOCKER_HUB_HOSTS.contains(first) ? withoutOfficialNamespace(rest) : lowered;
        }
        return withoutOfficialNamespace(lowered);
    }

    private static boolean isHost(String segment) {
        return segment.contains(DOT) || segment.contains(PORT) || LOCAL_HOST.equals(segment);
    }

    private static String withoutOfficialNamespace(String path) {
        return path.startsWith(OFFICIAL_NAMESPACE) ? path.substring(OFFICIAL_NAMESPACE.length()) : path;
    }
}
