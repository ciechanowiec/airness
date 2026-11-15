package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Judges the findings of one shipped image by where each package came from and by what a newer base would change.
 *
 * <p>A system package the base holds is answered by a newer release of the base, and by nothing else: the image's
 * own build may not upgrade it. So its finding fails only once a newer release of the base ships the fix, and the
 * failure names that release. Until then the finding is reported with the harness's own account of why it does not
 * fail, which is either that the distribution has published no fixed version or that no release of the base carries
 * the one it published. A system package the image's build installed itself fails once its distribution publishes a
 * fixed version, which the build then installs by raising the version it pins. A language package the base holds is
 * open like any other, and its repair is a newer base that drops it or, failing that, removing it or recording the
 * project's own mitigation.
 *
 * @param module   the module whose node project holds the Dockerfile
 * @param evidence the image the Dockerfile built
 * @param image    what the image holds
 * @param base     the base the last stage ships on, empty when it starts from {@code scratch} or cannot be read
 * @param inBase   what the base holds, empty without a base
 * @param today    the date the harness records its accounts on
 */
record ImageJudgement(
    String module, ImageEvidence evidence, ImageInventory image, Optional<String> base,
    Optional<ImageInventory> inBase, LocalDate today
) {

    /**
     * The findings a newer release of the base could answer: those of packages the base itself holds, a system one
     * only once its distribution has published a fixed version. Releases are looked up only for these.
     *
     * @param findings every finding of the image
     * @return the findings a newer base could answer
     */
    List<OsvAdvisory> answerable(List<OsvAdvisory> findings) {
        return findings.stream().filter(this::fromBase)
            .filter(finding -> finding.fixed() || !this.held(finding).system()).toList();
    }

    /**
     * Judges every finding of the image against the releases of its base that were listed.
     *
     * @param findings   every finding of the image
     * @param candidates the newer releases of the base, in the order they are preferred on a tie
     * @return the system findings as reported, the language findings for the node project's allowlist, and the
     *         repairs
     */
    Verdict judge(List<OsvAdvisory> findings, List<ImageCandidate> candidates) {
        List<OsvAdvisory> answerable = this.answerable(findings);
        Comparator<ImageCandidate> answering = Comparator.comparingLong(
            candidate -> this.answered(candidate, answerable)
        );
        Optional<ImageCandidate> best = candidates.stream()
            .filter(candidate -> this.answered(candidate, answerable) > 0).max(answering);
        Set<OsvAdvisory> answered = best.map(
            candidate -> answerable.stream().filter(finding -> candidate.answers(this.held(finding), finding))
                .collect(Collectors.toUnmodifiableSet())
        ).orElse(Set.of());
        List<ReportAdvisory> system = findings.stream().filter(finding -> this.held(finding).system())
            .map(finding -> this.system(finding, answered.contains(finding))).toList();
        List<OsvAdvisory> language = findings.stream().filter(finding -> !this.held(finding).system()).toList();
        List<ImageRepair> repairs = new ArrayList<>();
        best.filter(_ -> !answered.isEmpty()).ifPresent(candidate -> repairs.add(this.newerBase(candidate, answered)));
        repairs.addAll(this.installed(findings));
        repairs.addAll(this.unanswered(language, answered));
        return new Verdict(system, language, repairs);
    }

    private long answered(ImageCandidate candidate, List<OsvAdvisory> answerable) {
        return answerable.stream().filter(finding -> candidate.answers(this.held(finding), finding)).count();
    }

    private ReportAdvisory system(OsvAdvisory finding, boolean answered) {
        if (!finding.fixed()) {
            return this.unfixed(finding);
        }
        return !this.fromBase(finding) || answered ? finding.advisory() : this.awaiting(finding);
    }

    // A fixed finding of the base that no listed release answers waits for a release that does.
    private ReportAdvisory awaiting(OsvAdvisory finding) {
        String pinned = this.base.orElseThrow();
        return this.accounted(
            finding, this.image.distributionName() + " has published a fixed version of " + this.held(finding).source()
                + ", but no newer release of " + pinned + " ships it yet, and the image's own build may not upgrade"
                + " what its base holds.",
            "The check fails once a newer release of " + pinned + " ships the fixed version, and names that release"
                + " as the repair."
        );
    }

    // A finding the distribution has not fixed waits for the distribution, and then for the base or the build's pin.
    private ReportAdvisory unfixed(OsvAdvisory finding) {
        String distribution = this.image.distributionName();
        String plan = this.fromBase(finding)
            ? "The check fails once " + distribution + " publishes a fixed version and a newer release of "
                + this.base.orElseThrow() + " ships it, and names that release as the repair."
            : "The check fails once " + distribution + " publishes a fixed version, which "
                + this.evidence.dockerfile() + " then installs by raising the version it pins.";
        return this.accounted(
            finding, distribution + " has published no fixed version of " + this.held(finding).source()
                + ", so no image built on it can ship one.", plan
        );
    }

    private ReportAdvisory accounted(OsvAdvisory finding, String reason, String plan) {
        ReportAdvisory advisory = finding.advisory();
        return new ReportAdvisory(
            advisory.identity(), advisory.severity(), advisory.artifact(), advisory.modules(), advisory.description(),
            Optional.of(new ReportMitigation(reason, plan, this.today.toString(), true))
        );
    }

    private ImageRepair newerBase(ImageCandidate candidate, Set<OsvAdvisory> answered) {
        return new ImageRepair(
            this.subject() + ": replace FROM " + this.base.orElseThrow() + " with FROM " + candidate.reference()
                + ", which ships the fix for " + identities(answered) + ". Upgrading packages inside the Dockerfile is"
                + " refused; a newer release of the base is the repair.",
            keys(answered.stream())
        );
    }

    // One repair per package the image's own build installed whose distribution has published a fixed version.
    private List<ImageRepair> installed(List<OsvAdvisory> findings) {
        Map<ImagePackage, List<OsvAdvisory>> packages = findings.stream()
            .filter(finding -> this.held(finding).system() && finding.fixed() && !this.fromBase(finding))
            .collect(Collectors.groupingBy(this::held, LinkedHashMap::new, Collectors.toList()));
        return packages.entrySet().stream().map(
            entry -> new ImageRepair(
                this.subject() + ": " + entry.getKey().name() + ' ' + entry.getKey().version()
                    + ", which the Dockerfile installs itself, has a fixed version in " + this.image.distributionName()
                    + " for " + identities(entry.getValue()) + "; raise the version its install line pins.",
                keys(entry.getValue().stream())
            )
        ).toList();
    }

    // One repair per language package of the base that no listed release of the base drops or fixes.
    private List<ImageRepair> unanswered(List<OsvAdvisory> language, Set<OsvAdvisory> answered) {
        Map<ImagePackage, List<OsvAdvisory>> packages = language.stream()
            .filter(finding -> this.fromBase(finding) && !answered.contains(finding))
            .collect(Collectors.groupingBy(this::held, LinkedHashMap::new, Collectors.toList()));
        return packages.entrySet().stream().map(
            entry -> new ImageRepair(
                this.subject() + ": " + entry.getKey().purl() + " comes with the base image " + this.base.orElseThrow()
                    + ", and no newer release of it ships the fix for " + identities(entry.getValue())
                    + "; remove it from the final stage if the image does not use it, or, for an npm package, record"
                    + " an airness.vulnerabilityAllowlist entry.",
                keys(entry.getValue().stream())
            )
        ).toList();
    }

    private boolean fromBase(OsvAdvisory finding) {
        return this.inBase.flatMap(held -> held.held(finding.advisory().artifact())).isPresent();
    }

    private ImagePackage held(OsvAdvisory finding) {
        return this.image.held(finding.advisory().artifact()).orElseThrow(
            () -> new IllegalStateException(
                "A finding names " + finding.advisory().artifact() + ", which "
                    + this.evidence.dockerfile() + "'s image does not hold"
            )
        );
    }

    private String subject() {
        return this.module + ' ' + this.evidence.dockerfile();
    }

    private static String identities(Collection<OsvAdvisory> findings) {
        return findings.stream().map(finding -> finding.advisory().identity()).distinct().sorted()
            .collect(Collectors.joining(", "));
    }

    private static Set<String> keys(Stream<OsvAdvisory> findings) {
        return findings.map(finding -> finding.advisory().key()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * What judging one image came back with.
     *
     * @param system   the system findings as reported, harness accounts included
     * @param language the language findings, still open to the node project's allowlist
     * @param repairs  the repairs, each printed while a finding it answers is open
     */
    record Verdict(List<ReportAdvisory> system, List<OsvAdvisory> language, List<ImageRepair> repairs) {

        Verdict {
            system = List.copyOf(system);
            language = List.copyOf(language);
            repairs = List.copyOf(repairs);
        }

        /**
         * The verdict of an image that holds no finding.
         *
         * @return a verdict with nothing in it
         */
        static Verdict none() {
            return new Verdict(List.of(), List.of(), List.of());
        }
    }
}
