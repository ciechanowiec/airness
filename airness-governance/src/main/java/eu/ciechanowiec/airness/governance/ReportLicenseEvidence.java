package eu.ciechanowiec.airness.governance;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The upstream release evidence Airness verified for an artifact whose supplier metadata declares no license, as
 * the report prints it: each fact apart, then each retained text.
 *
 * <p>It travels in a package's notices under a key starting with {@link ReportPackage#LICENSE_EVIDENCE_PREFIX}, so
 * the build-tool inventory a plugin goal writes carries it unchanged. {@link #encode()} and {@link #decode(String)}
 * are the only writer and reader of that value.
 *
 * @param license  the verified license
 * @param sha256   the checksum of the verified artifact bytes
 * @param declared what the supplier's metadata declares, empty when it declares nothing
 * @param reason   why the evidence supplements the supplier's metadata
 * @param texts    each retained text by its label
 */
public record ReportLicenseEvidence(
    String license, String sha256, List<String> declared, String reason, Map<String, String> texts
) {

    private static final char FIELD = '\u001F';
    private static final char TEXT = '\u001E';
    private static final int FACTS = 4;

    public ReportLicenseEvidence {
        declared = List.copyOf(declared);
        texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    /**
     * Writes the evidence as one notice value.
     *
     * @return the value {@link #decode(String)} reads
     */
    public String encode() {
        String facts = String.join(
            String.valueOf(FIELD), this.license, this.sha256, String.join(" OR ", this.declared), this.reason
        );
        return facts + this.texts.entrySet().stream().map(entry -> TEXT + entry.getKey() + FIELD + entry.getValue())
            .collect(Collectors.joining());
    }

    /**
     * Reads a notice value {@link #encode()} wrote.
     *
     * @param value the notice value
     * @return the evidence, or empty when the value is no encoded evidence
     */
    public static Optional<ReportLicenseEvidence> decode(String value) {
        String[] parts = value.split(String.valueOf(TEXT), -1);
        String[] facts = parts[0].split(String.valueOf(FIELD), -1);
        if (facts.length != FACTS) {
            return Optional.empty();
        }
        List<String> declared = facts[2].isEmpty() ? List.of() : List.of(facts[2].split(" OR ", -1));
        return texts(Arrays.asList(parts).subList(1, parts.length))
            .map(texts -> new ReportLicenseEvidence(facts[0], facts[1], declared, facts[3], texts));
    }

    // Each text is its label and its words, apart. A text without the separator is no encoded evidence.
    private static Optional<Map<String, String>> texts(List<String> parts) {
        if (parts.stream().anyMatch(part -> part.indexOf(FIELD) < 0)) {
            return Optional.empty();
        }
        Map<String, String> texts = new LinkedHashMap<>();
        parts.forEach(
            part -> texts.put(part.substring(0, part.indexOf(FIELD)), part.substring(part.indexOf(FIELD) + 1))
        );
        return Optional.of(texts);
    }
}
