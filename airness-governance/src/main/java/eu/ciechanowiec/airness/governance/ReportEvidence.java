package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Map;

/**
 * The downloadable evidence and the provenance facts printed beside it.
 *
 * @param sbom      exact aggregate CycloneDX JSON
 * @param downloads safely encoded JSON containing the SBOM and CSV downloads
 * @param facts     tool versions, evidence hashes, module list, and coverage details
 */
public record ReportEvidence(String sbom, String downloads, Map<String, String> facts) {

    private static final String SCRIPT_END = "</script";
    private static final String SCAN_DATE = " scan date";

    public ReportEvidence {
        facts = Map.copyOf(facts);
        if (downloads.toLowerCase(Locale.ROOT).contains(SCRIPT_END)) {
            throw new IllegalArgumentException("Report download JSON contains an unescaped script terminator");
        }
    }

    /**
     * When the vulnerability data the report states was read, which is the oldest module scan.
     *
     * @return the earliest module scan date, or empty when no module was scanned
     */
    public String scannedAt() {
        return this.facts.entrySet().stream().filter(entry -> entry.getKey().endsWith(SCAN_DATE))
            .map(Map.Entry::getValue).sorted().findFirst().orElse("");
    }
}
