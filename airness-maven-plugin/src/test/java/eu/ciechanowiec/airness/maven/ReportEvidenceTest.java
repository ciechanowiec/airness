package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportEvidenceTest {

    private static final Instant STARTED = Instant.parse("2026-10-13T12:00:00Z");

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void refusesSymbolicallyLinkedEvidenceAndNonTextualIdentityFields() {
        Path file = this.directory.resolve("evidence.json");
        ReportJson.write(file, Map.of("identity", 1));
        assertThrows(IllegalStateException.class, () -> ReportJson.required(ReportJson.read(file), "identity"));
        Path link = Files.createSymbolicLink(this.directory.resolve("linked.json"), file);
        assertThrows(IllegalStateException.class, () -> ReportJson.read(link));
    }

    @Test
    void readsTheCurrentLicenseChecksSupplierDeclarations() {
        Path file = this.directory.resolve("licenses.json");
        ReportJson.write(
            file, Map.of(
                "generatedAt", STARTED.toString(), "packages", List.of(
                    Map.of(
                        "group", "example", "artifact", "library", "version", "1.0", "declared", List.of("MIT License")
                    )
                )
            )
        );
        assertEquals(Map.of("example:library:1.0", List.of("MIT License")), ReportLicenses.read(file, STARTED));
        assertThrows(IllegalStateException.class, () -> ReportLicenses.read(file, STARTED.plusSeconds(1)));
    }

    @Test
    void refusesMissingOrMalformedLicenseInventories() {
        Path file = this.directory.resolve("licenses.json");
        assertThrows(IllegalStateException.class, () -> ReportLicenses.read(file, STARTED));
        ReportJson.write(file, Map.of("generatedAt", STARTED.toString()));
        assertThrows(IllegalStateException.class, () -> ReportLicenses.read(file, STARTED));
        ReportJson.write(
            file, Map.of(
                "generatedAt", STARTED.toString(), "packages", List.of(
                    Map.of(
                        "group", "example", "artifact", "library", "version", "1.0"
                    )
                )
            )
        );
        assertThrows(IllegalStateException.class, () -> ReportLicenses.read(file, STARTED));
        ReportJson.write(file, Map.of("generatedAt", STARTED.toString(), "packages", List.of(Map.of("group", ""))));
        assertThrows(IllegalStateException.class, () -> ReportLicenses.read(file, STARTED));
    }

    @Test
    void retainsBothUnresolvedAndActuallySuppressedScannerFindings() {
        Path file = this.directory.resolve("scan.json");
        Map<String, Object> dependency = Map.of(
            "fileName", "library.jar", "packages", List.of(Map.of("id", "pkg:maven/a/b@1")),
            "vulnerabilities", List.of(Map.of("name", "CVE-2026-1111", "severity", "HIGH")),
            "suppressedVulnerabilities", List.of(Map.of("name", "CVE-2026-2222", "notes", "2026-10-13: not reachable"))
        );
        ReportJson.write(file, scan(List.of(dependency)));
        List<ReportAdvisory> findings = ReportVulnerabilities.read(file, STARTED, "module");
        assertEquals(List.of("Unresolved", "Suppressed"), findings.stream().map(ReportAdvisory::status).toList());
        assertEquals("pkg:maven/a/b@1", findings.getFirst().evidence().get("Artifact"));
        assertTrue(findings.getLast().evidence().getOrDefault("Exception", "").contains("not reachable"));
    }

    @Test
    void readsSuppressedSeverityFromTheScannersNestedCvssEvidence() {
        Path file = this.directory.resolve("scan.json");
        List<Map<String, Object>> findings = List.of(
            Map.of("name", "CVE-2026-1111", "cvssv4", Map.of("baseSeverity", "CRITICAL")),
            Map.of("name", "CVE-2026-2222", "cvssv3", Map.of("baseSeverity", "HIGH")),
            Map.of("name", "CVE-2026-3333", "cvssv2", Map.of("severity", "MEDIUM"))
        );
        ReportJson.write(file, scan(List.of(Map.of("fileName", "library.jar", "suppressedVulnerabilities", findings))));
        assertEquals(
            List.of("CRITICAL", "HIGH", "MEDIUM"),
            ReportVulnerabilities.read(file, STARTED, "module").stream().map(ReportAdvisory::severity).toList()
        );
    }

    @Test
    void distinguishesAnEmptySuccessfulScanFromMissingOrFailedEvidence() {
        Path file = this.directory.resolve("scan.json");
        ReportJson.write(file, scan(List.of()));
        assertEquals(List.of(), ReportVulnerabilities.read(file, STARTED, "module"));
        ReportJson.write(file, Map.of("projectInfo", Map.of("reportDate", STARTED.toString())));
        assertThrows(IllegalStateException.class, () -> ReportVulnerabilities.read(file, STARTED, "module"));
        ReportJson.write(
            file, Map.of(
                "projectInfo", Map.of("reportDate", STARTED.toString()), "dependencies", List.of(),
                "scanInfo", Map.of("analysisExceptions", List.of("database unavailable"))
            )
        );
        assertThrows(IllegalStateException.class, () -> ReportVulnerabilities.read(file, STARTED, "module"));
    }

    @Test
    @SneakyThrows
    void escapesDownloadJsonAndRejectsNonObjectEvidence() {
        String encoded = ReportJson.encode(Map.of("text", "</script>&"));
        assertFalse(encoded.contains("</script>"));
        Path file = Files.writeString(this.directory.resolve("data.json"), encoded);
        JsonNode parsed = ReportJson.read(file);
        assertEquals("</script>&", ReportJson.required(parsed, "text"));
        Files.writeString(file, "[]");
        assertThrows(IllegalStateException.class, () -> ReportJson.read(file));
        Files.writeString(file, "null");
        assertThrows(IllegalStateException.class, () -> ReportJson.read(file));
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ReportJson.hash("")
        );
    }

    private static Map<String, Object> scan(List<Map<String, Object>> dependencies) {
        return Map.of(
            "projectInfo", Map.of("reportDate", STARTED.toString()), "scanInfo", Map.of(),
            "dependencies", dependencies
        );
    }
}
