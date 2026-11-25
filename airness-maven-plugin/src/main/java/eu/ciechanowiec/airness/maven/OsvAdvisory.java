package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.SuppressionNotes;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An OSV finding with the canonical identifier and aliases its database actually supplied.
 *
 * @param advisory    the report finding
 * @param identifiers the canonical identifier and its aliases
 * @param fixed       whether the database names a version that fixes the package asked about
 */
record OsvAdvisory(ReportAdvisory advisory, Set<String> identifiers, boolean fixed) {

    private static final Pattern LEAD_IN = Pattern.compile("^(?:Issue|Problem) (?:summary|description):\\s*");

    OsvAdvisory {
        identifiers = Set.copyOf(identifiers);
    }

    static OsvAdvisory of(JsonNode vulnerability, String purl, String module) {
        return of(vulnerability, OsvQuery.purl(purl), module);
    }

    static OsvAdvisory of(JsonNode vulnerability, OsvQuery query, String module) {
        String identity = ReportJson.required(vulnerability, "id");
        String summary = vulnerability.path("summary").asText();
        String description = summary.isBlank() ? details(vulnerability.path("details").asText()) : summary;
        String severity = Optional.of(vulnerability.path("database_specific").path("severity").asText())
            .filter(text -> !text.isBlank()).or(() -> distribution(vulnerability)).or(() -> vector(vulnerability))
            .orElse("Unknown");
        // A distribution's advisory names the CVEs it answers as upstream advisories rather than as aliases.
        Set<String> identifiers = Stream.concat(
            Stream.of(identity), Stream.concat(
                vulnerability.path("aliases").valueStream(), vulnerability.path("upstream").valueStream()
            ).filter(JsonNode::isTextual).map(JsonNode::asText)
        ).collect(Collectors.toUnmodifiableSet());
        return new OsvAdvisory(
            new ReportAdvisory(
                identity, severity, query.artifact(), List.of(module), description, Optional.empty(),
                identifiers.stream().filter(name -> !name.equals(identity)).sorted().toList()
            ),
            identifiers, query.fixedIn(vulnerability)
        );
    }

    // An advisory without a summary describes itself in its details, wrapped at a fixed width as a terminal shows
    // it. The lines are joined into the text they wrap, and a lead-in such as "Issue summary:" is dropped, so the
    // first sentence reads whole.
    private static String details(String details) {
        String joined = SuppressionNotes.normalized(details);
        return LEAD_IN.matcher(joined).replaceFirst("");
    }

    // A distribution's tracker publishes a CVSS vector rather than a rating, and the vector determines the rating.
    private static Optional<String> vector(JsonNode vulnerability) {
        return vulnerability.path("severity").valueStream().filter(
            entry -> entry.path("type").asText().startsWith(
                "CVSS_V3"
            )
        ).map(entry -> entry.path("score").asText()).map(CvssV3::rating).flatMap(Optional::stream).findFirst();
    }

    // Ubuntu rates its own advisories, as low, medium, high or critical, under a severity of its own type.
    private static Optional<String> distribution(JsonNode vulnerability) {
        return vulnerability.path("severity").valueStream()
            .filter(entry -> "Ubuntu".equals(entry.path("type").asText()))
            .map(entry -> entry.path("score").asText().toUpperCase(Locale.ROOT)).filter(score -> !score.isBlank())
            .findFirst();
    }
}
