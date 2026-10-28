package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An OSV finding with the canonical identifier and aliases its database actually supplied.
 *
 * @param advisory    the report finding
 * @param identifiers the canonical identifier and its aliases
 */
record OsvAdvisory(ReportAdvisory advisory, Set<String> identifiers) {

    OsvAdvisory {
        identifiers = Set.copyOf(identifiers);
    }

    static OsvAdvisory of(JsonNode vulnerability, String purl, String module) {
        String identity = ReportJson.required(vulnerability, "id");
        String summary = vulnerability.path("summary").asText();
        String description = summary.isBlank()
            ? vulnerability.path("details").asText().lines().findFirst().orElse("") : summary;
        String severity = Optional.of(vulnerability.path("database_specific").path("severity").asText())
            .filter(text -> !text.isBlank()).orElse("Unknown");
        Set<String> identifiers = Stream.concat(
            Stream.of(identity), vulnerability.path("aliases").valueStream()
                .filter(JsonNode::isTextual).map(JsonNode::asText)
        ).collect(Collectors.toUnmodifiableSet());
        return new OsvAdvisory(
            new ReportAdvisory(identity, severity, purl, List.of(module), description, Optional.empty()), identifiers
        );
    }
}
