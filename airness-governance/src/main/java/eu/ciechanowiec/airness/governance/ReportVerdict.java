package eu.ciechanowiec.airness.governance;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The report's header: the draft notice, the verdict statements and the contents. The verdict precedes
 * the contents, so a reader who opens only the first page reads it. It states what was checked and
 * never calls the project compliant.
 *
 * @param report evaluated report
 */
record ReportVerdict(ComplianceReport report) {

    /**
     * The label of the date the vulnerability data of the Maven packages was current at.
     */
    static final String MAVEN_DATA = "Vulnerability data for Java (Maven) packages as of";

    String header() {
        return "<div id=\"header\"><h1>Open Source Software Report</h1>" + this.preamble() + ReportOutline.contents()
            + ReportGuide.render() + "</div>";
    }

    // The figures a reader takes in at a glance: what was resolved, how its licenses are classed, and where its
    // vulnerabilities stand.
    private String figures() {
        ReportInventory inventory = this.report.inventory();
        Map<String, Long> groups = inventory.packages().stream()
            .collect(Collectors.groupingBy(ReportTables::group, Collectors.counting()));
        Map<LicenseClass, Long> classes = inventory.packages().stream()
            .collect(
                Collectors.groupingBy(
                    entry -> inventory.policy().classification(entry.licenses()),
                    Collectors.counting()
                )
            );
        long images = inventory.images().stream().filter(ReportImagePackage::system).count();
        return "<div class=\"figures\">" + figure(
            inventory.packages().size(), "package versions checked", Stream.of(
                Map.entry("Java (Maven) packages", groups.getOrDefault(ReportTables.MAVEN, 0L)),
                Map.entry("JavaScript (npm) packages", groups.getOrDefault(ReportTables.NPM, 0L)),
                Map.entry("build-tool packages", groups.getOrDefault(ReportTables.TOOLS, 0L)),
                Map.entry("container image packages, listed only", images)
            ).filter(entry -> entry.getValue() > 0).toList()
        ) + figure(
            classes.getOrDefault(LicenseClass.OUTSIDE_POLICY, 0L), "package versions with a license outside the policy",
            List.of(
                Map.entry("permissive", classes.getOrDefault(LicenseClass.PERMISSIVE, 0L)),
                Map.entry("weak copyleft", classes.getOrDefault(LicenseClass.WEAK_COPYLEFT, 0L))
            )
        ) + figure(
            distinct(inventory.open()), "open vulnerabilities", Stream.of(
                Map.entry("awaiting a fix", distinct(inventory.awaitingFix())),
                Map.entry("mitigated", distinct(inventory.mitigated()))
            ).filter(entry -> !inventory.images().isEmpty() || !"awaiting a fix".equals(entry.getKey())).toList()
        ) + "</div>";
    }

    private static String figure(long count, String label, List<Map.Entry<String, Long>> parts) {
        return "<div class=\"figure\"><p class=\"figure-count\">" + count + "</p><p class=\"figure-label\">" + label
            + "</p><ul class=\"figure-parts\">" + parts.stream()
                .map(part -> "<li><span>" + part.getValue() + "</span> " + ReportMarkup.escape(part.getKey()) + "</li>")
                .collect(Collectors.joining()) + "</ul></div>";
    }

    private String preamble() {
        String scanned = this.report.evidence().scannedAt();
        ReportInventory inventory = this.report.inventory();
        Stream<Map.Entry<String, String>> facts = Stream.of(
            Map.entry("Project", ReportMarkup.escape(this.report.project())),
            Map.entry("Source commit", ReportMarkup.code(this.report.source().commit())),
            Map.entry("Report generated", ReportMarkup.moment(this.report.source().generatedAt())),
            Map.entry(MAVEN_DATA, scanned.isEmpty() ? "No scan recorded" : ReportMarkup.moment(scanned))
        );
        Stream<Map.Entry<String, String>> queried = osvData(inventory)
            .map(label -> Map.entry(label, ReportMarkup.moment(this.report.source().generatedAt()))).stream();
        return "<div id=\"preamble\">" + this.draftNotice() + this.verdict() + this.figures()
            + ReportMarkup.facts(Stream.concat(facts, queried).toList()) + "</div>";
    }

    /**
     * The label of the moment the OSV database was asked, naming the packages it was asked about, when any were.
     *
     * @param inventory the evaluated inventory
     * @return the label, or nothing when no npm or container image package was looked up
     */
    static Optional<String> osvData(ReportInventory inventory) {
        boolean npm = inventory.packages().stream().anyMatch(
            entry -> ReportTables.NPM.equals(ReportTables.group(entry))
        );
        boolean images = !inventory.images().isEmpty();
        List<String> packages = Stream.of(Map.entry(npm, "JavaScript (npm)"), Map.entry(images, "container image"))
            .filter(Map.Entry::getKey).map(Map.Entry::getValue).toList();
        return packages.isEmpty() ? Optional.empty()
            : Optional.of("Vulnerability data for " + String.join(" and ", packages) + " packages as of");
    }

    private String draftNotice() {
        List<String> reasons = this.report.draftReasons();
        return reasons.isEmpty() ? "" : ReportMarkup.warning(
            "<p class=\"title\">Draft: not release evidence</p><ul class=\"draft-reasons\">"
                + reasons.stream().map(reason -> "<li>" + ReportMarkup.escape(reason) + "</li>")
                    .collect(Collectors.joining()) + "</ul>"
        );
    }

