package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * The packages of a module depend on one another in one direction only.
 *
 * <p>Production and test sources are read together, because a test package that depends on the package
 * it tests is the ordinary arrangement and a production package that depends back on a test package is
 * the defect the rule is for. Reading only one of the two would leave that edge with nowhere to appear.
 *
 * <p>The rule is reported per module. A cycle that crossed two modules would need each of them to
 * depend on the other, which the build tool refuses before this check ever runs, so the module is the
 * whole of the scope in which a cycle can exist.
 */
public final class PackageCycleCheck {

    private static final String ACYCLIC
        = "Packages depend on one another in a loop, so none of them can be read, changed or moved alone";

    private final PackageGraph graph;
    private final int sources;

    /**
     * Reads the sources and builds the graph they declare.
     *
     * @param root        the working tree root
     * @param sourceRoots the directories whose Java sources are read
     */
    public PackageCycleCheck(Path root, Collection<Path> sourceRoots) {
        List<Path> found = JavaSources.under(root, sourceRoots);
        this.sources = found.size();
        this.graph = PackageGraph.over(found);
    }

    /**
     * How many sources the check read, which a caller refuses when it is zero.
     *
     * @return the number of Java sources in scope
     */
    public int scanned() {
        return this.sources;
    }

    /**
     * How many packages those sources declared, which a report names so a scope of one package reads as
     * the trivially acyclic case it is rather than as a clean graph.
     *
     * @return the number of declared packages
     */
    public int packages() {
        return this.graph.size();
    }

    /**
     * The one rule, with one offence per cycle.
     *
     * @return the verdict
     */
    public List<Findings> findings() {
        return List.of(new Findings(ACYCLIC, this.graph.cycles()));
    }
}
