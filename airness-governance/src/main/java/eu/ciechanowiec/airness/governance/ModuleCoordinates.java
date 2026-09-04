package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The coordinates one module reaches: those its raw pom declares, and those Maven resolved for it.
 *
 * <p>The two are kept apart because they answer to different locations in a report. A declared
 * coordinate is repaired where it is written, and the raw pom is read for it so a declaration in an
 * inactive profile or a management section is found where the effective model would have dropped it. A
 * resolved coordinate may have arrived through a starter three levels up, and the report says so by
 * naming the resolved set rather than a line.
 *
 * @param declared what the raw pom declares, with its build extensions
 * @param resolved what Maven resolved for the module, in a stable order
 */
public record ModuleCoordinates(List<DeclaredCoordinate> declared, List<DeclaredCoordinate> resolved) {

    /**
     * Makes defensive copies so a caller cannot alter the sets after the check read them.
     *
     * @param declared what the raw pom declares
     * @param resolved what Maven resolved
     */
    public ModuleCoordinates {
        declared = List.copyOf(declared);
        resolved = List.copyOf(resolved);
    }

    /**
     * Reads a module's declarations out of its pom and pairs them with what Maven resolved.
     *
     * @param pom        the raw pom of the module
     * @param properties the effective properties of the module, for a version written as one
     * @param resolved   the artifacts Maven resolved for the module, in any order
     * @return the module's coordinates
     */
    public static ModuleCoordinates of(
        Path pom, Map<String, String> properties, Collection<DeclaredCoordinate> resolved
    ) {
        List<DeclaredCoordinate> declared = Stream.concat(
            DeclaredCoordinates.from(pom, properties).stream(), MavenExtensions.in(pom).stream()
        ).distinct().toList();
        List<DeclaredCoordinate> ordered = resolved.stream()
            .distinct()
            .sorted(Comparator.comparing(DeclaredCoordinate::groupId).thenComparing(DeclaredCoordinate::artifactId))
            .toList();
        return new ModuleCoordinates(declared, ordered);
    }
}
