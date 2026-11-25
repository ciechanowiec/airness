package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The report's notice texts and its provenance.
 *
 * @param report evaluated report
 */
record ReportSections(ComplianceReport report) {

    private static final String DIGEST = "SHA-256";
    private static final int LONG_TEXT = 1200;
    private static final String SNAPSHOT = "-SNAPSHOT";
    private static final String UNRELEASED = " (an unreleased development version, so the report can&#39;t be "
        + "reproduced exactly)";
    private static final String TYPEFACES = ReportMarkup.paragraphs(
        List.of(
            "The typefaces embedded in this document, each under the SIL Open Font License 1.1, whose text follows its "
                + "name."
        )
    );
    private static final List<String> NOTICES_INTRODUCTION = List.of(
        "This section reproduces the license and notice files of every package that ships with the product, the "
            + "packages marked <em>Production</em> in section 4. Many open source licenses require these texts to "
            + "accompany the product.",
        "The words are unchanged. Only the line wrapping and the text-formatting symbols of the original files are "
            + "removed, so the text reads as paragraphs. Identical texts are printed once, with every package version "
            + "that carries them.",
        "Packages used only to develop, test or build the product, or provided by the environment it runs in, "
            + "aren&#39;t part of the delivered product, so their files aren&#39;t reproduced."
    );
    private static final Set<String> TOOLS = Set.of(
        "Vulnerability scanner", "OSV vulnerability database", "Image package lister", "Published SBOM generator",
        "PDF renderer", "Build checks version"
    );
    private static final Map<String, String> TOOL_LABELS = Map.of(
        "Vulnerability scanner", "Vulnerability scanner for Java (Maven) packages", "OSV vulnerability database",
        "Vulnerability database for JavaScript and container image packages", "Image package lister",
        "Tool that listed the container image packages", "Published SBOM generator",
        "Generator of the per-module SBOMs published with the Java modules", "PDF renderer",
        "Renderer of this PDF"
    );
    private static final Map<String, String> FACT_LABELS = Map.of(
        "Aggregate SBOM SHA-256", "Fingerprint of the SBOM embedded in the HTML edition (SHA-256)", "Modules",
        "Modules covered"
    );
    private static final Map<String, String> EVIDENCE_LABELS = Map.of(
        " SBOM SHA-256", ": fingerprint of its SBOM (SHA-256)", " scanner SHA-256",
        ": fingerprint of its vulnerability scan results (SHA-256)", " scan date", ": vulnerability scan date"
    );

    String notices() {
        List<ReportPackage> production = this.report.inventory().packages().stream()
            .filter(ReportPackage::production).toList();
        List<String> missing = production.stream().filter(entry -> entry.archiveNotices().isEmpty())
            .map(entry -> display(entry.coordinate())).sorted().toList();
        List<Map.Entry<String, List<Map.Entry<String, String>>>> groups = groups(production);
        String introduction = ReportMarkup.paragraphs(NOTICES_INTRODUCTION);
        if (production.isEmpty()) {
            return ReportOutline.section(
                "notices", introduction + ReportOutline.subheading("notices-missing")
                    + ReportMarkup.empty("No package ships with the product, so no license file is reproduced.")
                    + ReportOutline.subheading("notices-texts")
                    + ReportMarkup.empty("No package ships with the product, so no license file is reproduced.")
                    + ReportOutline.subheading("notices-typefaces") + TYPEFACES + ReportAssets.credits()
            );
        }
        return ReportOutline.section(
            "notices", introduction + ReportOutline.subheading("notices-missing") + (missing.isEmpty()
                ? ReportMarkup.empty(
                    "Each of the " + ReportCounts.plural(production.size(), "package version")
                        + " the product ships contains a license file."
                )
                : ReportMarkup.paragraphs(
                    List.of(
                        ReportCounts.plural(missing.size(), "package version") + " of the " + production.size()
                            + " the product ships " + ReportCounts.agreeing(missing.size(), "contains", "contain")
                            + " no license file, so no text can be reproduced for them here.",
                        "Their license is named in " + ReportOutline.reference("inventory") + ". Where that license "
                            + "requires its text or a copyright notice to accompany the product, the text has to be "
                            + "obtained from the package&#39;s source."
                    )
                ) + ReportMarkup.idList(missing))
                + ReportOutline.subheading("notices-texts") + (groups.isEmpty()
                    ? ReportMarkup.empty("No package the product ships contains a license file.")
                    : ReportMarkup.paragraphs(
                        List.of(
                            ReportCounts.plural(groups.size(), "distinct text") + " "
                                + ReportCounts.agreeing(groups.size(), "covers", "cover") + " the "
                                + (production.size() - missing.size()) + " package versions that contain one, "
                                + "ordered by package name."
                        )
                    ) + IntStream.range(0, groups.size()).mapToObj(index -> notice(groups.get(index), index))
                        .collect(Collectors.joining()))
                + ReportOutline.subheading("notices-typefaces") + TYPEFACES + ReportAssets.credits()
        );
    }

