package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportImagePackage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every image a module's node projects built is looked up in OSV, its base and the base's releases are listed only
 * when a finding could be answered by one, and a failure names the release that answers it.
 */
class ImageReportInputsTest {

    private static final String BASE = "node:26.8.2-alpine@sha256:" + "a".repeat(64);
    private static final String REBUILT = "node:26.8.2-alpine@sha256:" + "b".repeat(64);
    private static final String NEWER = "node:26.13.1-alpine@sha256:" + "c".repeat(64);
    private static final String OPENSSL = ImageDocuments.apk("libcrypto3", "openssl", "3.5.7-r0");
    private static final String PATCHED = ImageDocuments.apk("libcrypto3", "openssl", "3.5.8-r0");
    private static final String GLOB = ImageDocuments.npm("glob", "13.0.6");
    private static final String GLOB_QUERY = "pkg:npm/glob@13.0.6";
    private static final String OPENSSL_QUERY = "Alpine:v3.24 openssl 3.5.7-r0";
    private static final String CVE = "CVE-2026-14456";
    private static final String ADVISORY
        = """
            {"id": "%s", "summary": "Heap overflow", "affected": [{"package": {"ecosystem": "Alpine:v3.24",
             "name": "openssl"}, "ranges": [{"type": "ECOSYSTEM",
             "events": [{"introduced": "0"}, {"fixed": "3.5.8-r0"}]}]}]}
            """
            .formatted(CVE);
    private static final String GLOB_ADVISORY = "{\"id\": \"GHSA-glob\", \"summary\": \"Path traversal\"}";
    private static final String SCANNER = "aquasec/trivy:0.76.2@sha256:" + "d".repeat(64);
    private static final String ALPINE = "alpine";
    private static final String RELEASE = "3.24.1";
    private static final String SAVE = "save";
    private static final String INSPECT = "inspect";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    private Path directory;

    @Test
    void namesTheReleaseOfTheBaseThatShipsTheFixAndListsWhatNoReleaseAnswers() {
        MavenProject project = this.module(ImageDocuments.document(ALPINE, RELEASE, OPENSSL, GLOB));
        Images docker = new Images(
            Map.of(
                BASE, ImageDocuments.document(ALPINE, RELEASE, OPENSSL, GLOB),
                REBUILT, ImageDocuments.document(ALPINE, "3.24.4", PATCHED, GLOB),
                NEWER, ImageDocuments.document(ALPINE, "3.24.4", PATCHED, GLOB)
            ),
            Map.of("node:26.8.2-alpine", REBUILT, "node:26.13.1-alpine", NEWER)
        );
        try (
            OsvStub osv = new OsvStub().lists(OPENSSL_QUERY, CVE, ADVISORY).lists(
                GLOB_QUERY, "GHSA-glob", GLOB_ADVISORY
            )
        ) {
            ImageReportInputs images = ImageReportInputs.read(
                project, new ImageLookups(
                    osv.scan(), docker.images(), _ -> Optional.of("node:26.13.1-alpine"), TODAY
                )
            );
            List<ReportAdvisory> advisories = images.system();
            assertAll(
                () -> assertEquals(List.of(CVE), advisories.stream().map(ReportAdvisory::identity).toList()),
                () -> assertTrue(advisories.getFirst().open()),
                () -> assertEquals(1, images.documents().size()),
                () -> assertListsEveryPackage(images),
                () -> assertEquals(
                    List.of("GHSA-glob"),
                    Objects.requireNonNull(images.language().get(this.directory.resolve("web"))).stream().map(
                        finding -> finding.advisory().identity()
                    ).toList()
                ),
                () -> assertEquals(
                    List.of(
                        project.getId() + " Dockerfile: replace FROM " + BASE + " with FROM " + REBUILT
                            + ", which ships the fix"
                            + " for " + CVE
                            + ". Upgrading packages inside the Dockerfile is refused; a newer release of the base"
                            + " is the repair."
                    ),
                    images.needed(advisories)
                ),
                () -> assertEquals(
                    List.of(BASE, REBUILT, NEWER), docker.listed(), "the base, then each release, is listed once"
                )
            );
        }
    }

