package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * A stable numeric Docker tag and the syntax family it belongs to.
 */
record DockerTag(String raw, String prefix, String version, int levels, boolean calendar) {

    private static final Pattern VERSIONED = Pattern.compile("^(?<prefix>v?)(?<version>[0-9]+(?:[.][0-9]+)+)$");
    private static final int YEAR_LENGTH = 4;

    static Optional<DockerTag> from(String raw) {
        Matcher matched = VERSIONED.matcher(raw);
        if (!matched.matches()) {
            return Optional.empty();
        }
        String version = matched.group("version");
        String first = version.substring(0, version.indexOf('.'));
        return Optional.of(
            new DockerTag(
                raw, matched.group("prefix"), version, version.split("[.]").length,
                first.length() == YEAR_LENGTH && first.startsWith("20")
            )
        );
    }

    boolean sameScheme(DockerTag other) {
        return this.prefix.equals(other.prefix())
            && this.levels == other.levels()
            && this.calendar == other.calendar();
    }

    int compareVersion(DockerTag other) {
        return new ComparableVersion(this.version).compareTo(new ComparableVersion(other.version()));
    }
}
