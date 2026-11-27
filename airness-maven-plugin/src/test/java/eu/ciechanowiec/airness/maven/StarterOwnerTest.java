package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.LicenseCatalogue;
import java.time.Year;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StarterOwnerTest {

    private static final LicenseCatalogue LICENSES = SpdxLicenses.current();
    private static final Year YEAR = Year.of(2026);
    private static final String ORGANIZATION = "Sample Organization";
    private static final String URL = "https://git.sample.org/tracker";

    @Test
    void namesTheApplicationProprietaryUnlessALicenseIsGiven() {
        Properties properties = new Properties();
        properties.setProperty("airness.organization", ORGANIZATION);
        properties.setProperty("airness.url", URL);
        assertEquals(
            new StarterOwner(ORGANIZATION, URL, "LicenseRef-Proprietary", YEAR), StarterOwner.of(properties, YEAR)
        );
        properties.setProperty("airness.license", "Apache-2.0");
        assertEquals("Apache-2.0", StarterOwner.of(properties, YEAR).license());
    }

    @Test
    void admitsAnOwnerWithAnHttpsRepositoryAndACurrentLicense() {
        assertEquals(List.of(), new StarterOwner(ORGANIZATION, URL, "Apache-2.0", YEAR).problems(LICENSES));
    }

    @Test
    void refusesAnOwnerWithoutAnOrganization() {
        assertEquals(
            List.of("airness.organization is not set, but it must name who owns the application"),
            new StarterOwner(" ", URL, "LicenseRef-Proprietary", YEAR).problems(LICENSES)
        );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "http://git.sample.org/tracker", "https://git.sample.org/tracker/", "https://git.sample.org/tracker.git",
            "https://git.sample.org/a&b", "", "https://"
        }
    )
    void refusesEveryAddressThatIsNotABareHttpsRepository(String url) {
        assertEquals(
            List.of(
                "airness.url is '" + url + "', but it must be the https address of the application's repository, "
                    + "without a trailing slash or .git, such as https://git.sample.org/task-tracker"
            ),
            new StarterOwner(ORGANIZATION, url, "LicenseRef-Proprietary", YEAR).problems(LICENSES)
        );
    }

    @Test
    void refusesALicenseThatGivesTheCodeAway() {
        assertEquals(
            List.of(
                "airness.license is 'Unlicense', but Unlicense places the code in the public domain; name "
                    + "LicenseRef-Proprietary for code that is not given away"
            ),
            new StarterOwner(ORGANIZATION, URL, "Unlicense", YEAR).problems(LICENSES)
        );
    }

    @Test
    void clonesTheRepositoryFromItsAddress() {
        assertEquals(
            "scm:git:https://git.sample.org/tracker.git",
            new StarterOwner(ORGANIZATION, URL, "LicenseRef-Proprietary", YEAR).connection()
        );
    }
}