    @Test
    void asksNothingOfDockerForAnImageWithoutFindings() {
        MavenProject project = this.module(ImageDocuments.document(ALPINE, RELEASE, OPENSSL, GLOB));
        Images docker = new Images(Map.of(), Map.of());
        try (OsvStub osv = new OsvStub()) {
            ImageReportInputs images = ImageReportInputs.read(
                project, new ImageLookups(osv.scan(), docker.images(), _ -> Optional.empty(), TODAY)
            );
            assertAll(
                () -> assertEquals(List.of(), images.system()),
                () -> assertEquals(List.of(), docker.listed()),
                () -> assertEquals(List.of(OPENSSL_QUERY, GLOB_QUERY), osv.asked())
            );
        }
    }

    @Test
    void accountsForAFixedBaseFindingWhenTheTagStillNamesTheBaseAndNoReleaseIsNewer() {
        MavenProject project = this.module(ImageDocuments.document(ALPINE, RELEASE, OPENSSL, GLOB));
        Images docker = new Images(
            Map.of(BASE, ImageDocuments.document(ALPINE, RELEASE, OPENSSL, GLOB)), Map.of("node:26.8.2-alpine", BASE)
        );
        try (OsvStub osv = new OsvStub().lists(OPENSSL_QUERY, CVE, ADVISORY)) {
            ImageReportInputs images = ImageReportInputs.read(
                project, new ImageLookups(osv.scan(), docker.images(), _ -> Optional.empty(), TODAY)
            );
            assertAll(
                () -> assertTrue(images.system().getFirst().mitigation().orElseThrow().harness()),
                () -> assertEquals(List.of(), images.needed(images.system())),
                () -> assertEquals(
                    List.of(BASE), docker.listed(), "a tag that still names the base is no release to list"
                )
            );
        }
    }

    @Test
    void readsTheBuildsOwnPackagesWhereTheBaseIsWrittenWithAVariableOrWithoutADigest() {
        try (OsvStub osv = new OsvStub().lists(OPENSSL_QUERY, CVE, ADVISORY)) {
            MavenProject variable = this.module(
                ImageDocuments.document(ALPINE, RELEASE, OPENSSL), "FROM node:${NODE}\n"
            );
            Images unread = new Images(Map.of(), Map.of());
            ImageReportInputs owned = ImageReportInputs.read(
                variable, new ImageLookups(osv.scan(), unread.images(), _ -> Optional.empty(), TODAY)
            );
            MavenProject tagged = this.module(
                ImageDocuments.document(ALPINE, RELEASE, OPENSSL), "FROM node:26.8.2-alpine\n"
            );
            Images rebuilt = new Images(
                Map.of(
                    "node:26.8.2-alpine", ImageDocuments.document(ALPINE, RELEASE, OPENSSL),
                    REBUILT, ImageDocuments.document(ALPINE, "3.24.4", PATCHED)
                ),
                Map.of("node:26.8.2-alpine", REBUILT)
            );
            ImageReportInputs moved = ImageReportInputs.read(
                tagged, new ImageLookups(osv.scan(), rebuilt.images(), _ -> Optional.empty(), TODAY)
            );
            assertAll(
                () -> assertEquals(List.of(), unread.listed(), "a base no default resolves is not pulled"),
                () -> assertTrue(
                    owned.needed(owned.system()).getFirst().contains("which the Dockerfile installs itself"),
                    "a package no base can be shown to hold is the build's own"
                ),
                () -> assertTrue(
                    moved.needed(moved.system()).getFirst().contains(
                        "replace FROM node:26.8.2-alpine with FROM " + REBUILT
                    ),
                    "a base pinned by its tag alone is pinned to the release that answers"
                )
            );
        }
    }

    @Test
    void readsNothingOfANodeProjectWhoseImageGateWroteNoResult() {
        MavenProject project = NpmFixture.module(this.directory, true);
        try (OsvStub osv = new OsvStub()) {
            ImageReportInputs images = ImageReportInputs.read(
                project, new ImageLookups(
                    osv.scan(), new Images(Map.of(), Map.of()).images(), _ -> Optional.empty(), TODAY
                )
            );
            assertAll(
                () -> assertEquals(List.of(), images.documents()),
                () -> assertEquals(List.of(), osv.asked())
            );
        }
    }

    @Test
    void namesTheRepairsOfEveryModuleAsOneVerdict() {
        ReportModule shop = new ReportModule(
            "shop", List.of(), List.of(), Map.of(), List.of("replace FROM a", "replace FROM a"), List.of()
        );
        ReportModule web = new ReportModule("web", List.of(), List.of(), Map.of());
        Findings findings = ImageReportInputs.repairs(List.of(shop, web));
        assertAll(
            () -> assertEquals(List.of("replace FROM a"), findings.offences()),
            () -> assertTrue(findings.headline().contains("upgrading packages inside a Dockerfile is refused"))
        );
    }

