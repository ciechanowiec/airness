package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EarlyPmdTest {

    private static final String CONFIGURATION
        = "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/pmd.xml";

    @Test
    @SneakyThrows
    void agreesWithTheRetainedPmdRules(@TempDir Path directory) {
        List<EarlyAnalysisCase> samples = EarlyAnalysisCase.all().stream()
            .filter(sample -> "PMD".equals(sample.tool())).toList();
        for (EarlyAnalysisCase sample : samples) {
            Report report = inspect(directory, sample);
            assertTrue(report.getProcessingErrors().isEmpty(), () -> report.getProcessingErrors().toString());
            assertTrue(report.getConfigurationErrors().isEmpty(), () -> report.getConfigurationErrors().toString());
            assertEquals(
                sample.originalExpected(), report.getViolations().size(),
                () -> sample.rule() + " over " + sample.source() + ": " + report.getViolations()
            );
        }
    }

    @SneakyThrows
    private static Report inspect(Path directory, EarlyAnalysisCase sample) {
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setDefaultLanguageVersion(JavaLanguageModule.getInstance().getVersion("25"));
        configuration.setIgnoreIncrementalAnalysis(true);
        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            RuleSet rules = analysis.newRuleSetLoader().loadFromString(
                CONFIGURATION, Files.readString(SelfModules.repository().resolve(CONFIGURATION))
            );
            Rule rule = Objects.requireNonNull(rules.getRuleByName(sample.rule()), sample.rule());
            analysis.addRuleSet(RuleSet.forSingleRule(rule));
            analysis.files().addFile(Files.writeString(directory.resolve("Sample.java"), sample.source()));
            return analysis.performAnalysisAndCollectReport();
        }
    }
}
