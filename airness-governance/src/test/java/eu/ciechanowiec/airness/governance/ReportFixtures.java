package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Small evaluated reports the report tests render.
 */
@UtilityClass
final class ReportFixtures {

    static final String ROOT_MODULE = "root";
    static final String MIT = "MIT";
    static final String DIRECT = "Direct";
    static final String COMMIT = "a12345678901234567890123456789012345678901";
    static final ReportLicensePolicy POLICY = new ReportLicensePolicy(
        Set.of(MIT, "MPL-2.0"),
        Map.of("MIT License", MIT)
    );

    static ReportAdvisory open(String identity, String severity) {
        return new ReportAdvisory(
            identity, severity, "pkg:maven/group/library@1?type=jar", List.of(ROOT_MODULE),
            "A parser overflows. It needs a nested input.", Optional.empty()
        );
    }

    static ReportAdvisory mitigated(String identity, String severity, boolean harness) {
        return new ReportAdvisory(
            identity, severity, "pkg:maven/group/library@1?type=jar", List.of(ROOT_MODULE),
            "A parser overflows. It needs a nested input.", Optional.of(
                ReportMitigation.of(
                    "Added 2026-10-13. The parser is absent. Mitigation: Removed with the next upgrade.", harness
                )
            )
        );
    }

    static ReportPackage dependency(String license) {
        return new ReportPackage(
            "group:library:jar:1", List.of(license),
            List.of(new ReportUse(ROOT_MODULE, "compile", DIRECT)), Map.of()
        );
    }

    static ComplianceReport report(List<ReportPackage> packages, List<ReportAdvisory> advisories) {
        return new ComplianceReport(
            "sample", new ReportSource(
                COMMIT, "fingerprint", "2026-10-13T12:00:00Z",
                List.of("Uncommitted sources")
            ), new ReportInventory(packages, advisories, POLICY),
            new ReportEvidence("{}", "{\"sbom\":\"{}\",\"csv\":\"header\"}", Map.of("SBOM SHA-256", "digest"))
        );
    }
}
