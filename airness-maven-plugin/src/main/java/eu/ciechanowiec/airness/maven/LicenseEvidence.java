package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    String description() {
        return this.reason + "\nSupplier declaration: "
            + (this.declared.isEmpty() ? "Missing" : String.join(" OR ", this.declared))
            + "\n" + this.texts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "\n" + entry.getKey() + "\n" + LicenseMetadata.resource(entry.getValue()))
                .collect(Collectors.joining());
    }
}
