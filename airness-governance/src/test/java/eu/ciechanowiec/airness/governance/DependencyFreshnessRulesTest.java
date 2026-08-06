package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The freshness policy fails a dependency two or more majors behind and passes one that is current or
 * a single major behind. A date-versioned dependency uses its leading {@code 20**} year as the major,
 * so it follows the same bound.
 */
class DependencyFreshnessRulesTest {

    private static final DeclaredCoordinate PICOCLI = new DeclaredCoordinate("info.picocli", "picocli", "4.7.7");
    private static final DeclaredCoordinate JSON = new DeclaredCoordinate("org.json", "json", "20260719");
    private static final int ONE_YEAR_AHEAD = 2027;
    private static final int TWO_YEARS_AHEAD = 2028;

    @Test
    void failsADependencyTwoMajorsBehind() {
        assertFalse(DependencyFreshnessRules.violation(update(PICOCLI, "6.0.0")).isEmpty());
    }

    @Test
    void passesADependencyOneMajorBehind() {
        assertTrue(DependencyFreshnessRules.violation(update(PICOCLI, "5.0.0")).isEmpty());
    }

    @Test
    void passesACurrentDependency() {
        assertTrue(DependencyFreshnessRules.violation(update(PICOCLI, "4.8.0")).isEmpty());
    }

    @Test
    void failsADateVersionedDependencyTwoYearsBehind() {
        assertTrue(DependencyFreshnessRules.hasComparableMajor(JSON.version()));
        assertFalse(DependencyFreshnessRules.violation(update(JSON, "%d0101".formatted(TWO_YEARS_AHEAD))).isEmpty());
    }

    @Test
    void passesADateVersionedDependencyOneYearBehind() {
        assertTrue(DependencyFreshnessRules.violation(update(JSON, "%d0101".formatted(ONE_YEAR_AHEAD))).isEmpty());
    }

    @Test
    void skipsAVersionWithoutAMajorOrLeadingYear() {
        DeclaredCoordinate named = new DeclaredCoordinate("example", "named", "release-2026");
        assertFalse(DependencyFreshnessRules.hasComparableMajor(named.version()));
    }

    @Test
    void comparesOnlyVersionsUsingTheDeclaredScheme() {
        assertTrue(DependencyFreshnessRules.sameScheme("20260719", "20280719"));
        assertFalse(DependencyFreshnessRules.sameScheme("20260719", "3.0.0"));
        assertFalse(DependencyFreshnessRules.sameScheme("3.0.0", "20280719"));
    }

    private static VersionUpdate update(DeclaredCoordinate coordinate, String latest) {
        return new VersionUpdate(new OwnedCoordinate("test:project", coordinate), latest);
    }
}
