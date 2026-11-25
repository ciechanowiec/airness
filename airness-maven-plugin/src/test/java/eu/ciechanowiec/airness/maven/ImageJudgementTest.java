package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A finding of a shipped image fails only where the project can repair it: a system package of the base once a newer
 * release of the base ships the fix, which the failure names, a package the image's build installed once its
 * distribution publishes a fix, and a language package of the base like any other package. Every other finding is
 * reported with the harness's account of why it does not fail.
 */
class ImageJudgementTest {

    private static final String MODULE = "com.example:shop:pom:1";
    private static final String BASE = "node:26.8.2-alpine@sha256:" + "a".repeat(64);
    private static final String REBUILT = "node:26.8.2-alpine@sha256:" + "b".repeat(64);
    private static final String NEWER = "node:26.13.1-alpine@sha256:" + "c".repeat(64);
    private static final String OTHER = "node:26.9.0-alpine@sha256:" + "d".repeat(64);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final String CURL_PACKAGE = "curl";
    private static final String OPENSSL = ImageDocuments.apk("libcrypto3", "openssl", "3.5.7-r0");
    private static final String ZLIB = ImageDocuments.apk("zlib", "zlib", "1.3.2-r0");
    private static final String CURL = ImageDocuments.apk(CURL_PACKAGE, CURL_PACKAGE, "8.17.0-r0");
    private static final String UNDICI = ImageDocuments.npm("undici", "6.28.0");
    private static final String GLOB = ImageDocuments.npm("glob", "13.0.6");
    private static final ImageInventory IMAGE = ImageInventory.of(
        ImageDocuments.document("alpine", "3.24.1", OPENSSL, ZLIB, CURL, UNDICI, GLOB)
    );
    private static final ImageInventory IN_BASE = ImageInventory.of(
        ImageDocuments.document("alpine", "3.24.1", OPENSSL, ZLIB, UNDICI, GLOB)
    );
    private static final ImageEvidence EVIDENCE = new ImageEvidence(
        "Dockerfile", Path.of("Dockerfile"), ImageDocuments.document("alpine", "3.24.1")
    );

    private static final OsvAdvisory OPENSSL_FIXED = system("libcrypto3", "openssl", "CVE-2026-14456", true);
    private static final OsvAdvisory ZLIB_FIXED = system("zlib", "zlib", "CVE-2026-85091", true);
    private static final OsvAdvisory ZLIB_UNFIXED = system("zlib", "zlib", "CVE-2026-4738", false);
    private static final OsvAdvisory CURL_FIXED = system(CURL_PACKAGE, CURL_PACKAGE, "CVE-2026-1000", true);
    private static final OsvAdvisory CURL_UNFIXED = system(CURL_PACKAGE, CURL_PACKAGE, "CVE-2026-1001", false);
    private static final OsvAdvisory UNDICI_FINDING = language("undici", "6.28.0", "GHSA-undici");
    private static final OsvAdvisory GLOB_FINDING = language("glob", "13.0.6", "GHSA-glob");
    private static final List<OsvAdvisory> FINDINGS = List.of(
        OPENSSL_FIXED, ZLIB_FIXED, ZLIB_UNFIXED, CURL_FIXED, CURL_UNFIXED, UNDICI_FINDING, GLOB_FINDING
    );

    // The rebuild of the same tag drops the fixed openssl and undici findings. The newer release answers openssl alone.
    private static final ImageCandidate REBUILD = carrying(REBUILT, ZLIB_FIXED, GLOB_FINDING);
    private static final ImageCandidate NEWER_RELEASE = carrying(NEWER, ZLIB_FIXED, GLOB_FINDING, UNDICI_FINDING);

    @Test
    void looksUpReleasesOnlyForFindingsANewerBaseCouldAnswer() {
        assertEquals(
            List.of(OPENSSL_FIXED, ZLIB_FIXED, UNDICI_FINDING, GLOB_FINDING), judgement(Optional.of(IN_BASE))
                .answerable(FINDINGS),
            "an unfixed system finding has no release to wait for, and the build's own packages have none at all"
        );
    }

