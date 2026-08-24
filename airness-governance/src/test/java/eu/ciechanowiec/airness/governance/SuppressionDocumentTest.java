package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An entry recording an advisory this project cannot reach says why, says when, and says which.
 */
class SuppressionDocumentTest {

    private static final String COMPLETE = """
        <suppressions>
            <suppress>
                <notes>The vulnerable code path is a servlet this project never deploys. Added 2026-08-23.</notes>
                <cve>CVE-2020-27225</cve>
            </suppress>
        </suppressions>
        """;

    @TempDir
    private Path directory;

    @Test
    void acceptsAnEntryThatRecordsTheWholeDecision() {
        assertEquals(List.of(), this.problems(COMPLETE), "a reason, a date and an advisory are the decision");
    }

    @Test
    void rejectsAnEntryThatExplainsNothing() {
        String document = """
            <suppressions>
                <suppress>
                    <cve>CVE-2020-27225</cve>
                </suppress>
            </suppressions>
            """;
        assertTrue(
            this.problems(document).getFirst().contains("say why this project cannot reach"),
            "an exception nobody explained is one nobody can retire"
        );
    }

    @Test
    void rejectsAnEntryThatRecordsNoDate() {
        String document = """
            <suppressions>
                <suppress>
                    <notes>The vulnerable code path is a servlet this project never deploys.</notes>
                    <cve>CVE-2020-27225</cve>
                </suppress>
            </suppressions>
            """;
        assertTrue(
            this.problems(document).getFirst().contains("record the date"),
            "a judgement with no date cannot be told from one made before the code was rewritten"
        );
    }

    @Test
    void rejectsAnEntryThatNamesOnlyAPackage() {
        String document = """
            <suppressions>
                <suppress>
                    <notes>This project never reaches it. Added 2026-08-23.</notes>
                    <packageUrl regex="true">^pkg:maven/org\\.example/.*$</packageUrl>
                </suppress>
            </suppressions>
            """;
        assertTrue(
            this.problems(document).getFirst().contains("name the advisory it excuses"),
            "a package with no advisory excuses everything that will ever be published about it"
        );
    }

    @SneakyThrows
    private List<String> problems(CharSequence content) {
        Path document = this.directory.resolve("suppressions.xml");
        Files.writeString(document, content);
        return new SuppressionDocument(document).problems();
    }
}
