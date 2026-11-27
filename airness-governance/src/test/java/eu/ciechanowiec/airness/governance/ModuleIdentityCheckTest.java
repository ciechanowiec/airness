package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleIdentityCheckTest {

    private static final String MODEL = "4.0.0";
    private static final String SAMPLE = "org.sample";
    private static final String WEB = "web";
    private static final String RELEASE = "1.0.0";
    private static final String GROUP = "group";

    @Test
    void acceptsAModuleNamedAfterItsDirectory() {
        assertTrue(Verdicts.clean(findings(module(SAMPLE, WEB, RELEASE))));
    }

    @Test
    void exemptsTheRootFromItsDirectoryName() {
        assertTrue(
            Verdicts.clean(findings(new ModuleIdentity(MODEL, SAMPLE, "tracker", "1.0.0-SNAPSHOT", "ws", true)))
        );
    }

    @Test
    void refusesAModelVersionMavenThreeDoesNotRead() {
        assertEquals(
            List.of("org.sample:web writes modelVersion '4.1.0'"),
            Verdicts.offences(findings(new ModuleIdentity("4.1.0", SAMPLE, WEB, RELEASE, WEB, false)), "modelVersion")
        );
    }

    @Test
    void refusesAGroupThatIsNoReverseDomain() {
        assertEquals(
            List.of("sample:web: groupId sample is not a reverse domain of lower-case segments"),
            Verdicts.offences(findings(module("sample", WEB, RELEASE)), GROUP)
        );
        assertEquals(
            List.of("org.Sample:web: groupId org.Sample is not a reverse domain of lower-case segments"),
            Verdicts.offences(findings(module("org.Sample", WEB, RELEASE)), GROUP)
        );
    }

    @Test
    void refusesAGroupUnderAnExampleDomain() {
        assertEquals(
            List.of("com.example:web: groupId com.example names an example domain, not an owner"),
            Verdicts.offences(findings(module("com.example", WEB, RELEASE)), GROUP)
        );
        assertEquals(
            List.of("org.example.shop:web: groupId org.example.shop names an example domain, not an owner"),
            Verdicts.offences(findings(module("org.example.shop", WEB, RELEASE)), GROUP)
        );
    }

    @Test
    void refusesAnArtifactOfAnotherShapeOrNamedApartFromItsDirectory() {
        assertEquals(
            List.of(
                "org.sample:Web_App: artifactId Web_App is not lower-case words joined by hyphens",
                "org.sample:Web_App: artifactId Web_App differs from its directory web"
            ),
            Verdicts.offences(findings(module(SAMPLE, "Web_App", RELEASE)), "artifact")
        );
    }

    @Test
    void refusesAVersionThatIsNotMajorMinorPatch() {
        assertEquals(
            List.of("org.sample:web: version 1.0.0-RC1 is not major.minor.patch"),
            Verdicts.offences(findings(module(SAMPLE, WEB, "1.0.0-RC1")), "major.minor.patch")
        );
    }

    // A module below the root, in the directory web.
    private static ModuleIdentity module(String group, String artifact, String version) {
        return new ModuleIdentity(MODEL, group, artifact, version, WEB, false);
    }

    private static List<Findings> findings(ModuleIdentity identity) {
        return new ModuleIdentityCheck(identity).findings();
    }
}
