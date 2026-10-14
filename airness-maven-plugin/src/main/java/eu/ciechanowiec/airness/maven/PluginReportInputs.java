package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.maven.RepositoryUtils;
import org.eclipse.aether.artifact.Artifact;

/**
 * Publishes licenses for exactly the tool artifacts handed to the vulnerability scanner.
 */
@UtilityClass
final class PluginReportInputs {

    static void write(Path directory, Collection<PluginResolution> resolutions, MavenReportArtifacts reader) {
        List<ReportPackage> packages = resolutions.stream().flatMap(
            resolution -> resolution.artifacts().stream()
                .map(
                    artifact -> Map.entry(
                        artifact, new ReportUse(
                            resolution.module(), "build",
                            artifact.equals(resolution.root()) ? "Direct" : "Transitive"
                        )
                    )
                )
        ).collect(Collectors.groupingBy(Map.Entry::getKey)).entrySet().stream()
            .map(entry -> described(entry, reader)).toList();
        Map<String, List<String>> graph = resolutions.stream().flatMap(
            resolution -> resolution.dependencies().entrySet().stream()
        ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportGraph::union));
        ReportJson.write(
            directory.resolve("report-inventory.json"),
            Map.of("generatedAt", Instant.now().toString(), "packages", packages, "dependencies", graph)
        );
    }

    private static ReportPackage described(
        Map.Entry<Artifact, List<Map.Entry<Artifact, ReportUse>>> entry,
        MavenReportArtifacts reader
    ) {
        List<ReportUse> uses = entry.getValue().stream().map(Map.Entry::getValue).distinct().toList();
        ReportPackage initial = reader.tool(RepositoryUtils.toArtifact(entry.getKey()), uses.getFirst().module());
        return new ReportPackage(initial.coordinate(), initial.licenses(), uses, initial.notices());
    }
}
