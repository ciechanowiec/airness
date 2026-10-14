package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportPackage;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;

/**
 * Requires complete current-invocation evidence before a module enters the aggregate report.
 */
final class ReportInputs {

    private static final String FORMAT_FIELD = "bomFormat";
    private static final String FORMAT = "CycloneDX";

    private final Instant started;

    ReportInputs(Instant started) {
        this.started = started;
    }

    @SneakyThrows
    ReportModule read(MavenProject project) {
        Path build = Path.of(project.getBuild().getDirectory());
        Map<String, List<String>> licenses = ReportLicenses.read(
            build.resolve("airness/compliance/licenses.json"),
            this.started
        );
        List<ReportPackage> dependencies = MavenReportArtifacts.dependencies(project, licenses);
        Path bom = build.resolve("bom.json");
        JsonNode parsed = ReportJson.read(bom);
        ReportLicenses.current(
            Files.getLastModifiedTime(bom, LinkOption.NOFOLLOW_LINKS).toInstant().toString(), this.started
        );
        String sbom = Files.readString(bom);
        if (!FORMAT.equals(parsed.path(FORMAT_FIELD).asText())) {
            throw new IllegalStateException("Invalid CycloneDX evidence for " + project.getId());
        }
        ReportSbom.validate(parsed, dependencies);
        List<ReportPackage> packages = Stream.concat(dependencies.stream(), this.tools(project, build).stream())
            .toList();
        Path scan = build.resolve("dependency-check-report.json");
        List<ReportAdvisory> advisories = ReportVulnerabilities.read(scan, this.started, project.getId());
        return new ReportModule(
            project.getId(), packages, advisories, Map.of(
                "SBOM", sbom,
                "SBOM SHA-256", ReportJson.hash(sbom), "Scanner SHA-256", ReportJson.hash(Files.readString(scan)),
                "Scan date", ReportJson.read(scan).path("projectInfo").path("reportDate").asText(),
                "Graph", this.graph(project, build)
            )
        );
    }

    private String graph(MavenProject project, Path build) {
        Path file = build.resolve("airness/plugin-inputs/report-inventory.json");
        Stream<Map.Entry<String, List<String>>> tools = Files.exists(file)
            ? ReportJson.read(file).path("dependencies").properties().stream().map(
                entry -> Map.entry(
                    entry.getKey(),
                    entry.getValue().valueStream().map(JsonNode::asText).toList()
                )
            ) : Stream.empty();
        return ReportJson.encode(
            Stream.concat(ReportGraph.project(project).entrySet().stream(), tools)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportGraph::union))
        );
    }

    @SneakyThrows
    private List<ReportPackage> tools(MavenProject project, Path build) {
        Path path = build.resolve("airness/plugin-inputs/report-inventory.json");
        boolean required = project.getBuildPlugins().stream().flatMap(plugin -> plugin.getExecutions().stream())
            .anyMatch(execution -> execution.getGoals().contains("plugin-inputs"));
        if (required && !Files.exists(path)) {
            throw new IllegalStateException("Missing current build-tool license inventory: " + path);
        }
        if (!Files.exists(path)) {
            return List.of();
        }
        JsonNode document = ReportJson.read(path);
        ReportLicenses.current(ReportJson.required(document, "generatedAt"), this.started);
        JsonNode packages = document.path("packages");
        if (!packages.isArray()) {
            throw new IllegalStateException("Malformed build-tool license inventory: " + path);
        }
        return List.copyOf(Arrays.asList(new ObjectMapper().treeToValue(packages, ReportPackage[].class)));
    }
}
