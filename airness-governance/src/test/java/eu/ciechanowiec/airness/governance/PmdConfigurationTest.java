package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CPDReport;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PmdConfigurationTest {

    private static final int CPD_MINIMUM_TOKENS = 100;
    private static final String CONFIGURATION
        = "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/pmd.xml";
    private static final List<Fixture> SEMANTIC_FIXTURES = List.of(
        new Fixture(
            "BlankJustification.java",
            """
                package example;
                @Justification(" ")
                @SuppressWarnings("PMD.AvoidDuplicateLiterals")
                final class BlankJustification {
                }
                """,
            "JustificationNeedsText",
            2
        ),
        new Fixture(
            "StaleJustification.java",
            """
                package example;
                @Justification("A suppression used to be here")
                final class StaleJustification {
                }
                """,
            "JustificationNeedsSuppression",
            2
        ),
        new Fixture(
            "MissingJustification.java",
            """
                package example;
                @SuppressWarnings("PMD.AvoidDuplicateLiterals")
                final class MissingJustification {
                }
                """,
            "SuppressionNeedsJustification",
            2
        ),
        new Fixture(
            "DialectQuery.java",
            """
                package example;
                interface DialectQuery {
                    @Query(value = "select nextval('numbering')", nativeQuery = true)
                    long unjustified();
                }
                """,
            "NativeQueryNeedsJustification",
            4
        ),
        new Fixture(
            "AbsentColumn.java",
            """
                package example;
                @Entity
                final class AbsentColumn {
                    private @Nullable String note;
                }
                """,
            "NullablePersistentValueNeedsJustification",
            4
        ),
        new Fixture(
            "Branched.java",
            """
                package example;
                final class Branched {
                    int counted(int given) {
                        int total = given;
                        if (given > 1) { total++; }
                        if (given > 2) { total++; }
                        if (given > 3) { total++; }
                        if (given > 4) { total++; }
                        if (given > 5) { total++; }
                        if (given > 6) { total++; }
                        if (given > 7) { total++; }
                        if (given > 8) { total++; }
                        return total;
                    }
                }
                """,
            "CyclomaticComplexity",
            3
        ),
        new Fixture(
            "Wide.java",
            """
                package example;
                final class Wide {
                    Wide(String first, String second, String third, String fourth, String fifth) {
                    }
                }
                """,
            "ExcessiveParameterList",
            3
        )
    );

    private static final Fixture POSITIVE_FIXTURE = new Fixture(
        "Positive.java",
        """
            package example;
            interface TypedQuery {
                @Query(value = "select two from Two two", nativeQuery = false)
                long typed();
            }
            @Embeddable
            record BoundDefault(String stated) {
                BoundDefault(@Nullable String stated) {
                    this.stated = stated == null ? "" : stated;
                }
            }
            enum Measured {
                ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT;
                String named() {
                    return switch (this) {
                        case ONE -> "one";
                        case TWO -> "two";
                        case THREE -> "three";
                        case FOUR -> "four";
                        case FIVE -> "five";
                        case SIX -> "six";
                        case SEVEN -> "seven";
                        case EIGHT -> "eight";
                    };
                }
            }
            final class WideTest {
                WideTest(String first, String second, String third, String fourth, String fifth) {
                }
            }
            """,
        "unused",
        1
    );

    @Test
    @SneakyThrows
    void reportsEveryConsumerSemanticFixtureAtItsLocation(@TempDir Path directory) {
        for (Fixture fixture : SEMANTIC_FIXTURES) {
            Report report = inspect(directory, fixture);
            assertTrue(report.getProcessingErrors().isEmpty(), () -> "PMD errors: " + report.getProcessingErrors());
            assertTrue(
                report.getConfigurationErrors().isEmpty(), () -> "PMD config: " + report.getConfigurationErrors()
            );
            assertTrue(
                report.getViolations().stream().anyMatch(fixture::matches),
                () -> "%s:%d must report %s, but reported %s"
                    .formatted(fixture.file(), fixture.line(), fixture.rule(), describe(report))
            );
        }
    }

    @Test
    @SneakyThrows
    void leavesSuppressedNativeAndPersistentExceptionsOpen(@TempDir Path directory) {
        Fixture fixture = new Fixture(
            "Justified.java",
            """
                package example;
                interface JustifiedQuery {
                    @SuppressWarnings("PMD.NativeQueryNeedsJustification")
                    @Justification("A sequence is reachable from SQL alone")
                    @Query(value = "select nextval('numbering')", nativeQuery = true)
                    long justified();
                }
                @Entity
                final class JustifiedColumn {
                    @SuppressWarnings("PMD.NullablePersistentValueNeedsJustification")
                    @Justification("The day an unissued document was issued")
                    private @Nullable String issuedOn;
                }
                """,
            "unused",
            1
        );
        Report report = inspect(directory, fixture);
        List<String> closedRules = List.of(
            "NativeQueryNeedsJustification",
            "NullablePersistentValueNeedsJustification",
            "JustificationNeedsText",
            "JustificationNeedsSuppression",
            "SuppressionNeedsJustification",
            "UnnecessaryWarningSuppression"
        );
        assertTrue(
            report.getViolations().stream().noneMatch(violation -> closedRules.contains(violation.getRule().getName())),
            () -> "the suppression and its reason must leave a way through, but reported " + describe(report)
        );
        assertEquals(2, report.getSuppressedViolations().size(), "both deliberate exceptions are suppressed");
    }

    @Test
    @SneakyThrows
    void keepsPositiveControlsFreeOfTheirSemanticRules(@TempDir Path directory) {
        Report report = inspect(directory, POSITIVE_FIXTURE);
        List<String> semanticRules = List.of(
            "NativeQueryNeedsJustification",
            "NullablePersistentValueNeedsJustification",
            "CyclomaticComplexity",
            "ExcessiveParameterList"
        );
        assertTrue(
            report.getViolations().stream().noneMatch(
                violation -> semanticRules.contains(violation.getRule().getName())
            ),
            () -> "positive controls must stay free of their paired rules, but reported " + describe(report)
        );
    }

    @Test
    @SneakyThrows
    void detectsAndClearsDuplicationWithTheParentThreshold(@TempDir Path directory) {
        String duplicated = """
            package example;
            import java.util.List;
            import java.util.Locale;
            final class FirstScorer {
                static int score(List<String> values) {
                    int total = 0;
                    for (int index = 0; index < values.size(); index++) {
                        String entry = values.get(index);
                        if (entry.isEmpty()) {
                            continue;
                        }
                        String trimmed = entry.trim().toLowerCase(Locale.ROOT);
                        if (trimmed.startsWith("a") || trimmed.startsWith("b")) {
                            total = total + trimmed.length() * 2;
                        } else if (trimmed.endsWith("z")) {
                            total = total - trimmed.length();
                        } else {
                            total = total + 1;
                        }
                        if (total > 1000) {
                            total = 1000;
                        }
                    }
                    return total;
                }
            }
            """;
        Path first = write(directory, "FirstScorer.java", duplicated);
        Path second = write(directory, "SecondScorer.java", duplicated.replace("FirstScorer", "SecondScorer"));
        CPDReport duplicatedReport = inspectDuplication(List.of(first, second));
        CPDReport cleanReport = inspectDuplication(List.of(first));
        assertFalse(duplicatedReport.getMatches().isEmpty(), "the duplicated fixture crosses the parent threshold");
        assertTrue(cleanReport.getMatches().isEmpty(), "one source does not duplicate another source");
        assertTrue(duplicatedReport.getProcessingErrors().isEmpty());
    }

    @SneakyThrows
    private static Report inspect(Path directory, Fixture fixture) {
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setClassLoader(Thread.currentThread().getContextClassLoader());
        configuration.setDefaultLanguageVersion(JavaLanguageModule.getInstance().getVersion("25"));
        configuration.setIgnoreIncrementalAnalysis(true);
        configuration.setShowSuppressedViolations(true);
        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            analysis.addRuleSet(
                analysis.newRuleSetLoader().loadFromString(CONFIGURATION, configuration())
            );
            analysis.files().addFile(
                write(directory, fixture.file(), fixture.source()),
                JavaLanguageModule.getInstance()
            );
            return analysis.performAnalysisAndCollectReport();
        }
    }

    @SneakyThrows
    private static CPDReport inspectDuplication(List<Path> sources) {
        CPDConfiguration configuration = new CPDConfiguration();
        configuration.setDefaultLanguageVersion(JavaLanguageModule.getInstance().getVersion("25"));
        configuration.setMinimumTileSize(CPD_MINIMUM_TOKENS);
        List<CPDReport> reports = new ArrayList<>();
        try (CpdAnalysis analysis = CpdAnalysis.create(configuration)) {
            for (Path source : sources) {
                analysis.files().addFile(source, JavaLanguageModule.getInstance());
            }
            analysis.performAnalysis(reports::add);
        }
        return reports.stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("CPD returned no report after analysis"));
    }

    @SneakyThrows
    private static String configuration() {
        Path resource = SelfModules.repository().resolve(CONFIGURATION);
        assertTrue(Files.isRegularFile(resource), "the checked-in PMD configuration exists");
        return Files.readString(resource);
    }

    @SneakyThrows
    private static Path write(Path directory, String file, String source) {
        Path path = directory.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        return path;
    }

    private static List<String> describe(Report report) {
        return report.getViolations().stream()
            .map(violation -> "%s:%d".formatted(violation.getRule().getName(), violation.getBeginLine()))
            .toList();
    }

    private record Fixture(String file, String source, String rule, int line) {

        boolean matches(RuleViolation violation) {
            return this.rule.equals(violation.getRule().getName()) && this.line == violation.getBeginLine();
        }
    }
}
