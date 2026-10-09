package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import net.sourceforge.pmd.reporting.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PmdUpgradeTest {

    private static final List<Sample> SAMPLES = List.of(
        new Sample(
            "OnDemandImport", "import java.util.*;\nclass Sample {}",
            "import java.util.List;\nclass Sample {}", 1
        ),
        new Sample(
            "OnDemandImport", "import static org.junit.jupiter.api.Assertions.*;\nclass Sample {}",
            "import static org.junit.jupiter.api.Assertions.assertEquals;\nclass Sample {}", 1
        ),
        new Sample("TypeNameMismatch", "class Different {}", "class Sample {}", 1),
        new Sample(
            "CStyleArrayDeclaration", "class Sample { int values[]; }",
            "class Sample { int[] values; }", 1
        ),
        new Sample(
            "LongLiteralEndingWithLowercaseL", "class Sample { long value = 1l; }",
            "class Sample { long value = 1L; }", 1
        ),
        new Sample(
            "InternalApiUsage",
            """
                class Sample {
                    @interface TestOnly {}
                    @TestOnly int internal() { return 1; }
                    int value() { return internal(); }
                }
                """,
            """
                class Sample {
                    int internal() { return 1; }
                    int value() { return internal(); }
                }
                """, 4
        )
    );

    @Test
    void reportsEachNewRuleAtTheDefectiveSourceLocation(@TempDir Path directory) {
        for (Sample sample : SAMPLES) {
            Report report = PmdRule.inspect(directory, sample.rule(), sample.defective());
            assertTrue(report.getProcessingErrors().isEmpty(), () -> report.getProcessingErrors().toString());
            assertTrue(report.getConfigurationErrors().isEmpty(), () -> report.getConfigurationErrors().toString());
            assertEquals(1, report.getViolations().size(), () -> sample.rule() + ": " + report.getViolations());
            assertEquals(sample.rule(), report.getViolations().getFirst().getRule().getName());
            assertEquals(sample.line(), report.getViolations().getFirst().getBeginLine());
        }
    }

    @Test
    void acceptsEachRepairedSourceThroughTheSameConfiguredRule(@TempDir Path directory) {
        for (Sample sample : SAMPLES) {
            Report report = PmdRule.inspect(directory, sample.rule(), sample.repaired());
            assertTrue(report.getProcessingErrors().isEmpty(), () -> report.getProcessingErrors().toString());
            assertTrue(report.getConfigurationErrors().isEmpty(), () -> report.getConfigurationErrors().toString());
            assertTrue(report.getViolations().isEmpty(), () -> sample.rule() + ": " + report.getViolations());
        }
    }

    private record Sample(String rule, String defective, String repaired, int line) {
    }
}
