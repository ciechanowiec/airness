package eu.ciechanowiec.airness.governance;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An artifact's coordinate as a reader reads it: the package and its version apart, whatever ecosystem wrote it.
 *
 * <p>A Maven coordinate {@code group:artifact:type[:classifier]:version} reads as {@code group:artifact}, with the
 * type when it is no plain jar and the classifier when there is one. A package URL reads as its namespace and name,
 * decoded, so {@code pkg:npm/%40scope/name@1.0.0} reads as {@code @scope/name} at {@code 1.0.0}.
 *
 * @param name    the package
 * @param version the version, empty when the coordinate names none
 */
record ReportCoordinate(String name, String version) {

    private static final Pattern PACKAGE_URL = Pattern.compile("^pkg:([a-z0-9.+-]+)/([^@?#]+)(?:@([^?#]*))?");
    private static final String NPM_TYPE = "npm";
    private static final String MAVEN_TYPE = "maven";
    private static final String GENERIC_TYPE = "generic";
    private static final String PLAIN_TYPE = "jar";
    private static final int GROUP_AND_ARTIFACT = 2;
    private static final String SPACE = " ";

    /**
     * Reads a coordinate the inventory or a finding names.
     *
     * @param coordinate a Maven coordinate, a package URL, or any other name, such as a file name
     * @return the package and its version
     */
    static ReportCoordinate of(String coordinate) {
        Matcher address = PACKAGE_URL.matcher(coordinate);
        if (address.find()) {
            return packageUrl(address);
        }
        String[] parts = coordinate.split(":", -1);
        boolean maven = parts.length > GROUP_AND_ARTIFACT && !coordinate.contains(SPACE);
        return maven ? maven(parts) : new ReportCoordinate(coordinate, "");
    }

    private static ReportCoordinate packageUrl(Matcher address) {
        String path = URLDecoder.decode(address.group(2), StandardCharsets.UTF_8);
        String name = MAVEN_TYPE.equals(address.group(1)) ? path.replace('/', ':') : path;
        return new ReportCoordinate(name, Objects.requireNonNullElse(address.group(3), ""));
    }

    // group:artifact:type[:classifier]:version, where a short form group:artifact:version has a plain jar's type.
    private static ReportCoordinate maven(String... parts) {
        StringBuilder name = new StringBuilder(parts[0]).append(':').append(parts[1]);
        int last = parts.length - 1;
        String type = parts.length > GROUP_AND_ARTIFACT + 1 ? parts[2] : PLAIN_TYPE;
        if (parts.length > GROUP_AND_ARTIFACT + 2) {
            name.append(':').append(parts[GROUP_AND_ARTIFACT + 1]);
        }
        if (!PLAIN_TYPE.equals(type)) {
            name.append(" (").append(type).append(')');
        }
        return new ReportCoordinate(name.toString(), parts[last]);
    }

    /**
     * The ecosystem a coordinate belongs to, which the inventory groups packages by.
     *
     * @param coordinate a coordinate the inventory names
     * @return {@code npm} for an npm package, {@code maven} for a Maven artifact, and {@code other} for anything
     *         else, such as a pinned runtime
     */
    static String ecosystem(String coordinate) {
        Matcher address = PACKAGE_URL.matcher(coordinate);
        if (!address.find()) {
            return MAVEN_TYPE;
        }
        String type = address.group(1);
        return NPM_TYPE.equals(type) || MAVEN_TYPE.equals(type) ? type : GENERIC_TYPE;
    }
}