    // Identical texts are printed once. Most permissive licenses differ only in their copyright line,
    // so the grouping is by the reflowed text rather than by the license name.
    private static List<Map.Entry<String, List<Map.Entry<String, String>>>> groups(List<ReportPackage> production) {
        Map<String, List<Map.Entry<String, String>>> grouped = production.stream().flatMap(
            entry -> entry.archiveNotices().entrySet().stream()
                .map(
                    notice -> Map.entry(
                        ReportNoticeText.reflow(notice.getValue()),
                        Map.entry(display(entry.coordinate()), notice.getKey())
                    )
                )
        ).collect(
            Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList()))
        );
        return grouped.entrySet().stream()
            .sorted(Comparator.comparing(group -> String.join(", ", coordinates(group)))).toList();
    }

    private static String display(String coordinate) {
        ReportCoordinate parsed = ReportCoordinate.of(coordinate);
        return parsed.version().isEmpty() ? parsed.name() : parsed.name() + ' ' + parsed.version();
    }

    String provenance() {
        ReportSource source = this.report.source();
        Stream<Map.Entry<String, String>> own = Stream.of(
            Map.entry("Source commit", ReportMarkup.code(source.commit())),
            Map.entry("Uncommitted changes", source.dirty() ? "Present, so this report is a draft" : "None"),
            Map.entry("Fingerprint of the source files (SHA-256)", ReportMarkup.digest(source.fingerprint())),
            Map.entry("Report date", ReportMarkup.moment(source.generatedAt())),
            Map.entry("Command to regenerate", ReportMarkup.code("mvn -Pextended airness:compliance-report"))
        );
        Stream<Map.Entry<String, String>> evidence = this.report.evidence().facts().entrySet().stream()
            .filter(entry -> !TOOLS.contains(entry.getKey())).sorted(Map.Entry.comparingByKey()).map(
                entry -> Map.entry(
                    ReportMarkup.escape(label(entry.getKey())), entry.getKey().contains(DIGEST)
                        ? ReportMarkup.digest(entry.getValue()) : ReportMarkup.moment(entry.getValue())
                )
            );
        return ReportOutline.section(
            "provenance", ReportMarkup.paragraphs(
                Stream.concat(
                    Stream.of(
                        "This report is generated from the project&#39;s build, not written by hand.",
                        "It reads the same package list, license policy and vulnerability findings that the build&#39;s "
                            + "license and vulnerability checks act on, so the report and those checks can&#39;t "
                            + "disagree.",
                        "Anyone with the repository can regenerate it from the commit below with the command given."
                    ),
                    source.dirty() ? Stream.of(
                        "This draft includes uncommitted changes, so it can&#39;t be reproduced exactly from that commit."
                    ) : Stream.empty()
                ).toList()
            ) + ReportOutline.subheading("provenance-source")
                + ReportMarkup.facts(Stream.concat(own, evidence).toList())
                + ReportOutline.subheading("provenance-scanners") + this.scanners()
                + ReportOutline.subheading("provenance-scope") + ReportMarkup.paragraphs(this.scope())
        );
    }

    // What each source of the report's findings is and when it was read, so a reader knows how current every
    // verdict is.
    private String scanners() {
        Map<String, String> facts = this.report.evidence().facts();
        List<Map.Entry<String, String>> rows = facts.entrySet().stream()
            .filter(entry -> TOOLS.contains(entry.getKey())).sorted(Map.Entry.comparingByKey()).map(
                entry -> Map.entry(
                    ReportMarkup.escape(TOOL_LABELS.getOrDefault(entry.getKey(), entry.getKey())),
                    ReportMarkup.escape(entry.getValue()) + (entry.getValue().endsWith(SNAPSHOT) ? UNRELEASED : "")
                )
            ).toList();
        String scanned = this.report.evidence().scannedAt();
        Stream<Map.Entry<String, String>> queried = ReportVerdict.osvData(this.report.inventory())
            .map(label -> Map.entry(label, ReportMarkup.moment(this.report.source().generatedAt()))).stream();
        return ReportMarkup.paragraphs(
            List.of(
                "The tools and databases the findings come from, with their versions and the dates of their data."
            )
        ) + ReportMarkup.facts(
            Stream.of(
                rows.stream(), Stream.of(
                    Map.entry(
                        ReportVerdict.MAVEN_DATA, scanned.isEmpty() ? "No scan recorded" : ReportMarkup.moment(scanned)
                    )
                ), queried
            ).flatMap(rowsOf -> rowsOf).toList()
        );
    }

    private List<String> scope() {
        ReportInventory inventory = this.report.inventory();
        boolean images = !inventory.images().isEmpty();
        return List.of(
            "The report covers every Java (Maven) package the project&#39;s modules use, whether it ships or is used "
                + "only for tests or the build, the tools the build runs, every JavaScript (npm) package the "
                + "project&#39;s JavaScript applications install, and the Node.js and pnpm versions that ran their "
                + "checks." + (images ? " It also lists the operating system packages of each scanned container "
                    + "image." : ""),
            "A package&#39;s license is the one its published metadata declares, or the one confirmed from release "
                + "evidence in " + ReportOutline.reference("policy-evidence") + ". The license policy covers the Java "
                + "and JavaScript packages and the build tools, not the operating system packages of a container "
                + "image.",
            "The software bill of materials (SBOM) embedded in the HTML edition lists every package of this report in "
                + "one dependency graph. The per-module SBOMs published with the Java modules list only what each "
                + "module ships. Files that reach the product other than through a package manager aren&#39;t "
                + "covered."
        );
    }

    // A module's evidence is keyed by the module's coordinate and what the value is, as the build records it. The
    // label says what the value is in words a reader without the build's vocabulary follows.
    private static String label(String key) {
        return Optional.ofNullable(FACT_LABELS.get(key)).orElseGet(
            () -> EVIDENCE_LABELS.entrySet().stream().filter(entry -> key.endsWith(entry.getKey())).findFirst()
                .map(entry -> key.substring(0, key.length() - entry.getKey().length()) + entry.getValue()).orElse(key)
        );
    }

    // The package is what a reader looks for, so it heads the entry. A text many packages share is headed by how
    // many share it, and the packages follow in small type. A long text is set in two columns, a short one in one.
    private static String notice(Map.Entry<String, List<Map.Entry<String, String>>> group, int index) {
        List<String> coordinates = coordinates(group);
        List<String> files = group.getValue().stream().map(Map.Entry::getValue).distinct().sorted().toList();
        boolean shared = coordinates.size() > 1;
        String title = shared ? "Shared by " + ReportCounts.plural(coordinates.size(), "package version")
            : coordinates.getFirst();
        String packages = shared ? "<p class=\"notice-shared\">" + coordinates.stream().map(ReportMarkup::breakable)
            .collect(Collectors.joining(", ")) + "</p>" : "";
        String length = group.getKey().length() > LONG_TEXT ? " long" : "";
        return "<div class=\"notice\" id=\"notice-" + (index + 1) + "\"><p class=\"title\">"
            + ReportMarkup.escape(title) + "</p>" + packages + "<p class=\"notice-packages\">"
            + ReportMarkup.escape(String.join(", ", files)) + "</p><div class=\"license-text" + length + "\">"
            + ReportNoticeText.render(group.getKey())
            + "</div></div>";
    }

    private static List<String> coordinates(Map.Entry<String, List<Map.Entry<String, String>>> group) {
        return group.getValue().stream().map(Map.Entry::getKey).distinct().sorted().toList();
    }
}
