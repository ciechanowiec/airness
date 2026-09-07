package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

    @Test
    void acceptsMultipleNamedAdvisories() {
        String selectors = """
            <cve>CVE-2020-27225</cve>
            <cve>CVE-2021-44228</cve>
            <vulnerabilityName>GHSA-jfh8-c2jp-5v3q</vulnerabilityName>
            """;
        assertTrue(this.selections(selectors).isEmpty());
    }

    @Test
    void acceptsDependencySelectorPatterns() {
        Map.of(
            "packageUrl", "^pkg:maven/org.example/library@.*$", "gav", "org.example:library:.*",
            "filePath", ".*/library.jar"
        ).forEach(
            (selector, value) -> assertTrue(
                this.selections(
                    "<%s regex=\"true\">%s</%s><cve>CVE-2020-27225</cve>".formatted(selector, value, selector)
                ).isEmpty()
            )
        );
    }

    @Test
    void acceptsAnExactArtifactHash() {
        String selectors = """
            <sha1>0123456789abcdef0123456789abcdef01234567</sha1>
            <cve>CVE-2020-27225</cve>
            """;
        assertTrue(this.selections(selectors).isEmpty());
    }

    @Test
    void acceptsExplicitLiteralMatching() {
        List.of("false", "0").forEach(
            flag -> assertTrue(
                this.selections(
                    "<vulnerabilityName regex=\"%s\">CVE-2020-27225</vulnerabilityName>".formatted(flag)
                ).isEmpty()
            )
        );
    }

    @Test
    void rejectsBlanketAdvisoryPatterns() {
        List.of("true", "1").forEach(
            flag -> assertTrue(
                this.selections(
                    "<vulnerabilityName regex=\"%s\">.*</vulnerabilityName>".formatted(flag)
                ).stream().anyMatch(problem -> problem.contains("literal advisory"))
            )
        );
    }

    @Test
    void rejectsBroadSelectors() {
        broadSelectors().forEach(
            (selector, value) -> assertTrue(
                this.selections("<%s>%s</%s>".formatted(selector, value, selector))
                    .stream().anyMatch(problem -> problem.contains("replace " + selector))
            )
        );
    }

    @Test
    void aNamedCveCannotHideABroadSelector() {
        broadSelectors().forEach(
            (selector, value) -> assertTrue(
                this.selections(
                    "<cve>CVE-2020-27225</cve><%s>%s</%s>".formatted(selector, value, selector)
                ).stream().anyMatch(problem -> problem.contains("replace " + selector))
            )
        );
    }

    @Test
    void rejectsEmptyAdvisoryNames() {
        List.of("cve", "vulnerabilityName").forEach(
            selector -> assertTrue(
                this.selections("<%s> </%s>".formatted(selector, selector))
                    .stream().anyMatch(problem -> problem.contains("literal advisory"))
            )
        );
    }

    @Test
    void rejectsAPatternBesideANamedCve() {
        String selectors = """
            <cve>CVE-2020-27225</cve>
            <vulnerabilityName regex="true">.*</vulnerabilityName>
            """;
        assertTrue(this.selections(selectors).stream().anyMatch(problem -> problem.contains("literal advisory")));
    }

    @Test
    void validatesPrefixedAndGroupedEntries() {
        String document = """
            <dc:suppressions xmlns:dc="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.4.xsd">
                <dc:suppressionGroup name="examples">
                    <dc:suppress>
                        <dc:notes>The affected parser is absent. Added 2026-09-07.</dc:notes>
                        <dc:vulnerabilityName regex="1">.*</dc:vulnerabilityName>
                    </dc:suppress>
                </dc:suppressionGroup>
            </dc:suppressions>
            """;
        assertTrue(this.problems(document).stream().anyMatch(problem -> problem.contains("literal advisory")));
    }

    @Test
    void acceptsLiteralAdvisoriesInPrefixedGroups() {
        String document = """
            <dc:suppressions xmlns:dc="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.4.xsd">
                <dc:suppressionGroup name="examples">
                    <dc:suppress>
                        <dc:notes>The affected parser is absent. Added 2026-09-07.</dc:notes>
                        <dc:cve>CVE-2020-27225</dc:cve>
                    </dc:suppress>
                </dc:suppressionGroup>
            </dc:suppressions>
            """;
        assertTrue(this.problems(document).isEmpty());
    }

    @Test
    void groupNotesCannotReplaceAnEntryReason() {
        String document = """
            <suppressions>
                <suppressionGroup name="examples">
                    <notes>The affected parser is absent. Added 2026-09-07.</notes>
                    <suppress><cve>CVE-2020-27225</cve></suppress>
                </suppressionGroup>
            </suppressions>
            """;
        assertTrue(this.problems(document).stream().anyMatch(problem -> problem.contains("say why")));
    }

    private static Map<String, String> broadSelectors() {
        return Map.of(
            "cpe", "cpe:/a:example:library", "cwe", "22", "cvssBelow", "7", "cvssV2Below", "7",
            "cvssV3Below", "7", "cvssV4Below", "7"
        );
    }

    private List<String> selections(String selectors) {
        return this.problems(
            """
                <suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
                    <suppress>
                        <notes>The affected parser is absent. Added 2026-09-07.</notes>
                        %s
                    </suppress>
                </suppressions>
                """.formatted(selectors)
        );
    }

    @SneakyThrows
    private List<String> problems(CharSequence content) {
        Path document = this.directory.resolve("suppressions.xml");
        Files.writeString(document, content);
        return new SuppressionDocument(document).problems();
    }
}
