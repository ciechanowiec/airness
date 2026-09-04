package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One image repository the blocklist refuses, and what a project pulls instead.
 *
 * <p>The tag decides only where the entry says it does. A floor names the first refused tag for a
 * repository that was once open, so an earlier tag stays with the licence allowlist. A refused tag
 * pattern refuses only the tags it names, for a repository whose default edition is open and whose
 * commercial edition is a tag suffix away. An allowed tag pattern exempts the tags it names, for a
 * repository publishing an open variant beside a default that is not.
 *
 * @param repository  the repository in the spelling {@link ImageReference} produces, with an optional
 *                    trailing asterisk
 * @param floor       the first refused tag, when the repository was once open
 * @param allowedTag  a tag pattern that stays allowed
 * @param refusedTag  a tag pattern that alone is refused
 * @param reason      why it is refused
 * @param replacement what to pull instead
 */
record BlockedImage(
    String repository, Optional<String> floor, Optional<Pattern> allowedTag, Optional<Pattern> refusedTag,
    String reason, String replacement
) {

    static BlockedImage of(String repository, String reason, String replacement) {
        return new BlockedImage(repository, Optional.empty(), Optional.empty(), Optional.empty(), reason, replacement);
    }

    BlockedImage from(String firstRefused) {
        return new BlockedImage(
            this.repository, Optional.of(firstRefused), this.allowedTag, this.refusedTag, this.reason, this.replacement
        );
    }

    BlockedImage allowing(String tagPattern) {
        return new BlockedImage(
            this.repository, this.floor, Optional.of(Pattern.compile(tagPattern)), this.refusedTag, this.reason,
            this.replacement
        );
    }

    BlockedImage refusing(String tagPattern) {
        return new BlockedImage(
            this.repository, this.floor, this.allowedTag, Optional.of(Pattern.compile(tagPattern)), this.reason,
            this.replacement
        );
    }

    boolean matches(String candidate) {
        return NamePattern.matches(this.repository, candidate);
    }
}
