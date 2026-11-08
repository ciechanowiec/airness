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
 * Requires complete current-invocation evidence before a module enters the aggregate report: the Maven
 * inventory, scan and SBOM, and the npm inventory and SBOM of every node project the module declares.
 */
final class ReportInputs {

    private final Instant started;
    private final OsvScan osv;

    ReportInputs(Instant started) {
        this(started, OsvScan.of(OsvScan.PUBLIC));
    }

    ReportInputs(Instant started, OsvScan osv) {
        this.started = started;
        this.osv = osv;
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
        if (!NpmEvidence.CYCLONEDX.equals(parsed.path(NpmEvidence.FORMAT).asText())) {
            throw new IllegalStateException("Invalid CycloneDX evidence for " + project.getId());
        }
        ReportSbom.validate(parsed, dependencies);
        NpmReportInputs npm = NpmReportInputs.read(project, this.started, this.osv);
        List<ReportPackage> packages = npm.packages(
            Stream.concat(dependencies.stream(), this.tools(project, build).stream()).toList()
        );
        Path scan = build.resolve("dependency-check-report.json");
        List<ReportAdvisory> advisories = ReportVulnerabilities.read(
            scan, this.started, project.getId(),
            ReportVulnerabilities.harnessNotes(build.resolve("airness/plugin-inputs"))
        );
        return new ReportModule(
            project.getId(), packages, Stream.concat(advisories.stream(), npm.advisories().stream()).toList(), Map.of(
                "SBOM", sbom,
                "SBOM SHA-256", ReportJson.hash(sbom), "Scanner SHA-256", ReportJson.hash(Files.readString(scan)),
                "Scan date", ReportJson.read(scan).path("projectInfo").path("reportDate").asText(),
                "Graph", ReportJson.encode(npm.graph(this.graph(project, build))),
                NpmReportInputs.DOCUMENTS, npm.documents()
            )
        );
    }

    private Map<String, List<String>> graph(MavenProject project, Path build) {
        Path file = build.resolve("airness/plugin-inputs/report-inventory.json");
        Stream<Map.Entry<String, List<String>>> tools = Files.exists(file)
            ? ReportJson.read(file).path("dependencies").properties().stream().map(
                entry -> Map.entry(
                    entry.getKey(),
                    entry.getValue().valueStream().map(JsonNode::asText).toList()
                )
            ) : Stream.empty();
        return Stream.concat(ReportGraph.project(project).entrySet().stream(), tools)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportGraph::union));
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
