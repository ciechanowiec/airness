package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * A base-image tag read as a version and the variant written after it, as {@code 26.13.1-alpine},
 * {@code 25.0.5_8-jre-alpine-3.24} or {@code 3.13.2-slim-bookworm} are.
 *
 * <p>A newer release of a base is the same variant at a higher version within the same major version: the
 * variant decides the distribution and what the image holds, and a new major version is a change somebody
 * decides on rather than a repair. A build number written after an underscore, as Temurin's {@code _8}, is part
 * of the version.
 *
 * @param raw     the tag as written
 * @param version the version, with any build number
 * @param variant everything after the version, empty for a bare version
 */
record VariantTag(String raw, String version, String variant) {

    private static final Pattern TAG = Pattern.compile(
        "^(?<version>v?[0-9]+(?:[.][0-9]+)*(?:_[0-9]+)?)(?<variant>(?:[-_][^0-9].*)?)$"
    );
    private static final Pattern MAJOR = Pattern.compile("^v?(?<major>[0-9]+)");
    private static final char LEVEL = '.';
    private static final char BUILD = '_';

    /**
     * Reads a tag that starts with a version.
     *
     * @param raw the tag
     * @return the tag, empty for one that starts with no version, such as {@code bookworm}
     */
    static Optional<VariantTag> from(String raw) {
        Matcher matched = TAG.matcher(raw);
        return matched.matches()
            ? Optional.of(new VariantTag(raw, matched.group("version"), matched.group("variant")))
            : Optional.empty();
    }

    /**
     * Whether the tag names one release rather than a line that moves, which a newer release can replace: a
     * version with a minor level or a build number, as {@code 26.8.2-alpine}, where {@code 26-alpine} moves.
     *
     * @return whether the version has more than its major level
     */
    boolean release() {
        return this.version.indexOf(LEVEL) >= 0 || this.version.indexOf(BUILD) >= 0;
    }

    /**
     * Whether the other tag is a newer release of the same variant within this tag's major version.
     *
     * @param other a tag the registry holds
     * @return whether it can replace this one as a repair
     */
    boolean replacedBy(VariantTag other) {
        return this.variant.equals(other.variant()) && this.major().equals(other.major())
            && this.version.startsWith("v") == other.version().startsWith("v") && this.compareVersion(other) < 0;
    }

    /**
     * Orders two tags by their versions alone.
     *
     * @param other the other tag
     * @return a negative number when this version is lower
     */
    int compareVersion(VariantTag other) {
        return comparable(this.version).compareTo(comparable(other.version()));
    }

    private String major() {
        Matcher matched = MAJOR.matcher(this.version);
        return matched.find() ? matched.group("major") : this.version;
    }

    private static ComparableVersion comparable(String version) {
        return new ComparableVersion(version.replace(BUILD, LEVEL));
    }
}
