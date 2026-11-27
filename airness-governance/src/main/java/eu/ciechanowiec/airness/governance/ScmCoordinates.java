package eu.ciechanowiec.airness.governance;

/**
 * Where a project's source is kept, as its pom states it. A value the pom does not state is empty.
 *
 * @param url                 the address a person browses the source at
 * @param connection          the read-only address a tool clones from
 * @param developerConnection the address a developer pushes to
 */
public record ScmCoordinates(String url, String connection, String developerConnection) {
}