    private String verdict() {
        return "<ul class=\"statements verdict\">" + Stream.concat(
            Stream.of(this.licenseStatement()), this.vulnerabilityStatements()
        ).collect(Collectors.joining()) + "</ul>";
    }

    private String licenseStatement() {
        ReportInventory inventory = this.report.inventory();
        long outside = inventory.refused().size();
        return outside == 0
            ? statement(true, all(inventory.packages().size()) + " a license the policy allows.")
            : statement(
                false, ReportCounts.plural(outside, "package version") + " "
                    + ReportCounts.agreeing(outside, "has", "have")
                    + " a license the policy doesn&#39;t allow. Each is listed in "
                    + ReportOutline.reference("review-outside") + "."
            );
    }

    // No statement reports recorded mitigations without a vulnerability: the scan fails on such an entry
    // before any report is written, so a written report never has one.
    private Stream<String> vulnerabilityStatements() {
        ReportInventory inventory = this.report.inventory();
        List<ReportAdvisory> open = inventory.open();
        List<ReportAdvisory> awaiting = inventory.awaitingFix();
        List<ReportAdvisory> mitigated = inventory.mitigated();
        String openStatement = open.isEmpty()
            ? statement(true, noneOpen(inventory))
            : statement(
                false, vulnerabilities(open) + " " + ReportCounts.agreeing(distinct(open), "is", "are")
                    + " open: nothing recorded explains why " + ReportCounts.agreeing(distinct(open), "it", "they")
                    + " can&#39;t be exploited here, so each must be fixed or explained. Each is listed in "
                    + ReportOutline.reference("advisories-all") + "."
            );
        return Stream.of(Stream.of(openStatement), waiting(awaiting), planned(mitigated))
            .flatMap(statements -> statements);
    }

    private static String noneOpen(ReportInventory inventory) {
        return inventory.advisories().isEmpty() ? "No known vulnerability was found."
            : "No vulnerability is open: every one found is "
                + (inventory.awaitingFix().isEmpty() ? "mitigated" : "mitigated or awaiting a fix") + ", as "
                + ReportOutline.reference("vulnerabilities") + " shows.";
    }

    private static Stream<String> waiting(List<ReportAdvisory> awaiting) {
        return awaiting.isEmpty() ? Stream.empty() : Stream.of(
            "<li class=\"warn\">" + vulnerabilities(awaiting) + " "
                + ReportCounts.agreeing(distinct(awaiting), "waits", "wait") + " for a fix that no release of the "
                + "container image&#39;s base includes yet. " + ReportCounts.agreeing(
                    distinct(awaiting), "It isn&#39;t", "They "
                        + "aren&#39;t"
                ) + " mitigated, and " + ReportCounts.agreeing(distinct(awaiting), "is", "are")
                + " accepted only until such a release exists. Each is listed in "
                + ReportOutline.reference("advisories-awaiting") + ".</li>"
        );
    }

    private static Stream<String> planned(List<ReportAdvisory> mitigated) {
        return mitigated.isEmpty() ? Stream.empty() : Stream.of(
            statement(
                true, vulnerabilities(mitigated) + " " + ReportCounts.agreeing(distinct(mitigated), "is", "are")
                    + " mitigated: a recorded reason explains why "
                    + ReportCounts.agreeing(distinct(mitigated), "it", "each") + " can&#39;t be exploited in this "
                    + "product, with "
                    + "a plan for removing the exception, in " + ReportOutline.reference("advisories-plans") + "."
            )
        );
    }

    // "2 vulnerabilities (1 high, 1 low), in 5 package versions", most severe first, which is the order the
    // advisories are listed in. One vulnerability often reaches several packages, so it is counted once, at the
    // severity it is listed under, and the packages it reaches are counted beside it.
    private static String vulnerabilities(List<ReportAdvisory> advisories) {
        Map<String, ReportSeverity> levels = new LinkedHashMap<>();
        advisories.forEach(advisory -> levels.putIfAbsent(advisory.identity(), advisory.level()));
        Map<ReportSeverity, Long> counts = levels.values().stream().collect(
            Collectors.groupingBy(
                severity -> severity, () -> new EnumMap<>(ReportSeverity.class), Collectors.counting()
            )
        );
        String breakdown = counts.entrySet().stream()
            .map(entry -> entry.getValue() + " " + entry.getKey().label().toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(", "));
        long packages = advisories.stream().map(ReportAdvisory::artifact).distinct().count();
        String reach = packages == levels.size() ? ""
            : ", in " + ReportCounts.plural(packages, "package version") + ",";
        return ReportCounts.plural(levels.size(), "vulnerability", "vulnerabilities") + " (" + breakdown + ")" + reach;
    }

    private static long distinct(List<ReportAdvisory> advisories) {
        return advisories.stream().map(ReportAdvisory::identity).distinct().count();
    }

    private static String all(int count) {
        return count == 1 ? "The 1 package version has" : "All " + count + " package versions have";
    }

    private static String statement(boolean passing, String text) {
        return "<li class=\"" + (passing ? "pass" : "fail") + "\">" + text + "</li>";
    }
}