    @Test
    void failsABaseFindingTheReleaseThatAnswersMostFixesAndNamesThatRelease() {
        ImageVerdict verdict = judgement(Optional.of(IN_BASE)).judge(
            FINDINGS, List.of(NEWER_RELEASE, REBUILD)
        );
        assertAll(
            () -> assertTrue(reported(verdict.system(), "CVE-2026-14456").open(), "the newer base ships openssl's fix"),
            () -> assertEquals(
                MODULE + " Dockerfile: replace FROM " + BASE + " with FROM " + REBUILT + ", which ships the fix for"
                    + " CVE-2026-14456, GHSA-undici. Upgrading packages inside the Dockerfile is refused; a newer"
                    + " release of the base is the repair.",
                verdict.repairs().getFirst().text()
            ),
            () -> assertEquals(
                Set.of(OPENSSL_FIXED.advisory().key(), UNDICI_FINDING.advisory().key()), verdict.repairs().getFirst()
                    .findings()
            )
        );
    }

    @Test
    void accountsForABaseFindingNoReleaseAnswersYetAndOneTheDistributionHasNotFixed() {
        ImageVerdict verdict = judgement(Optional.of(IN_BASE)).judge(FINDINGS, List.of(REBUILD));
        ReportMitigation awaiting = reported(verdict.system(), "CVE-2026-85091").mitigation().orElseThrow();
        ReportMitigation unfixed = reported(verdict.system(), "CVE-2026-4738").mitigation().orElseThrow();
        assertAll(
            () -> assertEquals(
                "Alpine has published a fixed version of zlib, but no newer release of " + BASE
                    + " ships it yet, and the"
                    + " image's own build may not upgrade what its base holds.",
                awaiting.reason()
            ),
            () -> assertEquals(
                "The check fails once a newer release of " + BASE
                    + " ships the fixed version, and names that release as the"
                    + " repair.",
                awaiting.plan()
            ),
            () -> assertEquals(
                "Alpine has published no fixed version of zlib, so no image built on it can ship one.", unfixed.reason()
            ),
            () -> assertTrue(unfixed.plan().contains("a newer release of " + BASE + " ships it"), unfixed.plan()),
            () -> assertEquals("2026-11-15", awaiting.addedOn()),
            () -> assertTrue(awaiting.harness() && unfixed.harness(), "the harness answers for its own accounts"),
            () -> assertEquals(ReportMitigation.Kind.AWAITING_FIX, awaiting.kind(), "no fix is a mitigation"),
            () -> assertEquals(ReportMitigation.Kind.AWAITING_FIX, unfixed.kind())
        );
    }

    @Test
    void failsAPackageTheBuildInstalledOnceItsDistributionHasAFix() {
        ImageVerdict verdict = judgement(Optional.of(IN_BASE)).judge(FINDINGS, List.of());
        assertAll(
            () -> assertTrue(reported(verdict.system(), "CVE-2026-1000").open()),
            () -> assertTrue(
                reported(verdict.system(), "CVE-2026-1001").mitigation().orElseThrow().plan().contains(
                    "Dockerfile then installs by raising"
                ),
                "an unfixed package the build installed waits for its distribution, not for the base"
            ),
            () -> assertEquals(
                List.of(
                    MODULE
                        + " Dockerfile: curl 8.17.0-r0, which the Dockerfile installs itself, has a fixed version in"
                        + " Alpine for CVE-2026-1000; raise the version its install line pins."
                ),
                verdict.repairs().stream().map(ImageRepair::text).filter(text -> text.contains(CURL_PACKAGE)).toList()
            )
        );
    }

