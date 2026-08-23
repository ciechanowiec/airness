package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The dependencies the packages of one module declare on each other, and the cycles among them.
 *
 * <p>A cycle is invisible from inside any one file on it. Every import along the way looks ordinary,
 * and the loop only exists in the sum, which is why nobody finds one by reading. What a cycle costs is
 * paid later: the packages on it can no longer be understood, changed, tested, or moved apart from one
 * another, so the smallest unit of the design quietly becomes their union.
 *
 * <p>Only packages the module itself declares are nodes. An import of a package outside the scanned set
 * is dropped rather than recorded against a node that does not exist, so a dependency on a library is
 * not mistaken for a dependency inside the design.
 *
 * <p>An import is credited to the longest declared package that prefixes it. That resolves a nested
 * type and a static member import to the package that really owns them, where taking the text up to the
 * last dot would invent a package named after the enclosing type and lose the edge.
 */
final class PackageGraph {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");
    private static final String ARROW = " -> ";
    private static final char DOT = '.';

    private final Map<String, Set<String>> edges;

    private PackageGraph(Map<String, Set<String>> edges) {
        this.edges = Map.copyOf(edges);
    }

    /**
     * Builds the graph the given sources declare.
     *
     * @param sources the Java sources to read
     * @return the graph
     */
    static PackageGraph over(Collection<Path> sources) {
        Map<String, Set<String>> imports = sources.stream()
            .map(PackageGraph::declarationsOf)
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, PackageGraph::joined, TreeMap::new));
        Collection<String> declared = imports.keySet();
        return new PackageGraph(
            imports.entrySet().stream().collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> resolved(entry.getKey(), entry.getValue(), declared),
                    PackageGraph::joined,
                    TreeMap::new
                )
            )
        );
    }

    private static Set<String> joined(Collection<String> first, Collection<String> second) {
        return Stream.concat(first.stream(), second.stream())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * How many packages the graph holds, which a caller refuses when it is zero.
     *
     * @return the number of declared packages
     */
    int size() {
        return this.edges.size();
    }

    /**
     * One rendered cycle per group of packages that lie on one, shortest first through each package.
     *
     * <p>A package already named by a reported cycle is not made the start of another, so a knot of
     * several packages is reported once as the loop a reader has to break rather than once per member.
     *
     * @return the cycles, each rendered as the path back to where it started
     */
    List<String> cycles() {
        Map<String, Set<String>> core = this.cyclicCore();
        Collection<String> reported = new TreeSet<>();
        Collection<String> found = new ArrayList<>();
        core.keySet().stream()
            .sorted()
            .filter(node -> !reported.contains(node))
            .forEach(node -> shortestCycle(node, core).ifPresent(cycle -> mark(cycle, reported, found)));
        return List.copyOf(found);
    }

    private static void mark(List<String> cycle, Collection<String> reported, Collection<String> found) {
        reported.addAll(cycle);
        found.add(String.join(ARROW, cycle) + ARROW + cycle.getFirst());
    }

    /*
     * Drops every package with no remaining dependency on another, over and over until nothing more
     * drops. What survives holds every package on a cycle, because a package on one always depends on
     * the next package along it, so no round can ever drop it.
     */
    private Map<String, Set<String>> cyclicCore() {
        Map<String, Set<String>> remaining = this.edges;
        Map<String, Set<String>> pruned = pruned(remaining);
        while (pruned.size() < remaining.size()) {
            remaining = pruned;
            pruned = pruned(remaining);
        }
        return pruned;
    }

    private static Map<String, Set<String>> pruned(Map<String, Set<String>> graph) {
        Set<String> live = graph.keySet();
        return graph.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), inside(entry.getValue(), live)))
            .filter(entry -> !entry.getValue().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, PackageGraph::joined, TreeMap::new));
    }

    private static Set<String> inside(Collection<String> targets, Set<String> live) {
        return targets.stream().filter(live::contains).collect(Collectors.toCollection(TreeSet::new));
    }

    /*
     * Breadth-first over paths rather than over nodes, so the path itself comes back and no map of
     * predecessors has to be kept to rebuild one. The first path that can step back to where it started
     * is the shortest cycle through that package, because breadth-first reaches every shorter one first.
     */
    private static Optional<List<String>> shortestCycle(String start, Map<String, Set<String>> core) {
        Deque<List<String>> queue = new ArrayDeque<>(List.of(List.of(start)));
        Set<String> seen = new TreeSet<>(Set.of(start));
        while (!queue.isEmpty()) {
            List<String> path = queue.removeFirst();
            Set<String> next = core.getOrDefault(path.getLast(), Set.of());
            if (next.contains(start)) {
                return Optional.of(path);
            }
            next.stream().filter(seen::add).forEach(node -> queue.addLast(extended(path, node)));
        }
        return Optional.empty();
    }

    private static List<String> extended(Collection<String> path, String node) {
        return Stream.concat(path.stream(), Stream.of(node)).toList();
    }

    private static Optional<Map.Entry<String, Set<String>>> declarationsOf(Path source) {
        Optional<String> code = Repository.readText(source).map(JavaCode::blanked);
        return code.flatMap(PackageGraph::declaredPackage)
            .map(owner -> Map.entry(owner, code.map(PackageGraph::importedTypes).orElseGet(Set::of)));
    }

    private static Optional<String> declaredPackage(CharSequence code) {
        return PACKAGE.matcher(code).results().map(hit -> hit.group(1)).findFirst();
    }

    private static Set<String> importedTypes(CharSequence code) {
        return IMPORT.matcher(code).results()
            .map(hit -> hit.group(1))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> resolved(String owner, Collection<String> fqns, Collection<String> declared) {
        return fqns.stream()
            .map(fqn -> owningPackage(fqn, declared))
            .flatMap(Optional::stream)
            .filter(target -> !target.equals(owner))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Optional<String> owningPackage(String fqn, Collection<String> declared) {
        String candidate = fqn;
        while (candidate.indexOf(DOT) >= 0) {
            candidate = candidate.substring(0, candidate.lastIndexOf(DOT));
            if (declared.contains(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
