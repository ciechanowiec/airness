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
import com.microsoft.playwright.options.Media;
import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.ComplianceReportHtml;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportRenderingTest {

    private static final int SECTIONS = 6;
    // The width and height of an A4 page inside the report's print margins, in CSS pixels, so the page is laid out
    // as the PDF prints it.
    private static final int PRINTED_WIDTH = 673;
    private static final int PRINTED_HEIGHT = 960;
    // Every line a table cell wraps onto ends where a reader expects a break: at a space, or after a hyphen or a
    // separator of a package name, never inside a word, a version or a license identifier.
    private static final String WORD_BREAKS = """
        () => Array.from(document.querySelectorAll('table.fixed td')).flatMap(cell => {
            const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
            const broken = [];
            for (let text = walker.nextNode(); text; text = walker.nextNode()) {
                const lines = new Map();
                for (let index = 0; index < text.length; index++) {
                    const range = document.createRange();
                    range.setStart(text, index);
                    range.setEnd(text, index + 1);
                    const rect = range.getClientRects()[0];
                    if (!rect) continue;
                    const top = Math.round(rect.top);
                    lines.set(top, (lines.get(top) || '') + text.data[index]);
                }
                const ordered = Array.from(lines.entries()).sort((a, b) => a[0] - b[0]).map(entry => entry[1]);
                ordered.slice(0, -1).filter(line => !/[\\s/:.@,-]$/.test(line))
                    .forEach(() => broken.push(text.data));
            }
            return broken;
        })
        """;
    private static final String OVERFLOWS = """
        () => Array.from(document.querySelectorAll('td')).filter(cell => cell.scrollWidth > cell.clientWidth + 1)
            .map(cell => cell.textContent)
        """;
    private static final String RAGGED = """
        () => Array.from(document.querySelectorAll(
            '#content p:not(.empty):not(.title):not(.license-heading):not(.notice-packages), .statements li, '
                + 'table.facts.plan td + td'
        )).filter(element => getComputedStyle(element).textAlign !== 'justify').map(element => element.textContent)
        """;
    private static final String WIDER = """
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth
        """;
    private static final String HEADERS = """
        () => Array.from(document.querySelectorAll('thead'))
            .filter(header => getComputedStyle(header).display !== 'table-header-group').length
        """;

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void downloadsTheEmbeddedInventoryAndSbomWithoutExternalRequests() {
        ComplianceReport report = ReportSample.report();
        Playwright.CreateOptions options = new Playwright.CreateOptions()
            .setEnv(ReportBrowser.environment());
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
    void selectsChromiumFromTheOwnedVersionedCache() {
        Playwright.CreateOptions options = new Playwright.CreateOptions().setEnv(ReportBrowser.environment());
        try (Playwright playwright = Playwright.create(options)) {
            Path executable = Path.of(playwright.chromium().executablePath());
            Path owned = Path.of(System.getProperty("user.home"), ".cache", "airness", "report-browser");
            assertTrue(executable.startsWith(owned));
            assertTrue(Files.isRegularFile(executable));
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
        ComplianceReport report = ReportSample.report();
        String html = ComplianceReportHtml.render(report);
        byte[] pdf = ReportPdf.print(html);
        Map<String, Integer> contents = ReportPdfContents.read(pdf);
        assertTrue(
            IntStream.rangeClosed(1, SECTIONS).allMatch(section -> contents.containsKey("section-" + section)),
            "every section has a page in the contents"
        );
        assertTrue(contents.containsKey("section-4.2"), "every subsection has a page in the contents");
        assertTrue(
            Optional.ofNullable(contents.get("section-6")).orElseThrow()
                > Optional.ofNullable(contents.get("section-1")).orElseThrow()
        );
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text.contains("Open Source Software Report"));
            assertTrue(text.replace(" ", "").contains("sample:dependency-34"));
            assertTrue(text.contains("Awaiting fix"));
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

    @Test
    void printsEveryTableWithinItsColumnsAndEveryLicenseWhole() {
        Playwright.CreateOptions options = new Playwright.CreateOptions().setEnv(ReportBrowser.environment());
        try (
            Playwright playwright = Playwright.create(options);
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(PRINTED_WIDTH, PRINTED_HEIGHT))
        ) {
            page.route("**/*", Route::abort);
            page.setContent(ComplianceReportHtml.render(ReportSample.report()));
            page.evaluate("() => document.fonts.ready.then(() => true)");
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
            assertEquals(List.of(), page.evaluate(WORD_BREAKS), "a table cell breaks inside a word");
            assertEquals(List.of(), page.evaluate(OVERFLOWS), "a table cell overflows its column");
            assertEquals(List.of(), page.evaluate(RAGGED), "a paragraph of prose is not justified");
            assertEquals(0, page.evaluate(HEADERS), "a table header does not repeat on every printed page");
            assertEquals(0, page.evaluate(WIDER), "content wider than the page makes the printer shrink every page");
        }
    }
}
