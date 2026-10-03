package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class EarlyInventoryTest {

    private static final String INVENTORY = "airness-governance/src/test/resources/analysis-coverage.tsv";
    private static final String PMD_RULES
        = "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/pmd.xml";
    private static final String PROFILE = "airness-assets/src/main/resources/qodana/profile.xml";

    @Test
    @SneakyThrows
    void inventoriesTheExpandedPmdCategoriesIncludingCustomRules() {
        List<String> lines = Files.readAllLines(SelfModules.repository().resolve(INVENTORY));
        Set<String> recorded = lines.stream().filter(line -> line.startsWith("PMD\t"))
            .map(EarlyInventoryTest::ruleName).collect(Collectors.toSet());
        try (PmdAnalysis analysis = PmdAnalysis.create(new PMDConfiguration())) {
            RuleSet rules = analysis.newRuleSetLoader().loadFromString(
                PMD_RULES, Files.readString(SelfModules.repository().resolve(PMD_RULES))
            );
            Set<String> enabled = rules.getRules().stream().map(Rule::getName).collect(Collectors.toSet());
            assertEquals(enabled, recorded, "a category or version change requires a fresh coverage audit");
        }
    }

    @Test
    @SneakyThrows
    void keepsTheOriginalQodanaCounterpartsEnabled() {
        Document profile = Xml.parse(Files.readString(SelfModules.repository().resolve(PROFILE)));
        Set<String> enabled = ProjectFiles.descendants(profile, "inspection_tool")
            .filter(inspection -> "true".equals(inspection.getAttribute("enabled")))
            .map(inspection -> inspection.getAttribute("class")).collect(Collectors.toSet());
        Set<String> mirrored = EarlyAnalysisCase.all().stream().filter(sample -> "Qodana".equals(sample.tool()))
            .map(EarlyAnalysisCase::rule).collect(Collectors.toSet());
        assertTrue(enabled.containsAll(mirrored), "every new counterpart retains its original inspection");
    }

    @Test
    @SneakyThrows
    void pinsTheAuditToTheConfiguredAnalyzerVersions() {
        String inventory = Files.readString(SelfModules.repository().resolve(INVENTORY));
        for (String property : List.of("qodana.image", "pmd.version", "checkstyle.version")) {
            assertTrue(
                inventory.contains(ProjectFiles.property(ProjectFiles.rootPom(), property)),
                () -> property + " changed: refresh the analyzer inventory and differential evidence"
            );
        }
    }

    @Test
    @SneakyThrows
    void preservesTheQodanaThresholdsAndFieldExemptions() {
        Document profile = Xml.parse(Files.readString(SelfModules.repository().resolve(PROFILE)));
        assertEquals("7", option(profile, "FieldCount", "m_limit"));
        assertEquals("false", option(profile, "FieldCount", "m_countConstantFields"));
        assertEquals("true", option(profile, "FieldCount", "m_considerStaticFinalFieldsConstant"));
        assertEquals("false", option(profile, "FieldCount", "myCountEnumConstants"));
        assertEquals("5", option(profile, "ConstructorCount", "m_limit"));
        assertEquals("false", option(profile, "ConstructorCount", "ignoreDeprecatedConstructors"));
        assertEquals("2", option(profile, "ClassNestingDepth", "m_limit"));
        assertEquals("3", option(profile, "IfStatementWithTooManyBranches", "m_limit"));
        assertEquals("15", option(profile, "AnonymousClassMethodCount", "m_limit"));
        assertEquals("80", option(profile, "ClassComplexity", "m_limit"));
        assertEquals("true", option(profile, "ThreeNegationsPerMethod", "m_ignoreInEquals"));
        assertEquals("false", option(profile, "ThreeNegationsPerMethod", "ignoreInAssert"));
    }

    @Test
    @SneakyThrows
    void keepsTheQuestionableNamesListIdentical() {
        Path configuration = SelfModules.repository().resolve(
            "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/checkstyle.xml"
        );
        Document profile = Xml.parse(Files.readString(SelfModules.repository().resolve(PROFILE)));
        String names = option(profile, "QuestionableName", "nameString");
        String checkstyle = Files.readString(configuration);
        assertTrue(checkstyle.contains("@text=('" + names.replace(",", "','") + "')"));
    }

    private static String option(Document profile, String rule, String name) {
        Element inspection = ProjectFiles.descendants(profile, "inspection_tool")
            .filter(candidate -> rule.equals(candidate.getAttribute("class"))).findFirst().orElseThrow();
        return Xml.children(inspection, "option").stream()
            .filter(candidate -> name.equals(candidate.getAttribute("name")))
            .map(candidate -> candidate.getAttribute("value")).findFirst().orElseThrow();
    }

    private static String ruleName(String line) {
        int start = line.indexOf('\t') + 1;
        int end = line.indexOf('\t', start);
        return line.substring(start, end);
    }
}
