package eu.ciechanowiec.airness.governance;

/**
 * A dependency, plugin, or parent coordinate the pom names directly, with its version resolved from
 * any local property reference. The version check compares this version against the latest stable
 * release on Maven Central.
 *
 * @param groupId    group identifier
 * @param artifactId artifact identifier
 * @param version    resolved version
 */
public record DeclaredCoordinate(String groupId, String artifactId, String version) {
}