    @Test
    @SneakyThrows
    void draftsAReportWhoseNodeProjectHoldsADockerfileTheImageGateNeverListed() {
        git(this.directory);
        Files.writeString(this.directory.resolve(".gitignore"), "node_modules/\ntarget/\n");
        MavenProject project = NpmFixture.module(this.directory, true);
        Files.createDirectories(this.directory.resolve("web/node_modules/kit"));
        Files.writeString(this.directory.resolve("web/node_modules/kit/Dockerfile"), "FROM scratch\n");
        assertEquals(
            List.of(), ImageReportInputs.qualifications(List.of(project)),
            "an installed package's file is not the project's"
        );
        Files.writeString(this.directory.resolve("web/deploy.Dockerfile"), "FROM " + BASE + '\n');
        List<String> unlisted = ImageReportInputs.qualifications(List.of(project));
        Path results = this.directory.resolve("target/airness/node/projects/web");
        Files.writeString(results.resolve(ImageEvidence.RESULT), "{\"evidence\": []}");
        assertAll(
            () -> assertEquals(1, unlisted.size()),
            () -> assertTrue(
                unlisted.getFirst().startsWith("Container image not scanned:"), unlisted.getFirst()
            ),
            () -> assertEquals(List.of(), ImageReportInputs.qualifications(List.of(project)))
        );
    }

    @SneakyThrows
    private static void git(Path root) {
        Process process = new ProcessBuilder("git", "-C", root.toString(), "init", "--quiet").redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(GIT_TIMEOUT) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new IllegalStateException("The fixture repository could not be made at " + root);
        }
    }

    private MavenProject module(JsonNode sbom) {
        return this.module(sbom, "FROM " + BASE + " AS runtime\nCMD [\"node\"]\n");
    }

    private MavenProject module(JsonNode sbom, String dockerfile) {
        MavenProject project = NpmFixture.module(this.directory, true);
        this.image(sbom, dockerfile);
        return project;
    }

    private static void assertListsEveryPackage(ImageReportInputs images) {
        assertTrue(
            images.packages().stream().allMatch(
                entry -> entry.image().startsWith("web/Dockerfile, on Alpine " + RELEASE)
            ),
            images.packages().toString()
        );
        assertTrue(images.packages().stream().anyMatch(ReportImagePackage::system));
        assertTrue(images.packages().stream().anyMatch(entry -> !entry.system()));
    }

    @SneakyThrows
    private void image(JsonNode sbom, String dockerfile) {
        Files.writeString(this.directory.resolve("web/Dockerfile"), dockerfile);
        Path results = this.directory.resolve("target/airness/node/projects/web");
        Files.writeString(results.resolve(ImageEvidence.RESULT), "{\"evidence\": [\"image-Dockerfile.json\"]}");
        ReportJson.write(
            results.resolve("image-Dockerfile.json"), Map.of("contract", 1, "dockerfile", "Dockerfile", "sbom", sbom)
        );
    }

    /**
     * Docker as the images it holds and the digests each tag names, recording every image it listed.
     */
    private static final class Images implements HostDocker {

        private final Map<String, JsonNode> held;
        private final Map<String, String> tags;
        private final List<String> listed;

        Images(Map<String, JsonNode> held, Map<String, String> tags) {
            this.held = held;
            this.tags = tags;
            this.listed = new ArrayList<>();
        }

        DockerImages images() {
            return new DockerImages(SCANNER, this);
        }

        List<String> listed() {
            return List.copyOf(this.listed);
        }

        @Override
        public String run(List<String> command, Optional<Path> input) {
            if (command.contains(SAVE)) {
                this.listed.add(command.getLast());
            }
            return input.map(_ -> ReportJson.encode(Objects.requireNonNull(this.held.get(this.listed.getLast()))))
                .orElseGet(() -> this.printed(command));
        }

        // What a command other than the scanner prints: the digest an inspected tag names, and nothing otherwise.
        private String printed(List<String> command) {
            if (!command.contains(INSPECT)) {
                return "";
            }
            String tagged = command.getLast();
            String pinned = Objects.requireNonNull(this.tags.get(tagged));
            return "[\"" + tagged.substring(0, tagged.indexOf(':')) + pinned.substring(pinned.indexOf('@')) + "\"]";
        }

        @Override
        public boolean succeeds(List<String> command) {
            return true;
        }
    }
}
