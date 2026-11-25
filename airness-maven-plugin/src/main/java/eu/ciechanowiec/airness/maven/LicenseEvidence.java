package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Map;

/**
 * Release evidence retained independently of incomplete supplier POMs.
 *
 * @param declared exact declarations expected in the supplier POM
 * @param source   immutable upstream evidence location
 * @param reason   why the evidence supplements the supplier metadata
 * @param texts    bundled license and notice resource names
 */
record LicenseEvidence(List<String> declared, String source, String reason, Map<String, String> texts) {

    LicenseEvidence {
        declared = List.copyOf(declared);
        texts = Map.copyOf(texts);
    }
}