    @Test
    void namesTheRemovalOfABaseLanguagePackageNoReleaseAnswers() {
        ImageVerdict verdict = judgement(Optional.of(IN_BASE)).judge(FINDINGS, List.of(REBUILD));
        assertAll(
            () -> assertEquals(List.of(UNDICI_FINDING, GLOB_FINDING), verdict.language()),
            () -> assertEquals(
                List.of(
                    MODULE + " Dockerfile: pkg:npm/glob@13.0.6 comes with the base image " + BASE
                        + ", and no newer release of it ships the fix for GHSA-glob; remove it from the final stage if"
                        + " the image does not use it, or, for an npm package, record an airness.vulnerabilityAllowlist"
                        + " entry."
                ),
                verdict.repairs().stream().map(ImageRepair::text).filter(text -> text.contains("glob")).toList()
            )
        );
    }

    @Test
    void prefersTheFirstListedReleaseWhenTwoAnswerAlike() {
        ImageCandidate other = carrying(OTHER, ZLIB_FIXED, GLOB_FINDING);
        ImageVerdict verdict = judgement(Optional.of(IN_BASE)).judge(FINDINGS, List.of(REBUILD, other));
        assertTrue(
            verdict.repairs().getFirst().text().contains("with FROM " + REBUILT), verdict.repairs().getFirst().text()
        );
    }

    @Test
    void treatsEveryPackageAsTheBuildsOwnWhenTheBaseCannotBeRead() {
        ImageVerdict verdict = judgement(Optional.empty()).judge(
            List.of(OPENSSL_FIXED, GLOB_FINDING), List.of(REBUILD)
        );
        assertAll(
            () -> assertTrue(verdict.system().getFirst().open()),
            () -> assertEquals(
                List.of(
                    MODULE
                        + " Dockerfile: libcrypto3 3.5.7-r0, which the Dockerfile installs itself, has a fixed version"
                        + " in Alpine for CVE-2026-14456; raise the version its install line pins."
                ),
                verdict.repairs().stream().map(ImageRepair::text).toList()
            )
        );
    }

    @Test
    void refusesAFindingOfAPackageTheImageDoesNotHold() {
        OsvAdvisory stranger = language("left-pad", "1.3.0", "GHSA-stranger");
        assertThrows(
            IllegalStateException.class, () -> judgement(Optional.of(IN_BASE)).judge(List.of(stranger), List.of())
        );
    }

    @Test
    void judgesAnImageWithoutFindingsToNothing() {
        assertEquals(ImageVerdict.none(), judgement(Optional.of(IN_BASE)).judge(List.of(), List.of(REBUILD)));
    }

    private static ImageJudgement judgement(Optional<ImageInventory> inBase) {
        return new ImageJudgement(MODULE, EVIDENCE, IMAGE, inBase.map(_ -> BASE), inBase, TODAY);
    }

    private static ReportAdvisory reported(List<ReportAdvisory> advisories, String identity) {
        return advisories.stream().filter(advisory -> advisory.identity().equals(identity)).findFirst().orElseThrow();
    }

    private static ImageCandidate carrying(String reference, OsvAdvisory... carried) {
        return ImageCandidate.of(reference, IN_BASE, List.of(carried));
    }

    private static OsvAdvisory system(String name, String source, String id, boolean fixed) {
        ImagePackage held = IMAGE.packages().stream().filter(entry -> entry.name().equals(name)).findFirst()
            .orElseThrow();
        OsvQuery query = OsvQuery.system(held.purl(), "Alpine:v3.24", source, held.sourceVersion());
        String event = fixed ? ", {\"fixed\": \"99-r0\"}" : "";
        return OsvAdvisory.of(
            ReportJson.parse(
                "{\"id\": \"" + id + "\", \"summary\": \"" + source
                    + " defect\", \"affected\": [{\"package\": {\"ecosystem\":"
                    + " \"Alpine:v3.24\", \"name\": \"" + source
                    + "\"}, \"ranges\": [{\"type\": \"ECOSYSTEM\", \"events\":"
                    + " [{\"introduced\": \"0\"}" + event + "]}]}]}"
            ), query, MODULE
        );
    }

    private static OsvAdvisory language(String name, String version, String id) {
        return OsvAdvisory.of(
            ReportJson.parse("{\"id\": \"" + id + "\", \"summary\": \"" + name + " defect\"}"),
            "pkg:npm/" + name + '@' + version, MODULE
        );
    }
}
