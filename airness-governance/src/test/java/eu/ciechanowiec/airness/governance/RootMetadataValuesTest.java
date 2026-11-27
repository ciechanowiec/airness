package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RootMetadataValuesTest {

    private static final String LICENSES = "current SPDX identifier";
    private static final String ADDRESSES = "https address";
    private static final String INCEPTION = "inceptionYear";
    private static final String WORDING = "own words";

    @Test
    void refusesEachLicenseNameTheRulesRefuse() {
        assertEquals(
            List.of(
                "Unlicense places the code in the public domain; name LicenseRef-Proprietary for code that is not "
                    + "given away",
                "write apache-2.0 as Apache-2.0"
            ),
            Verdicts.offences(
                RootMetadataFixtures.findings(RootMetadataFixtures.with("licenses", "Unlicense,apache-2.0,MIT-0")),
                LICENSES
            )
        );
    }

    @Test
    void judgesALicenseByWhatItsPropertyResolvesTo() {
        ProjectMetadata declared = RootMetadataFixtures.with("licenses", "${project.license}");
        ProjectMetadata effective = RootMetadataFixtures.with("licenses", "GPL-2.0");
        assertEquals(
            List.of(
                "GPL-2.0 is no current identifier of SPDX license list 3.29.0; name one, or LicenseRef-Proprietary"
            ),
            Verdicts.offences(RootMetadataFixtures.findings(declared, effective, Optional.empty()), LICENSES)
        );
    }

    @Test
    void refusesAnAddressThatIsNotHttps() {
        ProjectMetadata plain = RootMetadataFixtures.with("url", "http://git.sample.org/tracker");
        assertEquals(
            List.of(
                "url http://git.sample.org/tracker is not an https address",
                "scm/url https://git.sample.org/tracker differs from url http://git.sample.org/tracker"
            ),
            Verdicts.offences(RootMetadataFixtures.findings(plain), ADDRESSES)
        );
    }

    @Test
    void admitsAnHttpsSchemeInAnyCase() {
        ProjectMetadata upper = RootMetadataFixtures.metadata(
            Map.of(
                "url", "HTTPS://git.sample.org/tracker", "scmUrl", "HTTPS://git.sample.org/tracker",
                "connection", "scm:git:https://git.sample.org/tracker.git"
            )
        );
        assertEquals(List.of(), Verdicts.offences(RootMetadataFixtures.findings(upper), ADDRESSES));
    }

    @Test
    void refusesASourceAddressThatDiffersOnlyByATrailingSlash() {
        assertEquals(
            List.of("scm/url https://git.sample.org/tracker/ differs from url https://git.sample.org/tracker"),
            Verdicts.offences(
                RootMetadataFixtures.findings(RootMetadataFixtures.with("scmUrl", "https://git.sample.org/tracker/")),
                ADDRESSES
            )
        );
    }

    @Test
    void refusesAConnectionGitDoesNotRead() {
        assertEquals(
            List.of("scm/connection scm:svn:https://svn.sample.org/tracker does not start with scm:git:"),
            Verdicts.offences(
                RootMetadataFixtures.findings(
                    RootMetadataFixtures.with("connection", "scm:svn:https://svn.sample.org/tracker")
                ),
                ADDRESSES
            )
        );
        assertEquals(
            List.of("scm/developerConnection git@git.sample.org:tracker.git does not start with scm:git:"),
            Verdicts.offences(
                RootMetadataFixtures.findings(
                    RootMetadataFixtures.with("developerConnection", "git@git.sample.org:tracker.git")
                ),
                ADDRESSES
            )
        );
    }

    @Test
    void refusesAnInceptionYearThatIsNoYear() {
        assertEquals(
            List.of("inceptionYear 202 is not a four-digit year"),
            Verdicts.offences(RootMetadataFixtures.findings(RootMetadataFixtures.with(INCEPTION, "202")), INCEPTION)
        );
    }

    @Test
    void refusesAnInceptionYearAfterThisYearAndAfterTheFirstCommit() {
        assertEquals(
            List.of(
                "inceptionYear 2027 is after this year, 2026",
                "inceptionYear 2027 is after the repository's first commit, made in 2025"
            ),
            Verdicts.offences(RootMetadataFixtures.findings(RootMetadataFixtures.with(INCEPTION, "2027")), INCEPTION)
        );
    }

    @Test
    void admitsAnInceptionYearBeforeTheFirstCommitAndSkipsAnUnreadableHistory() {
        ProjectMetadata early = RootMetadataFixtures.with(INCEPTION, "2019");
        ProjectMetadata late = RootMetadataFixtures.with(INCEPTION, "2026");
        assertEquals(List.of(), Verdicts.offences(RootMetadataFixtures.findings(early), INCEPTION));
        assertEquals(
            List.of(), Verdicts.offences(RootMetadataFixtures.findings(late, late, Optional.empty()), INCEPTION)
        );
        assertEquals(
            List.of("inceptionYear 2026 is after the repository's first commit, made in 2025"),
            Verdicts.offences(
                RootMetadataFixtures.findings(late, late, Optional.of(RootMetadataFixtures.FIRST_COMMIT)), INCEPTION
            )
        );
    }

    @Test
    void refusesAPlaceholderNobodyReplaced() {
        assertEquals(
            List.of("name @NAME@ holds a placeholder nobody replaced"),
            Verdicts.offences(RootMetadataFixtures.findings(RootMetadataFixtures.with("name", "@NAME@")), WORDING)
        );
        assertEquals(
            List.of("description Made by @ORGANIZATION@. holds a placeholder nobody replaced"),
            Verdicts.offences(
                RootMetadataFixtures.findings(RootMetadataFixtures.with("description", "Made by @ORGANIZATION@.")),
                WORDING
            )
        );
    }

    @Test
    void refusesADescriptionThatRepeatsTheNameInAnyCaseOrSpacing() {
        assertEquals(
            List.of("description task  TRACKER repeats the name"),
            Verdicts.offences(
                RootMetadataFixtures.findings(RootMetadataFixtures.with("description", "task  TRACKER")), WORDING
            )
        );
    }
}
