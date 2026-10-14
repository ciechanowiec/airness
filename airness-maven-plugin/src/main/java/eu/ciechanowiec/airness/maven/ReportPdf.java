package eu.ciechanowiec.airness.maven;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.Media;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Prints the offline HTML with pinned Chromium, then fills its contents from the actual PDF bookmarks.
 */
@UtilityClass
final class ReportPdf {

    private static final double TIMEOUT = 60_000;
    private static final String CONTENTS = """
        pages => {
            document.querySelectorAll('#toc .page').forEach(slot => {
                const number = pages[slot.dataset.for];
                if (!number) throw new Error('No PDF page for ' + slot.dataset.for);
                slot.textContent = String(number);
            });
        }
        """;

    static byte[] print(String html) {
        Playwright.CreateOptions setup = new Playwright.CreateOptions()
            .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        try (
            Playwright playwright = Playwright.create(setup);
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setTimeout(TIMEOUT));
            Page page = browser.newPage()
        ) {
            page.setDefaultTimeout(TIMEOUT);
            page.route("**/*", Route::abort);
            page.setContent(html);
            page.evaluate("() => document.fonts.ready.then(() => true)");
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
            Page.PdfOptions options = new Page.PdfOptions().setFormat("A4").setPrintBackground(true)
                .setPreferCSSPageSize(true).setOutline(true).setTagged(true);
            Map<String, Integer> pages = ReportPdfContents.read(page.pdf(options));
            page.evaluate(CONTENTS, pages);
            byte[] printed = page.pdf(options);
            verifyPagination(pages, ReportPdfContents.read(printed));
            return printed;
        } catch (PlaywrightException exception) {
            throw new IllegalStateException(
                "Could not print the compliance PDF; install its pinned browser with "
                    + "mvn airness:report-browser-install and the Chromium system libraries", exception
            );
        }
    }

    static void verifyPagination(Map<String, Integer> before, Map<String, Integer> after) {
        if (!before.equals(after)) {
            throw new IllegalStateException("Report pagination moved while filling the contents");
        }
    }
}
