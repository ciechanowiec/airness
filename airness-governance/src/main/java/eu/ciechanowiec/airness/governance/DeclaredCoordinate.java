package eu.ciechanowiec.airness.governance;

/**
 * A dependency or plugin coordinate the pom names directly, with its version resolved from any local
 * property reference. The freshness check compares this version against the latest stable release on
 * Maven Central.
 */
public record DeclaredCoordinate(String groupId, String artifactId, String version) {
}
