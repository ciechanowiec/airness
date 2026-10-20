package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import eu.ciechanowiec.airness.governance.SuppressionDocument;
import eu.ciechanowiec.airness.governance.SuppressionNotes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads Dependency-Check's evaluated findings, including the exceptions it actually applied.
 */
@UtilityClass
final class ReportVulnerabilities {

    private static final String ERRORS = "analysisExceptions";
    private static final List<String> HARNESS_DOCUMENTS = List.of(
        "eclipse-suppressions.xml", "tool-suppressions.xml", "self-tool-suppressions.xml",
        "spring-tool-suppressions.xml"
    );

    static List<ReportAdvisory> read(Path file, Instant started, String module, Set<String> harness) {
        JsonNode document = ReportJson.read(file);
        JsonNode project = document.path("projectInfo");
        ReportLicenses.current(ReportJson.required(project, "reportDate"), started);
        JsonNode scan = document.path("scanInfo");
        JsonNode dependencies = document.path("dependencies");
        if (!dependencies.isArray() || !scan.path(ERRORS).isEmpty()) {
            throw new IllegalStateException("Incomplete vulnerability evidence: " + file);
        }
        return dependencies.valueStream().flatMap(dependency -> advisories(dependency, module, harness)).toList();
    }

    /**
     * The notes of every exception Airness ships, so a mitigation the harness answers for can be told
     * from one the project recorded. A module without the harness documents has none to tell apart.
     *
     * @param inputs the module's plugin-input directory
     * @return the normalized notes of every harness exception present
     */
    static Set<String> harnessNotes(Path inputs) {
        return HARNESS_DOCUMENTS.stream().map(inputs::resolve).filter(Files::isRegularFile)
            .flatMap(document -> new SuppressionDocument(document).notes().stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<ReportAdvisory> advisories(JsonNode dependency, String module, Set<String> harness) {
        String artifact = dependency.path("packages").valueStream().map(entry -> entry.path("id").asText())
            .findFirst().orElseGet(() -> ReportJson.required(dependency, "fileName"));
        return Stream.concat(
            dependency.path("vulnerabilities").valueStream().map(
                entry -> advisory(entry, artifact, module, Optional.empty())
            ),
            dependency.path("suppressedVulnerabilities").valueStream().map(
                entry -> advisory(entry, artifact, module, Optional.of(mitigation(entry, harness)))
            )
        );
    }

    private static ReportMitigation mitigation(JsonNode entry, Set<String> harness) {
        String notes = entry.path("notes").asText();
        return ReportMitigation.of(notes, harness.contains(SuppressionNotes.normalized(notes)));
    }

    private static String severity(JsonNode entry) {
        JsonNode fourth = entry.path("cvssv4");
        JsonNode third = entry.path("cvssv3");
        JsonNode second = entry.path("cvssv2");
        return Stream.of(
            entry.path("severity"), fourth.path("baseSeverity"), third.path("baseSeverity"), second.path("severity")
        ).filter(JsonNode::isTextual).map(JsonNode::asText).filter(value -> !value.isBlank())
            .findFirst().orElse("Unknown");
    }

    private static ReportAdvisory advisory(
        JsonNode entry, String artifact, String module, Optional<ReportMitigation> mitigation
    ) {
        return new ReportAdvisory(
            ReportJson.required(entry, "name"), severity(entry), artifact, List.of(module),
            entry.path("description").asText(), mitigation
        );
    }
}
