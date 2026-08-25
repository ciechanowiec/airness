package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SarifFindingsTest {

    private static final String REPORT = "qodana.sarif.json";

    /**
     * Three findings more than the reader names one by one, so the count of the rest is not zero.
     */
    private static final int FOUND = 23;

    private static final int NAMED = 21;

    private static final int LINE = 42;

    @Test
    void namesTheFileTheLineTheRuleAndTheMessageOfAFinding(@TempDir Path directory) {
        Path report = report(directory, wrapped(finding("TypeMayBeWeakened", "src/main/java/Rules.java", LINE)));
        assertEquals(
            List.of("src/main/java/Rules.java:%d TypeMayBeWeakened: Can be weakened".formatted(LINE)),
            new SarifFindings(report).listed()
        );
    }

    @Test
    void namesEveryFindingOfEveryRunTheReportHolds(@TempDir Path directory) {
        Path report = report(
            directory,
            wrapped(finding("First", "One.java", 1)) + ",\n"
                + wrapped(finding("Second", "Two.java", 2))
        );
        assertEquals(2, new SarifFindings(report).listed().size());
    }

    @Test
    void readsAFindingThatCarriesNeitherLocationNorRuleNorMessage(@TempDir Path directory) {
        Path report = report(directory, wrapped("{}"));
        assertEquals(
            List.of("unknown file unknown rule: no message"),
            new SarifFindings(report).listed()
        );
    }

    @Test
    void readsAFindingThatCarriesAFileButNoLine(@TempDir Path directory) {
        String located = """
            {
              "ruleId": "Unused",
              "message": {"text": "Never read"},
              "locations": [{"physicalLocation": {"artifactLocation": {"uri": "Loose.java"}}}]
            }""";
        Path report = report(directory, wrapped(located));
        assertEquals(List.of("Loose.java Unused: Never read"), new SarifFindings(report).listed());
    }

    @Test
    void putsAMessageThatRunsOverSeveralLinesOnOneLine(@TempDir Path directory) {
        String wrapping = """
            {"ruleId": "Wrapped", "message": {"text": "First line\\n  second line"}}""";
        Path report = report(directory, wrapped(wrapping));
        assertEquals(
            List.of("unknown file Wrapped: First line second line"),
            new SarifFindings(report).listed()
        );
    }

    @Test
    void countsTheFindingsItDoesNotNameRatherThanDroppingThem(@TempDir Path directory) {
        String many = IntStream.rangeClosed(1, FOUND)
            .mapToObj(index -> wrapped(finding("Rule%d".formatted(index), "File.java", index)))
            .collect(Collectors.joining(",\n"));
        Path report = report(directory, many);
        List<String> listed = new SarifFindings(report).listed();
        assertEquals(NAMED, listed.size());
        assertTrue(listed.getLast().startsWith("and 3 more in "));
    }

    @Test
    void reportsThatTheReportIsUnreadableRatherThanThatNothingWasFound(@TempDir Path directory) {
        Path report = report(directory, "not a report at all");
        assertTrue(new SarifFindings(report).listed().getFirst().contains("could not be read"));
    }

    @Test
    void namesNoFindingWhenTheCheckLeftNoReport(@TempDir Path directory) {
        assertEquals(List.of(), new SarifFindings(directory.resolve(REPORT)).listed());
    }

    private static String finding(String rule, String file, int line) {
        return """
            {
              "ruleId": "%s",
              "message": {"text": "Can be weakened"},
              "locations": [{"physicalLocation": {
                "artifactLocation": {"uri": "%s"}, "region": {"startLine": %d}
              }}]
            }""".formatted(rule, file, line);
    }

    private static String wrapped(String result) {
        return "{\"results\": [%s]}".formatted(result);
    }

    @SneakyThrows
    private static Path report(Path directory, String runs) {
        Path report = directory.resolve(REPORT);
        Files.writeString(report, "{\"runs\": [%s]}".formatted(runs));
        return report;
    }
}
