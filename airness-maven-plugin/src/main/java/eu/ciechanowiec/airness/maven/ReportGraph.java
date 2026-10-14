package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;

/**
 * Dependency edges from Maven's resolved trails and actual plugin dependency nodes.
 */
@UtilityClass
final class ReportGraph {

    private static final Set<String> SCOPES = Set.of("compile", "provided", "runtime", "test", "system");

    static Map<String, List<String>> project(MavenProject project) {
        Stream<Map.Entry<String, String>> edges = MavenReportArtifacts.resolved(project).stream()
            .flatMap(artifact -> trail(Optional.ofNullable(artifact.getDependencyTrail()).orElseGet(List::of)));
        return edges.collect(
            Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(
                    Map.Entry::getValue, Collectors.collectingAndThen(
                        Collectors.toSet(),
                        values -> values.stream().sorted().toList()
                    )
                )
            )
        );
    }

    static Map<String, List<String>> plugin(DependencyNode node) {
        return nodes(node, Set.of()).collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, Map.Entry::getValue,
                ReportGraph::union
            )
        );
    }

    static List<String> union(Collection<String> first, Collection<String> second) {
        return Stream.concat(first.stream(), second.stream()).distinct().sorted().toList();
    }

    static Map<String, List<String>> selected(Map<String, List<String>> graph, Set<String> resolved) {
        return graph.entrySet().stream().filter(entry -> resolved.contains(entry.getKey()))
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().stream().filter(resolved::contains).toList()
                )
            );
    }

    static Map<String, List<String>> resolved(
        DependencyNode root, Collection<Artifact> artifacts
    ) {
        Set<String> identities = artifacts.stream().map(RepositoryUtils::toArtifact).map(MavenReportArtifacts::identity)
            .collect(Collectors.toUnmodifiableSet());
        return selected(plugin(root), identities);
    }

    private static Stream<Map.Entry<String, String>> trail(List<String> names) {
        List<String> trail = names.stream()
            .map(ReportGraph::coordinate).toList();
        return IntStream.range(1, trail.size()).mapToObj(index -> Map.entry(trail.get(index - 1), trail.get(index)));
    }

    private static String coordinate(String trail) {
        int last = trail.lastIndexOf(':');
        return SCOPES.contains(trail.substring(last + 1)) ? trail.substring(0, last) : trail;
    }

    private static Stream<Map.Entry<String, List<String>>> nodes(DependencyNode node, Set<String> visited) {
        Optional<String> identity = Optional.ofNullable(node.getArtifact())
            .map(RepositoryUtils::toArtifact).map(MavenReportArtifacts::identity);
        if (identity.isPresent() && visited.contains(identity.orElseThrow())) {
            return Stream.empty();
        }
        Set<String> following = Stream.concat(visited.stream(), identity.stream()).collect(
            Collectors.toUnmodifiableSet()
        );
        List<String> children = node.getChildren().stream().map(DependencyNode::getArtifact)
            .flatMap(artifact -> Optional.ofNullable(artifact).stream())
            .map(RepositoryUtils::toArtifact).map(MavenReportArtifacts::identity).distinct().sorted().toList();
        return Stream.concat(
            identity.stream().map(value -> Map.entry(value, children)),
            node.getChildren().stream().flatMap(child -> nodes(child, following))
        );
    }
}
