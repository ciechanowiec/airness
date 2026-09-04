package eu.ciechanowiec.airness.governance;

import java.util.Optional;

/**
 * One Maven coordinate family the blocklist refuses, and what a project depends on instead.
 *
 * @param group       the group identifier, with an optional trailing asterisk
 * @param artifact    the artifact identifier, with an optional trailing asterisk
 * @param floor       the first refused version, when the artifact was once open
 * @param reason      why it is refused
 * @param replacement what to depend on instead
 */
record BlockedCoordinate(String group, String artifact, Optional<String> floor, String reason, String replacement) {

    static BlockedCoordinate of(String group, String artifact, String reason, String replacement) {
        return new BlockedCoordinate(group, artifact, Optional.empty(), reason, replacement);
    }

    boolean matches(String groupId, String artifactId) {
        return NamePattern.matches(this.group, groupId) && NamePattern.matches(this.artifact, artifactId);
    }
}
