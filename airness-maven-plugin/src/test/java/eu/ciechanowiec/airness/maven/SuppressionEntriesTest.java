package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every entry of the suppression setting names a document kept inside the repository, which the suppression
 * rules then read. No entry is passed over, because the scan would read what the check did not.
 */
class SuppressionEntriesTest {

    private static final String COMPLETE = """
        <suppressions>
            <suppress>
                <notes>The vulnerable code path is a servlet this project never deploys. Added 2026-08-23.
                Mitigation: Removed with the next upgrade.</notes>
                <cve>CVE-2020-27225</cve>
            </suppress>
        </suppressions>
        """;
    private static final String BLANKET = """
        <suppressions>
            <suppress>
                <notes>The vulnerable code path is a servlet this project never deploys. Added 2026-08-23.
            Mitigation: Removed with the next upgrade.</notes>
                <cve>CVE-2020-27225</cve>
                <vulnerabilityName regex="true">.*</vulnerabilityName>
            </suppress>
        </suppressions>
        """;

    @TempDir
    private Path root;

    @TempDir
    private Path elsewhere;

    @Test
    void refusesARemoteDocumentInEverySchemeTheScanDownloads() {
        Path module = this.module();
        List.of(
            "https://example.org/suppressions.xml", "http://example.org/suppressions.xml",
            "file:///etc/suppressions.xml", "file:" + this.root.resolve("shop/suppressions.xml"),
            "HTTPS://example.org/suppressions.xml"
        ).forEach(
            entry -> assertEquals(
                List.of(refusal(entry, "is a URL, which the scan would download")),
                SuppressionEntries.problems(entry, module, this.root),
                entry + " is fetched by the scan, so the check cannot read what it would excuse"
            )
        );
    }

    @Test
    void refusesAPathWithNoDocumentAtIt() {
        Path module = this.module();
        assertEquals(
            List.of(refusal("missing.xml", "is not an existing regular file")),
            SuppressionEntries.problems("missing.xml", module, this.root),
            "a declared path that names nothing is a mistake, never a silent no-op"
        );
        assertEquals(
            List.of(refusal(".airness", "is not an existing regular file")),
            SuppressionEntries.problems(".airness", module, this.root),
            "a directory is not a suppression document"
        );
    }

    @Test
    @SneakyThrows
    void refusesADocumentOutsideTheRepositoryWhateverPathReachesIt() {
        Path module = this.module();
        Path outside = Files.writeString(this.elsewhere.resolve("suppressions.xml"), BLANKET);
        Path link = Files.createSymbolicLink(module.resolve("linked.xml"), outside);
        String relative = module.relativize(outside).toString();
        List.of(outside.toString(), relative, link.getFileName().toString()).forEach(
            entry -> assertEquals(
                List.of(refusal(entry, "lies outside the repository")),
                SuppressionEntries.problems(entry, module, this.root),
                entry + " reaches a document no commit of this repository carries"
            )
        );
    }

    @Test
    @SneakyThrows
    void readsEveryEntryOfTheListAndHoldsAKeptDocumentToTheRules() {
        Path module = this.module();
        Path kept = Files.writeString(module.resolve(SuppressionEntries.DEFAULT), COMPLETE);
        Files.writeString(module.resolve("blanket.xml"), BLANKET);
        assertEquals(
            List.of(), SuppressionEntries.problems(kept.toString(), module, this.root),
            "a complete document kept in the module is the setting's purpose"
        );
        List<String> problems = SuppressionEntries.problems(
            kept + ", blanket.xml ,https://example.org/s.xml", module, this.root
        );
        assertTrue(
            problems.stream().anyMatch(problem -> problem.contains("literal advisory")),
            "the blanket document beside it is read: " + problems
        );
        assertEquals(
            refusal("https://example.org/s.xml", "is a URL, which the scan would download"), problems.getLast(),
            "and the remote entry after it is refused rather than passed over"
        );
    }

    @Test
    void acceptsTheDefaultPathWhileNoDocumentIsKeptThere() {
        Path module = this.module();
        String unkept = module.resolve(SuppressionEntries.DEFAULT).toString();
        assertEquals(
            List.of(), SuppressionEntries.problems(unkept, module, this.root),
            "the default names the place for a document, and the parent reads it only once one is kept there"
        );
        assertEquals(List.of(), SuppressionEntries.problems(null, module, this.root), "nothing declared, nothing read");
    }

    @SneakyThrows
    private Path module() {
        Path module = this.root.resolve("shop");
        Files.createDirectories(module.resolve(".airness"));
        return module;
    }

    private static String refusal(String entry, String reason) {
        return "airness.dependency-check.suppression.file names " + entry + ", which " + reason
            + "; name a suppression document kept inside the repository, such as "
            + ".airness/dependency-check-suppressions.xml in the module whose dependency it excuses";
    }
}
