package eu.ciechanowiec.airness.governance;

import java.util.Map;

/**
 * A finding as classified by the vulnerability scanner, including its recorded exception.
 *
 * @param identity advisory identifier
 * @param severity scanner severity
 * @param status   unresolved, suppressed or below threshold
 * @param evidence affected artifact, module, description, and exception details
 */
public record ReportAdvisory(String identity, String severity, String status, Map<String, String> evidence) {

    public ReportAdvisory {
        evidence = Map.copyOf(evidence);
    }
}
