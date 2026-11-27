package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectLicensesTest {

    private static final LicenseCatalogue LIST = new LicenseCatalogue(
        "3.29.0", Set.of("MIT-0", "Apache-2.0", "Unlicense")
    );

    @Test
    void admitsACurrentIdentifierAsTheListSpellsIt() {
        assertEquals(Optional.empty(), ProjectLicenses.refusal("Apache-2.0", LIST));
    }

    @Test
    void admitsTheProprietaryReference() {
        assertEquals(Optional.empty(), ProjectLicenses.refusal("LicenseRef-Proprietary", LIST));
    }

    @Test
    void asksForTheListsSpellingOfAnIdentifierInAnotherCase() {
        assertEquals(Optional.of("write mit-0 as MIT-0"), ProjectLicenses.refusal("mit-0", LIST));
    }

    @Test
    void asksForTheReservedSpellingOfTheProprietaryReference() {
        assertEquals(
            Optional.of("write licenseref-proprietary as LicenseRef-Proprietary"),
            ProjectLicenses.refusal("licenseref-proprietary", LIST)
        );
    }

    @Test
    void refusesTheUnlicenseInAnyCaseAlthoughTheListCarriesIt() {
        assertEquals(
            Optional.of(
                "Unlicense places the code in the public domain; name LicenseRef-Proprietary for code that is "
                    + "not given away"
            ),
            ProjectLicenses.refusal("Unlicense", LIST)
        );
        assertTrue(ProjectLicenses.refusal("UNLICENSE", LIST).isPresent());
    }

    @Test
    void refusesAReferenceOfTheProjectsOwn() {
        assertEquals(
            Optional.of(
                "LicenseRef-Mine is a license reference of the project's own; name LicenseRef-Proprietary or an SPDX "
                    + "identifier"
            ),
            ProjectLicenses.refusal("LicenseRef-Mine", LIST)
        );
    }

    @Test
    void refusesANameTheListDoesNotRecommendNamingItsVersion() {
        assertEquals(
            Optional.of(
                "GPL-2.0 is no current identifier of SPDX license list 3.29.0; name one, or LicenseRef-Proprietary"
            ),
            ProjectLicenses.refusal("GPL-2.0", LIST)
        );
    }

    @Test
    void refusesANameWithSurroundingSpaceAsTheListDoesNotSpellIt() {
        assertEquals(Optional.of("write  MIT-0  as MIT-0"), ProjectLicenses.refusal(" MIT-0 ", LIST));
    }

    @Test
    void refusesABlankName() {
        assertEquals(
            Optional.of("a license has no name; name it LicenseRef-Proprietary or by an SPDX identifier"),
            ProjectLicenses.refusal("  ", LIST)
        );
    }
}
