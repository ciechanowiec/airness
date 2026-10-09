package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import net.sourceforge.pmd.reporting.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EarlyPmdTest {

    @Test
    void agreesWithTheRetainedPmdRules(@TempDir Path directory) {
        List<EarlyAnalysisCase> samples = EarlyAnalysisCase.all().stream()
            .filter(sample -> "PMD".equals(sample.tool())).toList();
        for (EarlyAnalysisCase sample : samples) {
            Report report = PmdRule.inspect(directory, sample.rule(), sample.source());
            assertTrue(report.getProcessingErrors().isEmpty(), () -> report.getProcessingErrors().toString());
            assertTrue(report.getConfigurationErrors().isEmpty(), () -> report.getConfigurationErrors().toString());
            assertEquals(
                sample.originalExpected(), report.getViolations().size(),
                () -> sample.rule() + " over " + sample.source() + ": " + report.getViolations()
            );
        }
    }
}
