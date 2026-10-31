package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The harness excuses frontend files only in the harness's own module that ships them.
 */
class HarnessFrontendTest {

    @Test
    void excusesTheComplianceReportAssetsOfTheGovernanceModule() {
        Map<String, String> excused = HarnessFrontend.excused("eu.ciechanowiec", "airness-governance");
        assertEquals(2, excused.size(), "the report's stylesheet and script are the only excused files");
        assertTrue(
            excused.keySet().stream().allMatch(path -> path.startsWith("src/main/resources/")),
            "each entry names a module-relative resource: " + excused
        );
    }

    @Test
    void excusesNothingInAnyOtherModule() {
        assertTrue(
            HarnessFrontend.excused("com.example", "airness-governance").isEmpty(), "a consumer is excused nothing"
        );
    }
}
