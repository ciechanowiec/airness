package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * License and vulnerability evidence for all participating modules.
 *
 * @param packages   resolved artifact versions
 * @param advisories all scanner findings, including mitigated findings
 * @param policy     the license policy the build enforces
 * @param images     the packages the shipped container images hold, which the policy doesn't judge
 */
public record ReportInventory(
    List<ReportPackage> packages, List<ReportAdvisory> advisories,
    ReportLicensePolicy policy, List<ReportImagePackage> images
) {

    private static final Pattern PACKAGE_URL = Pattern.compile("^pkg:maven/([^/]+)/([^@]+)@([^?]+)");
    private static final String PRODUCTION = "Production";
    private static final List<String> SYSTEM_PACKAGES = List.of("pkg:apk/", "pkg:deb/", "pkg:rpm/");

    public ReportInventory {
        packages = List.copyOf(packages);
        advisories = List.copyOf(advisories.stream().sorted(ReportAdvisory.TRIAGE).toList());
        images = List.copyOf(images);
    }

    /**
     * The evidence of a build that ships no container image.
     *
     * @param packages   resolved artifact versions
     * @param advisories all scanner findings, including mitigated findings
     * @param policy     the license policy the build enforces
     */
    public ReportInventory(List<ReportPackage> packages, List<ReportAdvisory> advisories, ReportLicensePolicy policy) {
        this(packages, advisories, policy, List.of());
    }

    /**
     * Whether every license is within the policy and no advisory is open.
     *
     * @return the dependency policy verdict, independent of source cleanliness
     */
    public boolean withinPolicy() {
        return this.failures().isEmpty();
    }

    /**
     * Names the exact evidence that prevents a dependency-policy pass.
     *
     * @return license refusals and open advisories
     */
    public List<String> failures() {
        return Stream.concat(
            this.refused().stream()
                .map(entry -> "License outside inherited policy: " + entry.coordinate() + " " + entry.licenses()),
            this.open().stream()
                .map(advisory -> "Open vulnerability: " + advisory.identity() + " in " + advisory.artifact())
        ).toList();
    }

    /**
     * The advisories no recorded mitigation covers.
     *
     * @return open advisories, most severe first
     */
    public List<ReportAdvisory> open() {
        return this.advisories.stream().filter(ReportAdvisory::open).toList();
    }

    /**
     * The container image findings no release of the image can fix yet, which the build accepts until one can.
     *
     * @return advisories awaiting a fix, most severe first
     */
    public List<ReportAdvisory> awaitingFix() {
        return this.advisories.stream().filter(ReportAdvisory::awaitingFix).toList();
    }

    /**
     * The advisories a recorded mitigation covers.
     *
     * @return mitigated advisories, most severe first
     */
    public List<ReportAdvisory> mitigated() {
        return this.advisories.stream().filter(ReportAdvisory::mitigated).toList();
    }

    /**
     * The artifact versions whose every admitted license carries a weak copyleft condition.
     *
     * @return weak copyleft packages
     */
    public List<ReportPackage> weakCopyleft() {
        return this.packages.stream()
            .filter(entry -> this.policy.classification(entry.licenses()) == LicenseClass.WEAK_COPYLEFT)
            .toList();
    }

    /**
     * The artifact versions no declared license of which the policy admits.
     *
     * @return refused packages
     */
    public List<ReportPackage> refused() {
        return this.packages.stream().filter(this.policy::refuses).toList();
    }

    /**
     * The use a reviewer reads an advisory under, worded as the inventory words the affected package's use, so one
     * package never reads as two. A package several uses reach lists each, the production use first. A system
     * package belongs to a container image the build ships, and any other artifact, such as a file name, has no use
     * the evidence determines.
     *
     * @param advisory a reported advisory
     * @return the inventory's use labels, {@code Container image} or {@code Not determined}
     */
    public String scope(ReportAdvisory advisory) {
        String artifact = advisory.artifact();
        List<String> uses = this.packages.stream().filter(entry -> names(entry, artifact))
            .flatMap(entry -> entry.uses().stream()).map(ReportUse::label).distinct()
            .sorted(Comparator.comparing((String label) -> !PRODUCTION.equals(label)).thenComparing(label -> label))
            .toList();
        if (!uses.isEmpty()) {
            return String.join(", ", uses);
        }
        return SYSTEM_PACKAGES.stream().anyMatch(artifact::startsWith) || this.inImage(artifact) ? "Container image"
            : "Not determined";
    }

    // A Maven package URL names a Maven coordinate in another order, and any other package URL names its package
    // exactly, apart from qualifiers such as the architecture.
    private static boolean names(ReportPackage entry, String artifact) {
        Matcher address = PACKAGE_URL.matcher(artifact);
        if (address.find()) {
            String prefix = address.group(1) + ':' + address.group(2) + ':';
            String suffix = ':' + address.group(3);
            return entry.coordinate().startsWith(prefix) && entry.coordinate().endsWith(suffix);
        }
        return entry.coordinate().equals(withoutQualifiers(artifact));
    }

    private boolean inImage(String artifact) {
        String purl = withoutQualifiers(artifact);
        return this.images.stream().anyMatch(entry -> withoutQualifiers(entry.purl()).equals(purl));
    }

    private static String withoutQualifiers(String purl) {
        int qualifiers = purl.indexOf('?');
        return qualifiers < 0 ? purl : purl.substring(0, qualifiers);
    }
}
