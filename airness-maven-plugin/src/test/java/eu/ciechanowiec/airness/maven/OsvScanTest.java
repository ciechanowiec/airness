package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The npm packages are looked up in OSV by their package URLs, every advisory it lists is open, and a database
 * that cannot be read fails the scan rather than passing it.
 */
class OsvScanTest {

    private static final String KIT = "pkg:npm/%40scope/kit@2.0.0";

    @Test
    void reportsEveryAdvisoryButAWithdrawnOne() {
        try (ArchiveServer server = NpmFixture.osv()) {
            List<ReportAdvisory> advisories = OsvScan.of(server.uri("/").toString()).findings(
                List.of("pkg:npm/left-pad@1.3.0", KIT, "pkg:npm/%40esbuild/linux-x64@0.25.0", KIT), "web"
            ).stream().map(OsvAdvisory::advisory).toList();
            assertEquals(1, advisories.size());
            ReportAdvisory advisory = advisories.getFirst();
            assertEquals(KIT, advisory.artifact());
            assertEquals("HIGH", advisory.severity());
            assertEquals("Prototype pollution", advisory.description());
            assertTrue(advisory.open(), "the raw scan retains every open advisory before project mitigations");
        }
    }

    @Test
    void readsTheDetailsWhenAnAdvisoryHasNoSummaryOrSeverity() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/v1/querybatch", bytes("{\"results\": [{\"vulns\": [{\"id\": \"OSV-1\"}]}]}"));
            server.serve("/v1/vulns/OSV-1", bytes("{\"id\": \"OSV-1\", \"details\": \"First line.\\nSecond.\"}"));
            ReportAdvisory advisory = OsvScan.of(server.uri("/").toString()).findings(List.of(KIT), "web").getFirst()
                .advisory();
            assertEquals("First line.", advisory.description());
            assertEquals("Unknown", advisory.severity());
        }
    }

    @Test
    void failsWhenOsvCannotBeReadOrAnswersWrongly() {
        IllegalStateException unreachable = assertThrows(
            IllegalStateException.class, () -> OsvScan.of("http://127.0.0.1:9/").findings(List.of(KIT), "web")
        );
        assertTrue(Objects.requireNonNull(unreachable.getMessage()).contains("rather than passing without it"));
        try (ArchiveServer server = new ArchiveServer()) {
            String base = server.uri("/").toString();
            assertThrows(IllegalStateException.class, () -> OsvScan.of(base).findings(List.of(KIT), "web"));
            server.serve("/v1/querybatch", bytes("{\"results\": []}"));
            assertThrows(IllegalStateException.class, () -> OsvScan.of(base).findings(List.of(KIT), "web"));
            assertEquals(List.of(), OsvScan.of(base).findings(List.of(), "web"), "nothing resolved, nothing asked");
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
