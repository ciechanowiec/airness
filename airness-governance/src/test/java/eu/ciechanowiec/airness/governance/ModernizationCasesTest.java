package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

/**
 * Holds the Checkstyle and PMD rules that carry the modernization requirements to their examples.
 *
 * <p>A rule written for a requirement reports every violation and none of its lookalikes. A rule reused for
 * one reports every violation, and it may report more than the requirement asks, since it already runs on its
 * own terms. An identifier joined with a bar is satisfied by any of the rules it names.
 */
class ModernizationCasesTest {

    @Test
    void checkstyleRulesReportTheViolationsAndOnlyThem(@TempDir Path directory) {
        List<Case> cases = cases("Checkstyle");
        assertFalse(cases.isEmpty(), "the Checkstyle rules have examples");
        assertAll(cases.stream().map(sample -> () -> sample.assertVerdict(directory, this::checkstyle)));
    }

    @Test
    void pmdRulesReportTheViolationsAndOnlyThem(@TempDir Path directory) {
        List<Case> cases = cases("PMD");
        assertFalse(cases.isEmpty(), "the PMD rules have examples");
        assertAll(cases.stream().map(sample -> () -> sample.assertVerdict(directory, this::pmd)));
    }

    private int checkstyle(Path directory, String rule, String source) {
        return CheckstyleRule.findings(directory, source, rule);
    }

    @SneakyThrows
    private int pmd(Path directory, String rule, String source) {
        Path folder = Files.createTempDirectory(directory, "pmd");
        return PmdRule.inspect(folder, rule, source).getViolations().size();
    }

    @SneakyThrows
    private static List<Case> cases(String tool) {
        Path inventory = SelfModules.repository()
            .resolve("airness-governance/src/test/resources/modernization-cases.xml");
        return ProjectFiles.descendants(Xml.parse(Files.readString(inventory)), "rule")
            .filter(rule -> tool.equals(rule.getAttribute("tool")))
            .flatMap(rule -> Xml.children(rule, "case").stream().map(sample -> Case.read(rule, sample)))
            .toList();
    }

    @FunctionalInterface
    private interface Analyzer {

        int findings(Path directory, String rule, String source);
    }

    private record Case(String rules, String name, boolean flagged, String source) {

        static Case read(Element rule, Element sample) {
            return new Case(
                rule.getAttribute("id"),
                sample.getAttribute("name"),
                Boolean.parseBoolean(sample.getAttribute("flagged")),
                ProjectFiles.child(sample, "source").getTextContent()
            );
        }

        private int findings(Analyzer analyzer, Path directory, String rule, String description) {
            return assertDoesNotThrow(() -> analyzer.findings(directory, rule, this.source), description);
        }

        void assertVerdict(Path directory, Analyzer analyzer) {
            String description = this.rules + " over " + this.name;
            boolean reported = Arrays.stream(this.rules.split("\\|"))
                .anyMatch(rule -> this.findings(analyzer, directory, rule, description) > 0);
            if (this.flagged) {
                assertTrue(reported, () -> description + " reports the violation");
            } else {
                assertFalse(reported, () -> description + " accepts the lookalike");
            }
        }
    }
}
