package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class InheritedMetadataCheckTest {

    @Test
    void refusesOnlyTheFieldsAnAirnessPomSupplies() {
        List<MetadataSource> sources = List.of(
            new MetadataSource("licenses", "eu.ciechanowiec:airness", true),
            new MetadataSource("url", "org.sample:tracker", false),
            new MetadataSource("developers", "org.sample:company-parent", false),
            new MetadataSource("scm/developerConnection", "eu.ciechanowiec:airness", true)
        );
        assertEquals(
            List.of(
                "<licenses> comes from eu.ciechanowiec:airness",
                "<scm/developerConnection> comes from eu.ciechanowiec:airness"
            ),
            Verdicts.offences(new InheritedMetadataCheck(sources).findings(), "inherit from Airness")
        );
    }

    @Test
    void acceptsAModuleWhoseMetadataIsAllItsOwners() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                new InheritedMetadataCheck(List.of(new MetadataSource("licenses", "org.sample:web", false))).findings(),
                "inherit from Airness"
            )
        );
    }
}
