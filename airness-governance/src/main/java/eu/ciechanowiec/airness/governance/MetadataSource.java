package eu.ciechanowiec.airness.governance;

/**
 * Where one metadata field of a module's effective model comes from: the nearest pom up its parent chain, itself
 * included, that declares the field.
 *
 * @param field      the field, such as {@code licenses} or {@code scm/url}
 * @param coordinate the {@code groupId:artifactId} of the pom that declares it
 * @param harness    whether that pom is one Airness publishes
 */
public record MetadataSource(String field, String coordinate, boolean harness) {
}
