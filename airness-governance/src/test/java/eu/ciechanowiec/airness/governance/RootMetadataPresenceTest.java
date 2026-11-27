package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RootMetadataPresenceTest {

    private static final String MISSING = "rather than inherit";

    @Test
    void acceptsARootPomThatStatesEverythingItsOwnWay() {
        assertTrue(Verdicts.clean(RootMetadataFixtures.findings(RootMetadataFixtures.clean())));
    }

    @Test
    void namesEveryElementACoordinatesOnlyRootPomLeavesToItsParent() {
        ProjectMetadata bare = RootMetadataFixtures.metadata(Map.of());
        assertEquals(
            List.of(
                "<description> is not declared", "<developers> is not declared", "<inceptionYear> is not declared",
                "<licenses> is not declared", "<name> is not declared", "<scm/connection> is not declared",
                "<scm/developerConnection> is not declared", "<scm/url> is not declared", "<url> is not declared"
            ),
            Verdicts.offences(
                RootMetadataFixtures.judged(bare, RootMetadataFixtures.AIRNESS, Optional.empty()),
                MISSING
            )
        );
    }

    @Test
    void judgesPresenceByTheRawPomEvenWhenTheEffectiveModelHoldsAnInheritedValue() {
        ProjectMetadata declared = RootMetadataFixtures.with("developerConnection", "");
        assertEquals(
            List.of("<scm/developerConnection> is not declared"),
            Verdicts.offences(
                RootMetadataFixtures.judged(declared, RootMetadataFixtures.clean(), Optional.empty()),
                MISSING
            )
        );
    }

    @Test
    void requiresADeveloperEvenBesideAnOrganization() {
        assertEquals(
            List.of("<developers> is not declared"),
            Verdicts.offences(RootMetadataFixtures.findings(RootMetadataFixtures.with("developers", "")), MISSING)
        );
    }

    @Test
    void leavesTheValueRulesSilentForAValueThePomDoesNotDeclare() {
        ProjectMetadata declared = RootMetadataFixtures.with("url", "");
        ProjectMetadata effective = RootMetadataFixtures.with("url", "http://inherited.sample.org/x");
        assertEquals(
            List.of(),
            Verdicts.offences(
                RootMetadataFixtures.judged(declared, effective, Optional.empty()), "https address"
            )
        );
    }

    @Test
    void refusesMetadataCopiedFromAnAirnessPom() {
        ProjectMetadata copied = RootMetadataFixtures.metadata(
            Map.of(
                "name", "Task Tracker",
                "description", "The reusable Java  build and repository governance harness",
                "url", "https://github.com/ciechanowiec/airness",
                "inceptionYear", "2025",
                "licenses", "MIT-0",
                "developers", "herman ciechanowiec",
                "scmUrl", "https://github.com/ciechanowiec/airness",
                "connection", "scm:git:https://github.com/ciechanowiec/airness.git",
                "developerConnection", "scm:git:git@github.com:ciechanowiec/airness.git"
            )
        );
        assertEquals(
            List.of(
                "<description> repeats Airness's own", "<developer> herman ciechanowiec is Airness's own",
                "<scm/connection> repeats Airness's own", "<scm/developerConnection> repeats Airness's own",
                "<scm/url> repeats Airness's own", "<url> repeats Airness's own"
            ),
            Verdicts.offences(RootMetadataFixtures.findings(copied), "copy Airness").stream().sorted().toList()
        );
    }

    @Test
    void admitsTheLicenseAirnessHappensToCarry() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                RootMetadataFixtures.findings(RootMetadataFixtures.with("licenses", "MIT-0")), "copy Airness"
            )
        );
    }
}
