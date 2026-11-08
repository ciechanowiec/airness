package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.ReportFixtures.dependency;
import static eu.ciechanowiec.airness.governance.ReportFixtures.report;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LicenseClassTest {

    @Test
    void ordersTheClassesFromPermissiveToOutsidePolicy() {
        assertEquals(
            List.of(LicenseClass.PERMISSIVE, LicenseClass.WEAK_COPYLEFT, LicenseClass.OUTSIDE_POLICY),
            List.of(LicenseClass.values())
        );
    }

    @Test
    void pairsEachReadableLabelWithTheKeyTheReportStylesItBy() {
        assertEquals("Permissive", LicenseClass.PERMISSIVE.label());
        assertEquals("permissive", LicenseClass.PERMISSIVE.key());
        assertEquals("Weak copyleft", LicenseClass.WEAK_COPYLEFT.label());
        assertEquals("weak-copyleft", LicenseClass.WEAK_COPYLEFT.key());
        assertEquals("Outside policy", LicenseClass.OUTSIDE_POLICY.label());
        assertEquals("refused", LicenseClass.OUTSIDE_POLICY.key());
    }

    @Test
    void rendersEveryClassAsAFilterOptionLegendEntryAndChip() {
        String html = ComplianceReportHtml.render(
            report(List.of(dependency("MIT"), dependency("MPL-2.0"), dependency("Proprietary")), List.of())
        );
        for (LicenseClass licenseClass : LicenseClass.values()) {
            assertTrue(
                html.contains("<option value=\"" + licenseClass.key() + "\">" + licenseClass.label() + "</option>"),
                licenseClass::label
            );
            assertTrue(html.contains("<li class=\"key-" + licenseClass.key() + "\">" + licenseClass.label() + ": 1 ("));
            String chip = "<span class=\"chip chip-" + licenseClass.key() + "\">";
            assertTrue(html.contains(chip + licenseClass.label() + "</span>"));
        }
    }

    @Test
    void exportsTheReadableLabelToTheCsv() {
        String csv = report(List.of(dependency("MPL-2.0")), List.of()).csv();
        assertTrue(csv.contains(",\"Weak copyleft\","));
    }
}
