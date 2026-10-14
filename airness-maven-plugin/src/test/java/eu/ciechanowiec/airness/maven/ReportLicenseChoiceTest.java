package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportLicenseChoiceTest {

    @Test
    void preservesMultipleSupplierDeclarationsInTheLicenseArrayShape() {
        assertEquals(List.of(), ReportLicenseChoice.from(List.of(" ")));
        assertEquals(
            List.of(
                Map.of("license", Map.of("name", "MIT OR Apache-2.0")),
                Map.of("license", Map.of("id", "MPL-2.0"))
            ),
            ReportLicenseChoice.from(List.of("MIT OR Apache-2.0", "MPL-2.0"))
        );
    }

    @Test
    void recognizesGroupedExpressionsWithoutAcceptingUnbalancedOrEmptyGroups() {
        assertTrue(ReportSpdxSyntax.expression("(MIT OR Apache-2.0) AND MPL-2.0"));
        assertTrue(ReportSpdxSyntax.expression("GPL-2.0-only WITH Classpath-exception-2.0"));
        for (
            String invalid : List.of(
                "", "MIT", "AND MIT", "MIT OR", "(MIT", "MIT)", "()", "MIT (MIT)",
                "MIT OR ()", "UNKNOWN OR MIT", "SEE LICENSE IN LICENSE.txt", "MIT OR !invalid", ") AND MIT"
            )
        ) {
            assertFalse(ReportSpdxSyntax.expression(invalid), invalid);
        }
    }

    @Test
    void preservesSpdxIdentifiersAndCompoundExpressions() {
        assertEquals(Map.of("license", Map.of("id", "MIT")), ReportLicenseChoice.from(List.of("MIT")).getFirst());
        String expression = "MIT OR Apache-2.0";
        assertEquals(Map.of("expression", expression), ReportLicenseChoice.from(List.of(expression)).getFirst());
    }

    @Test
    void retainsSupplierNamesWithoutReplacingThemWithFuzzyMatches() {
        String declared = "The Apache Software License, Version 2.0";
        assertEquals(
            Map.of("license", Map.of("name", declared)), ReportLicenseChoice.from(List.of(declared)).getFirst()
        );
        String unknown = "Example supplier license";
        assertEquals(Map.of("license", Map.of("name", unknown)), ReportLicenseChoice.from(List.of(unknown)).getFirst());
    }

    @Test
    void refusesAnInventoryThatDoesNotSatisfyTheCycloneDxSchema() {
        assertThrows(IllegalStateException.class, () -> ReportSbom.validateAggregate("{}"));
    }
}
