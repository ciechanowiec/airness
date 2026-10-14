package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.ComplianceReportHtml;
import eu.ciechanowiec.airness.governance.ReportEvidence;
import eu.ciechanowiec.airness.governance.ReportInventory;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportSource;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportRenderingTest {

    private static final int PACKAGES = 35;
    private static final int SECTIONS = 7;

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void downloadsTheEmbeddedInventoryAndSbomWithoutExternalRequests() {
        ComplianceReport report = sample();
        Playwright.CreateOptions options = new Playwright.CreateOptions()
            .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        try (
            Playwright playwright = Playwright.create(options);
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage()
        ) {
            page.route("**/*", Route::abort);
            page.setContent(ComplianceReportHtml.render(report));
            Download csv = page.waitForDownload(
                () -> page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Download inventory (CSV)")
                ).click()
            );
            assertEquals(report.fileStem() + ".csv", csv.suggestedFilename());
            assertEquals("inventory", Files.readString(csv.path()));
            Download sbom = page.waitForDownload(
                () -> page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Download SBOM (CycloneDX JSON)")
                ).click()
            );
            assertEquals(report.evidence().sbom(), Files.readString(sbom.path()));
        }
    }

    @Test
    void rejectsPaginationThatMovedAfterFillingTheContents() {
        assertThrows(
            IllegalStateException.class,
            () -> ReportPdf.verifyPagination(Map.of("section-1", 1), Map.of("section-1", 2))
        );
    }

    @Test
    @SneakyThrows
    void restoresThePreviousHtmlWhenThePdfCannotBeReplaced() {
        Path output = this.directory.toRealPath();
        String stem = "oss-compliance-report-existing";
        Path html = Files.writeString(output.resolve(stem + ".html"), "previous html");
        Path blocked = Files.createDirectory(output.resolve(stem + ".pdf"));
        Files.writeString(blocked.resolve("keep.txt"), "keep");
        ReportPublication publication = new ReportPublication(output.resolve("repository"), output, stem);
        assertThrows(IOException.class, () -> publication.publish("replacement", new byte[] {1}));
        assertEquals("previous html", Files.readString(html));
        assertEquals("keep", Files.readString(blocked.resolve("keep.txt")));
    }

    @Test
    @SneakyThrows
    void printsTheOfflineInventoryWithRealBookmarksAndFilledContents() {
        ComplianceReport report = sample();
        String html = ComplianceReportHtml.render(report);
        byte[] pdf = ReportPdf.print(html);
        Map<String, Integer> contents = ReportPdfContents.read(pdf);
        assertEquals(SECTIONS, contents.size());
        assertTrue(
            Optional.ofNullable(contents.get("section-7")).orElseThrow()
                > Optional.ofNullable(contents.get("section-1")).orElseThrow()
        );
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Open Source Software Compliance Report"));
            assertTrue(text.replace(" ", "").contains("sample:dependency-34:jar:1.0"));
            assertFalse(text.contains("000"));
        }
        Path preview = Path.of("target/report-preview");
        Files.createDirectories(preview);
        Files.writeString(preview.resolve("report.html"), html);
        Files.write(preview.resolve("report.pdf"), pdf);
    }

    @Test
    @SneakyThrows
    void writesOnlyIntoTheNormalizedDestination() {
        Path root = this.directory.toRealPath();
        Path output = Files.createDirectory(root.resolve("output"));
        Path elsewhere = Files.createDirectories(root.resolve("elsewhere/nested"));
        Files.createSymbolicLink(output.resolve("redirect"), elsewhere);
        String stem = "oss-compliance-report-normalized";
        new ReportPublication(root.resolve("repository"), output.resolve("redirect/../reports"), stem)
            .publish("normalized report", new byte[] {1});
        assertEquals("normalized report", Files.readString(output.resolve("reports/" + stem + ".html")));
        assertFalse(Files.exists(root.resolve("elsewhere/reports")));
    }

    @Test
    @SneakyThrows
    void replacesOlderPairsWhilePreservingUnrelatedFiles() {
        Path output = this.directory.toRealPath().resolve("output");
        Files.createDirectories(output);
        Files.writeString(output.resolve("oss-compliance-report-fixture-111111111111.html"), "old");
        Files.writeString(output.resolve("oss-compliance-report-fixture-111111111111.pdf"), "old");
        Files.writeString(output.resolve("review-notes.txt"), "keep");
        Path other = output.resolve("oss-compliance-report-fixture-child-111111111111.html");
        Files.writeString(other, "other project");
        String stem = "oss-compliance-report-fixture-222222222222";
        new ReportPublication(this.directory.resolve("repository"), output, stem).publish("new html", new byte[] {1});
        assertEquals("new html", Files.readString(output.resolve(stem + ".html")));
        assertFalse(Files.exists(output.resolve("oss-compliance-report-fixture-111111111111.html")));
        assertFalse(Files.exists(output.resolve(stem + ".pdf.tmp")));
        assertEquals("keep", Files.readString(output.resolve("review-notes.txt")));
        assertEquals("other project", Files.readString(other));
    }

    private static ComplianceReport sample() {
        List<ReportPackage> packages = IntStream.range(0, PACKAGES).mapToObj(
            index -> new ReportPackage(
                "sample:dependency-" + index + ":jar:1.0", List.of("MIT"),
                List.of(new ReportUse("sample:application:jar:1.0", "compile", "Direct")),
                Map.of(
                    "META-INF/LICENSE", "Copyright 2026 Example Authors.\nPermission is granted to use this example."
                )
            )
        ).toList();
        ReportInventory inventory = new ReportInventory(
            packages, List.of(), new ReportLicensePolicy(Set.of("MIT"), Map.of())
        );
        ReportEvidence evidence = new ReportEvidence(
            "{}", ReportJson.encode(Map.of("sbom", "{}", "csv", "inventory")),
            Map.of("SBOM SHA-256", ReportJson.hash("{}"), "Modules", "sample:application:jar:1.0")
        );
        return new ComplianceReport(
            "example-application", new ReportSource(
                "abcdef123456abcdef123456abcdef123456abcdef", "fingerprint", "2026-10-13T12:00:00Z", List.of()
            ), inventory,
            evidence
        );
    }
}
