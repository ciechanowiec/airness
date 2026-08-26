package eu.ciechanowiec.airness.governance;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A Docker Hub image reference split into the parts used by its registry API.
 */
record DockerReference(
    String property, String name, String namespace, String repository, String tag, String digest
) {

    private static final Pattern PINNED = Pattern.compile(
        "^(?<name>[a-z0-9][a-z0-9._/-]*):(?<tag>[A-Za-z0-9][A-Za-z0-9._-]*)"
            + "@(?<digest>sha256:[0-9a-f]{64})$"
    );
    private static final String DOCKER = "docker.io/";
    private static final String REGISTRY = "registry-1.docker.io/";

    static DockerReference from(DeclaredContainerImage declared) {
        Matcher matched = PINNED.matcher(declared.reference());
        if (!matched.matches()) {
            throw new IllegalArgumentException("Invalid digest-pinned container image: " + declared.reference());
        }
        String name = matched.group("name");
        String hubName = hubName(name);
        int separator = hubName.indexOf('/');
        if (separator < 1 || separator == hubName.length() - 1) {
            throw new IllegalArgumentException("A Docker Hub image must name its namespace: " + name);
        }
        return new DockerReference(
            declared.property(), name, hubName.substring(0, separator), hubName.substring(separator + 1),
            matched.group("tag"), matched.group("digest")
        );
    }

    String tagged() {
        return this.name + ':' + this.tag;
    }

    String pinned(String heldTag, String heldDigest) {
        return this.name + ':' + heldTag + '@' + heldDigest;
    }

    private static String hubName(String name) {
        String prefix = Stream.of(DOCKER, REGISTRY)
            .filter(name::startsWith)
            .findFirst()
            .orElse("");
        return name.substring(prefix.length());
    }
}
