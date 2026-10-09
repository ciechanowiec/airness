package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.reporting.Report;

@UtilityClass
final class PmdRule {

    private static final String CONFIGURATION
        = "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/pmd.xml";

    @SneakyThrows
    static Report inspect(Path directory, String name, String source) {
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setDefaultLanguageVersion(JavaLanguageModule.getInstance().getVersion("25"));
        configuration.setIgnoreIncrementalAnalysis(true);
        try (PmdAnalysis analysis = PmdAnalysis.create(configuration)) {
            RuleSet rules = analysis.newRuleSetLoader().loadFromString(
                CONFIGURATION, Files.readString(SelfModules.repository().resolve(CONFIGURATION))
            );
            Rule rule = Objects.requireNonNull(rules.getRuleByName(name), name);
            analysis.addRuleSet(RuleSet.forSingleRule(rule));
            analysis.files().addFile(Files.writeString(directory.resolve("Sample.java"), source));
            return analysis.performAnalysisAndCollectReport();
        }
    }
}
