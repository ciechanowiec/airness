package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final String MODULE = "web";

    @Test
    void reportsEveryAdvisoryButAWithdrawnOne() {
        try (ArchiveServer server = NpmFixture.osv()) {
            List<ReportAdvisory> advisories = OsvScan.of(server.uri("/").toString()).findings(
                List.of("pkg:npm/left-pad@1.3.0", KIT, "pkg:npm/%40esbuild/linux-x64@0.25.0", KIT), MODULE
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
            ReportAdvisory advisory = OsvScan.of(server.uri("/").toString()).findings(List.of(KIT), MODULE).getFirst()
                .advisory();
            assertEquals("First line.", advisory.description());
            assertEquals("Unknown", advisory.severity());
        }
    }

    @Test
    void asksASystemPackageByItsSourceAndReadsTheDistributionsOwnIdentifiersAndRating() {
        String artifact = "pkg:deb/ubuntu/libssl3@3.0.13-0ubuntu3?distro=ubuntu-24.04";
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/v1/querybatch", bytes("{\"results\": [{\"vulns\": [{\"id\": \"UBUNTU-CVE-2026-1\"}]}]}"));
            server.serve(
                "/v1/vulns/UBUNTU-CVE-2026-1", bytes(
                    "{\"id\": \"UBUNTU-CVE-2026-1\", \"upstream\": [\"CVE-2026-1\"], \"severity\": [{\"type\":"
                        + " \"CVSS_V3\", \"score\": \"CVSS:3.1/AV:N\"}, {\"type\": \"Ubuntu\", \"score\": \"medium\"}],"
                        + " \"affected\": [{\"package\": {\"ecosystem\": \"Ubuntu:24.04:LTS\", \"name\": \"openssl\"},"
                        + " \"ranges\": [{\"events\": [{\"introduced\": \"0\"},"
                        + " {\"fixed\": \"3.0.13-0ubuntu3.5\"}]}]}]}"
                )
            );
            OsvAdvisory finding = OsvScan.of(server.uri("/").toString()).findingsOf(
                List.of(OsvQuery.system(artifact, "Ubuntu:24.04:LTS", "openssl", "3.0.13-0ubuntu3")), MODULE
            ).getFirst();
            assertAll(
                () -> assertTrue(
                    server.bodies().getFirst().contains("\"ecosystem\":\"Ubuntu:24.04:LTS\""), server.bodies()
                        .getFirst()
                ),
                () -> assertTrue(server.bodies().getFirst().contains("\"version\":\"3.0.13-0ubuntu3\"")),
                () -> assertEquals(artifact, finding.advisory().artifact()),
                () -> assertEquals("MEDIUM", finding.advisory().severity()),
                () -> assertTrue(
                    finding.identifiers().contains("CVE-2026-1"), "the CVE an advisory answers is one of its names"
                ),
                () -> assertTrue(finding.fixed())
            );
        }
    }

    @Test
    void asksAgainWithThePageTokenUntilOsvHasAnsweredEveryPage() {
        try (ArchiveServer server = new ArchiveServer()) {
            String batch = "/v1/querybatch";
            List<String> pages = List.of(
                "{\"results\": [{\"next_page_token\": \"page-2\"}]}",
                "{\"results\": [{\"vulns\": [{\"id\": \"OSV-1\"}], \"next_page_token\": \"page-3\"}]}",
                "{\"results\": [{\"vulns\": [{\"id\": \"OSV-2\"}]}]}"
            );
            server.beforeAnswer(
                () -> server.serve(batch, bytes(pages.get(Math.min(server.requests(), pages.size()) - 1)))
            );
            server.serve("/v1/vulns/OSV-1", bytes("{\"id\": \"OSV-1\", \"summary\": \"First\"}"));
            server.serve("/v1/vulns/OSV-2", bytes("{\"id\": \"OSV-2\", \"summary\": \"Second\"}"));
            List<String> found = OsvScan.of(server.uri("/").toString()).findings(List.of(KIT), MODULE).stream()
                .map(advisory -> advisory.advisory().description()).sorted().toList();
            assertEquals(
                List.of("First", "Second"), found,
                "a query answered with only a page token still has vulnerabilities on the pages that follow"
            );
            List<String> bodies = server.bodies();
            assertTrue(bodies.get(1).contains("\"page_token\":\"page-2\""), bodies.get(1));
            assertTrue(bodies.get(2).contains("\"page_token\":\"page-3\""), bodies.get(2));
            assertTrue(bodies.get(1).contains(KIT) && bodies.get(2).contains(KIT), "each page repeats the package");
        }
    }

    @Test
    void asksAgainOnlyForTheQueriesOsvAnsweredInPart() {
        try (ArchiveServer server = new ArchiveServer()) {
            String batch = "/v1/querybatch";
            String other = "pkg:npm/left-pad@1.3.0";
            List<String> pages = List.of(
                "{\"results\": [{\"next_page_token\": \"page-2\"}, {\"vulns\": [{\"id\": \"OSV-1\"}]}]}",
                "{\"results\": [{\"vulns\": [{\"id\": \"OSV-2\"}]}]}"
            );
            server.beforeAnswer(
                () -> server.serve(batch, bytes(pages.get(Math.min(server.requests(), pages.size()) - 1)))
            );
            server.serve("/v1/vulns/OSV-1", bytes("{\"id\": \"OSV-1\", \"summary\": \"First\"}"));
            server.serve("/v1/vulns/OSV-2", bytes("{\"id\": \"OSV-2\", \"summary\": \"Second\"}"));
            List<String> found = OsvScan.of(server.uri("/").toString()).findings(List.of(KIT, other), MODULE).stream()
                .map(advisory -> advisory.advisory().artifact() + " " + advisory.advisory().description()).sorted()
                .toList();
            assertEquals(List.of(KIT + " Second", other + " First"), found);
            assertFalse(server.bodies().get(1).contains(other), "a query OSV answered in full is not asked again");
        }
    }

    @Test
    void failsWhenOsvCannotBeReadOrAnswersWrongly() {
        IllegalStateException unreachable = assertThrows(
            IllegalStateException.class, () -> OsvScan.of("http://127.0.0.1:9/").findings(List.of(KIT), MODULE)
        );
        assertTrue(Objects.requireNonNull(unreachable.getMessage()).contains("rather than passing without it"));
        try (ArchiveServer server = new ArchiveServer()) {
            String base = server.uri("/").toString();
            assertThrows(IllegalStateException.class, () -> OsvScan.of(base).findings(List.of(KIT), MODULE));
            server.serve("/v1/querybatch", bytes("{\"results\": []}"));
            assertThrows(IllegalStateException.class, () -> OsvScan.of(base).findings(List.of(KIT), MODULE));
            assertEquals(List.of(), OsvScan.of(base).findings(List.of(), MODULE), "nothing resolved, nothing asked");
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
