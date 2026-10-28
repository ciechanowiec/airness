package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportUse;
import eu.ciechanowiec.airness.maven.NpmEvidence.NpmPackage;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;

/**
 * The npm half of one module's compliance evidence, read beside its Maven half.
 *
 * <p>Every declared node project contributes its packages to the license inventory, judged by the same
 * allowlist as a Maven artifact, and its CycloneDX document to the aggregate SBOM, which the closing step
 * merges with the Maven one. A package limited to other platforms is in the document but not in the license
 * inventory, because this host did not install it and so read no license for it. Its license is judged where
 * it installs. The Node and pnpm that ran the gates are listed as the build tools they are.
 *
 * @param packages   the npm packages and the toolchain, as report packages
 * @param graph      the edges from the module to each node project's root
 * @param documents  the npm CycloneDX documents, encoded as one JSON array
 * @param advisories the known vulnerabilities OSV lists for the npm packages
 */
record NpmReportInputs(
    List<ReportPackage> packages, Map<String, List<String>> graph, String documents, List<ReportAdvisory> advisories
) {

    /**
     * The evidence key under which a module carries its npm documents.
     */
    static final String DOCUMENTS = "npm SBOM";

    private static final String NPM = "npm";
    private static final String BUILD = "build";

    /**
     * Copies every collection, so the evidence cannot change after it was read.
     *
     * @param packages   the npm packages and the toolchain
     * @param graph      the module edges
     * @param documents  the encoded documents
     * @param advisories the npm advisories
     */
    NpmReportInputs {
        packages = List.copyOf(packages);
        graph = Map.copyOf(graph);
        advisories = List.copyOf(advisories);
    }

    /**
     * Reads the npm evidence of every node project a module declares.
     *
     * @param project the module
     * @param started when this Maven invocation started, which every document must postdate
     * @param osv     the vulnerability database every resolved npm package is looked up in
     * @return the evidence, empty for a module that declares no node project
     * @throws IllegalStateException when a node project's inventory or SBOM is missing, malformed or stale, or
     *                               the vulnerability database cannot be read
     */
    static NpmReportInputs read(MavenProject project, Instant started, OsvScan osv) {
        List<NpmEvidence> evidence = NpmEvidence.of(project);
        if (evidence.isEmpty()) {
            return new NpmReportInputs(List.of(), Map.of(), "[]", List.of());
        }
        List<JsonNode> documents = evidence.stream().map(entry -> current(entry, started)).toList();
        List<String> roots = documents.stream()
            .map(document -> document.path("metadata").path("component").path("bom-ref").asText())
            .filter(root -> !root.isEmpty()).distinct().sorted().toList();
        List<ReportPackage> packages = Stream.concat(
            evidence.stream().flatMap(entry -> entry.packages().stream()).filter(NpmPackage::installed)
                .map(entry -> npm(entry, project.getId())),
            NodeToolchain.pinned().packages(project.getId()).stream()
        ).toList();
        return new NpmReportInputs(
            packages, roots.isEmpty() ? Map.of() : Map.of(project.getId(), roots), ReportJson.encode(documents),
            advisories(evidence, osv, project.getId())
        );
    }

    private static List<ReportAdvisory> advisories(List<NpmEvidence> evidence, OsvScan osv, String module) {
        Map<NpmEvidence, Set<String>> packages = evidence.stream().collect(
            Collectors.toUnmodifiableMap(
                entry -> entry, entry -> entry.packages().stream().map(NpmPackage::purl).collect(
                    Collectors.toUnmodifiableSet()
                )
            )
        );
        List<OsvAdvisory> findings = osv.findings(packages.values().stream().flatMap(Set::stream).toList(), module);
        return evidence.stream().flatMap(
            entry -> NpmMitigations.read(entry.directory()).apply(
                findings.stream().filter(
                    finding -> Objects.requireNonNull(packages.get(entry)).contains(finding.advisory().artifact())
                ).toList()
            ).stream()
        ).toList();
    }

    /**
     * Appends the npm half to a module's packages.
     *
     * @param packages the Maven packages
     * @return the module's packages followed by these
     */
    List<ReportPackage> packages(List<ReportPackage> packages) {
        return Stream.concat(packages.stream(), this.packages.stream()).toList();
    }

    /**
     * Merges these edges into a module's graph.
     *
     * @param graph the Maven graph
     * @return the union of both graphs
     */
    Map<String, List<String>> graph(Map<String, List<String>> graph) {
        return Stream.concat(graph.entrySet().stream(), this.graph.entrySet().stream()).collect(
            Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, ReportGraph::union)
        );
    }

    private static ReportPackage npm(NpmPackage entry, String module) {
        return new ReportPackage(
            entry.purl(), SpdxLicenses.alternatives(entry.license()),
            List.of(new ReportUse(module, NPM, entry.direct() ? "direct" : "transitive")), Map.of()
        );
    }

    @SneakyThrows
    private static JsonNode current(NpmEvidence entry, Instant started) {
        JsonNode document = entry.sbom();
        ReportLicenses.current(
            Files.getLastModifiedTime(entry.results().resolve(NpmEvidence.SBOM), LinkOption.NOFOLLOW_LINKS)
                .toInstant().toString(),
            started
        );
        return document;
    }

    /**
     * Why a report over these modules cannot be a final one on account of their npm evidence.
     *
     * <p>A node project whose {@code wiring} gate failed, which only a report-only build gets past, wrote its
     * inventory from a harness nothing vouched for.
     *
     * @param projects the modules the report covers
     * @return the qualifications, empty when no module declares a node project
     */
    static List<String> qualifications(Collection<MavenProject> projects) {
        List<NpmEvidence> evidence = projects.stream().flatMap(project -> NpmEvidence.of(project).stream()).toList();
        Stream<String> unwired = evidence.stream().filter(NpmEvidence::wiringFailed).map(
            entry -> entry.subject() + " wrote its npm evidence past a failing wiring gate, so it describes packages"
                + " the harness does not vouch for."
        );
        return unwired.toList();
    }

    /**
     * The use a build tool of the node projects records.
     *
     * @param module the module whose node projects it ran in
     * @return the use
     */
    static ReportUse tool(String module) {
        return new ReportUse(module, BUILD, "direct");
    }
}
