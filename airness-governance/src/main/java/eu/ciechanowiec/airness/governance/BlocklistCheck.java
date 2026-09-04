package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Nothing a module reaches is software Airness refuses by name: not a coordinate its pom declares, not
 * an artifact Maven resolved for it, and not an image a Testcontainers literal in its sources names.
 *
 * <p>The licence allowlist reads what a pom says about itself, and this reads what the table in
 * {@link BlocklistEntries} says about the name. Both are needed, because the licence that decides is
 * often one the pom never states: the licence of the only server a driver can reach, of a binary a
 * package downloads, or of an image, which has no pom at all.
 *
 * <p>The resolved set is read beside the declarations rather than instead of them. A refused driver
 * three levels under a starter is found only in the resolved set, and a refused plugin or a declaration
 * in an inactive profile is found only in the raw pom. The repository-wide files, the Dockerfiles and
 * compose files and workflows, are read once by {@link RepositoryBlocklistCheck} rather than once per
 * module.
 */
public final class BlocklistCheck {

    private static final String HEADLINE = "Software the module reaches that Airness refuses by name";
    private static final String RESOLVED = " (resolved set)";

    private final int scanned;
    private final List<String> offences;

    /**
     * Reads the module once, so the coordinates and the sources are judged from one pass.
     *
     * @param root        the working tree root
     * @param pom         the raw pom of the module
     * @param sourceRoots the Java source directories of the module, main and test alike
     * @param coordinates what the module declares and what Maven resolved for it
     */
    public BlocklistCheck(Path root, Path pom, Collection<Path> sourceRoots, ModuleCoordinates coordinates) {
        List<Path> sources = JavaSources.under(root, sourceRoots);
        String location = root.relativize(pom).toString();
        this.scanned = coordinates.declared().size() + coordinates.resolved().size() + sources.size();
        this.offences = Stream.of(
            refusals(coordinates.declared(), location),
            refusals(coordinates.resolved(), location + RESOLVED),
            sources.stream().flatMap(source -> literals(root, source))
        ).flatMap(stream -> stream).distinct().toList();
    }

    /**
     * How many subjects the check read, which a caller logs so the reach of a clean verdict is on the
     * record.
     *
     * @return the number of declarations, resolved artifacts, and sources read
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every refused coordinate and image the module reaches.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static Stream<String> refusals(Collection<DeclaredCoordinate> coordinates, String location) {
        return coordinates.stream()
            .map(Blocklist::coordinate)
            .flatMap(Optional::stream)
            .map(refusal -> refusal.at(location));
    }

    private static Stream<String> literals(Path root, Path source) {
        String relative = root.relativize(source).toString();
        return Repository.readText(source).stream()
            .flatMap(text -> JavaImageLiterals.in(text).stream())
            .flatMap(
                literal -> Blocklist.judgeImage(literal.value())
                    .map(refusal -> refusal.at(relative + ':' + literal.line()))
                    .stream()
            );
    }
}
