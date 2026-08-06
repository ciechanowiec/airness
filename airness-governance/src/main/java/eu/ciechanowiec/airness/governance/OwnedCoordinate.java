package eu.ciechanowiec.airness.governance;

/**
 * A declared coordinate together with the project whose raw {@code pom.xml} owns it.
 *
 * @param owner      the owning project's Maven coordinates
 * @param coordinate the declared dependency, plugin, or parent
 */
public record OwnedCoordinate(String owner, DeclaredCoordinate coordinate) {
}
