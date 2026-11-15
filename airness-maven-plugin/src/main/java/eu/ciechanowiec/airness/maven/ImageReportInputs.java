package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import eu.ciechanowiec.airness.governance.BaseReleases;
import eu.ciechanowiec.airness.governance.DockerfileBase;
import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.Repository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.project.MavenProject;

/**
 * The shipped-image half of one module's compliance evidence: what every image the {@code image} gate built holds,
 * and how its findings are judged.
 *
 * <p>Every package an image holds is looked up in OSV and added to the aggregate SBOM. Where a finding is one a newer
 * release of the image's base could answer, the base and its releases are listed too: the release the base's tag
 * names today, when it is a rebuild of the one the Dockerfile pins, and the newest release of the same variant and
 * major version on Docker Hub. An image without findings asks for neither, so a project whose images are current pays
 * for one OSV query per image.
 *
 * @param documents the images' documents for the aggregate SBOM
 * @param system    the system findings as reported, harness accounts included
 * @param language  the language findings of each node project's images, by the node project's directory, which the
 *                  node project's allowlist mitigates beside its npm findings
 * @param repairs   the repairs, each printed while a finding it answers is open
 */
record ImageReportInputs(
    List<JsonNode> documents, List<ReportAdvisory> system, Map<Path, List<OsvAdvisory>> language,
    List<ImageRepair> repairs
) {

    private static final Pattern DOCKERFILE = Pattern.compile("^(?:Dockerfile(?:\\..+)?|.+\\.[Dd]ockerfile)$");

    ImageReportInputs {
        documents = List.copyOf(documents);
        system = List.copyOf(system);
        language = Map.copyOf(language);
        repairs = List.copyOf(repairs);
    }

    /**
     * Reads and judges the images of every node project a module declares.
     *
     * @param project the module
     * @param lookups where the verdicts are read from
     * @return the evidence, empty for a module whose node projects built no image
     * @throws IllegalStateException when an image's list, its base or a release cannot be read
     */
    static ImageReportInputs read(MavenProject project, ImageLookups lookups) {
        List<Judged> judged = NpmEvidence.of(project).stream().flatMap(
            node -> ImageEvidence.of(node).orElse(List.of()).stream()
                .map(evidence -> Judged.of(project.getId(), node, evidence, lookups))
        ).toList();
        return new ImageReportInputs(
            judged.stream().map(Judged::document).toList(),
            judged.stream().flatMap(entry -> entry.verdict().system().stream()).toList(),
            judged.stream().collect(
                Collectors.groupingBy(
                    Judged::directory, Collectors.flatMapping(
                        entry -> entry.verdict().language().stream(), Collectors.toList()
                    )
                )
            ),
            judged.stream().flatMap(entry -> entry.verdict().repairs().stream()).toList()
        );
    }

    /**
     * Reads with the public OSV, the host's Docker client and Docker Hub.
     *
     * @param project the module
     * @param osv     the vulnerability database
     * @return the evidence
     */
    static ImageReportInputs read(MavenProject project, OsvScan osv) {
        BaseReleases hub = new BaseReleases();
        return read(project, new ImageLookups(osv, DockerImages.system(), hub::newer, LocalDate.now(ZoneOffset.UTC)));
    }

    /**
     * The repairs still needed once every finding of the module was mitigated as far as it can be.
     *
     * @param advisories every advisory of the module
     * @return the repairs that answer an open finding
     */
    List<String> needed(Collection<ReportAdvisory> advisories) {
        return this.repairs.stream().filter(repair -> repair.needed(advisories)).map(ImageRepair::text).distinct()
            .toList();
    }

    /**
     * The repairs every module's shipped images still need, as one verdict beside the report's own.
     *
     * @param modules the modules the report covers
     * @return the repairs, which fail the build while any is listed
     */
    static Findings repairs(Collection<ReportModule> modules) {
        return new Findings(
            "Shipped images whose open vulnerabilities have a repair (make each change it names; upgrading packages"
                + " inside a Dockerfile is refused)",
            modules.stream().flatMap(module -> module.repairs().stream()).distinct().toList()
        );
    }

    /**
     * Why a report over these modules cannot be a final one on account of their images: a node project holds a
     * Dockerfile whose image the {@code image} gate never listed, as when the report is regenerated on its own.
     *
     * @param projects the modules the report covers
     * @return the qualifications
     */
    static List<String> qualifications(Collection<MavenProject> projects) {
        return projects.stream().flatMap(project -> NpmEvidence.of(project).stream())
            .filter(node -> ImageEvidence.of(node).isEmpty() && holdsDockerfile(node.directory()))
            .map(ImageReportInputs::unlisted).toList();
    }

    private static String unlisted(NpmEvidence node) {
        return node.subject() + " holds a Dockerfile whose image the image gate did not list, so what the image ships"
            + " was not checked.";
    }

    // Whether a Dockerfile a commit would carry lies under the node project. Where Git cannot say, nothing is read,
    // and the report is not drafted on this account.
    private static boolean holdsDockerfile(Path directory) {
        return Repository.committableFiles(directory).orElse(List.of()).stream()
            .anyMatch(file -> DOCKERFILE.matcher(String.valueOf(file.getFileName())).matches());
    }

    /**
     * One image judged: the node project that built it, its document and its verdict.
     *
     * @param directory the node project's directory
     * @param document  the image's document for the aggregate SBOM
     * @param verdict   the verdict
     */
    private record Judged(Path directory, JsonNode document, ImageVerdict verdict) {

        static Judged of(String module, NpmEvidence node, ImageEvidence evidence, ImageLookups lookups) {
            ImageInventory inventory = ImageInventory.of(evidence.sbom());
            return new Judged(
                node.directory(), ImageSbom.document(module, evidence, inventory),
                verdict(module, evidence, inventory, lookups)
            );
        }

        // The base and its releases are listed only for a finding a newer base could answer.
        private static ImageVerdict verdict(
            String module, ImageEvidence evidence, ImageInventory inventory, ImageLookups lookups
        ) {
            List<OsvAdvisory> findings = lookups.findings(inventory, evidence, module);
            if (findings.isEmpty()) {
                return ImageVerdict.none();
            }
            Optional<String> base = DockerfileBase.of(text(evidence.file()))
                .filter(reference -> reference.indexOf('$') < 0);
            Optional<ImageInventory> inBase = base.map(lookups.images()::packages).map(ImageInventory::of);
            ImageJudgement judgement = new ImageJudgement(module, evidence, inventory, base, inBase, lookups.today());
            List<ImageCandidate> candidates = judgement.answerable(findings).isEmpty() ? List.of()
                : base.stream().flatMap(lookups::candidates)
                    .map(reference -> lookups.candidate(reference, evidence, module)).toList();
            return judgement.judge(findings, candidates);
        }

        private static String text(Path dockerfile) {
            try {
                return Files.readString(dockerfile);
            } catch (IOException exception) {
                throw new UncheckedIOException(
                    "The Dockerfile an image was built from cannot be read: " + dockerfile, exception
                );
            }
        }
    }
}
