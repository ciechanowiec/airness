package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariableDistanceCheckstyleTest {

    private static final String RULE = "VariableDeclarationUsageDistance";
    private static final String LOCAL = "int remembered = value;";
    private static final String DISTANT = """
        package example;
        final class Distance {
            int read(int value) {
                int remembered = value;
                first();
                second();
                third();
                fourth();
                return remembered;
            }
        }
        """;

    @Test
    void reportsDistanceWithoutRecommendingFinal(@TempDir Path directory) {
        List<Finding> findings = inspect(directory, DISTANT);
        assertTrue(findings.stream().anyMatch(finding -> finding.is(RULE)));
        assertFalse(findings.stream().anyMatch(finding -> finding.message().contains("making that variable final")));
    }

    @Test
    void acceptsALocalDeclaredNearItsUse(@TempDir Path directory) {
        String nearby = DISTANT.replace("        " + LOCAL + "\n", "")
            .replace("        return remembered;", "        " + LOCAL + "\n        return remembered;");
        assertFalse(inspect(directory, nearby).stream().anyMatch(finding -> finding.is(RULE)));
    }

    @Test
    void doesNotExemptFinalLocalsFromEitherRule(@TempDir Path directory) {
        List<Finding> findings = inspect(directory, DISTANT.replace(LOCAL, "final " + LOCAL));
        assertTrue(findings.stream().anyMatch(finding -> finding.is(RULE)));
        assertTrue(findings.stream().anyMatch(finding -> finding.message().contains("Local variables must not")));
        assertFalse(findings.stream().anyMatch(finding -> finding.message().contains("making that variable final")));
    }

    private static List<Finding> inspect(Path directory, String source) {
        return CheckstyleConfigurationTest.inspect(directory, new Fixture("Distance.java", source, RULE, 4), false);
    }
}
