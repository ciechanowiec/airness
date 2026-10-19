package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The licenses section, which judges every resolved artifact, and the policy section it is judged against.
 *
 * @param report evaluated report
 */
record ReportLicenseSections(ComplianceReport report) {

    private static final List<String> CLASSES = List.of("Permissive", "Weak copyleft", "Outside policy");
    private static final int CHART_LICENSES = 10;
    private static final List<String> REVIEW_HEADINGS = List.of("Artifact", "License", "Module / scope / relation");

    String licenses() {
        ReportInventory inventory = this.report.inventory();
        long total = inventory.packages().size();
        long production = inventory.packages().stream().filter(ReportPackage::production).count();
        return ReportOutline.section(
            "licenses", ReportMarkup.paragraphs(
                List.of(
                    ReportCounts.plural(total, "artifact version") + " " + ReportCounts.agreeing(total, "is", "are")
                        + " resolved, " + production + " on a production classpath and " + (total - production)
                        + " used only for tests, provided scope and the build.",
                    "Each license is judged against the policy in " + ReportOutline.reference("policy") + "."
                )
            ) + ReportOutline.subheading("license-classes") + this.classStack()
                + ReportOutline.subheading("common-licenses") + this.licenseBars()
                + ReportOutline.subheading("review-weak-copyleft") + ReportMarkup.paragraphs(
                    List.of(
                        "The policy admits weak copyleft only for components used unmodified. Test, provided and "
                            + "build components aren&#39;t distributed with the application."
                    )
                ) + ReportMarkup.table(
                    REVIEW_HEADINGS, inventory.weakCopyleft().stream().map(ReportTables::review).toList(),
                    "No dependency carries a weak copyleft license."
                ) + ReportOutline.subheading("review-outside") + ReportMarkup.table(
                    REVIEW_HEADINGS, inventory.refused().stream().map(ReportTables::review).toList(),
                    "No dependency declares a license outside the policy."
                )
        );
    }

    String policy() {
        ReportLicensePolicy policy = this.report.inventory().policy();
        List<String> permissive = policy.allowed().stream().filter(license -> !ReportLicensePolicy.copyleft(license))
            .sorted().toList();
        List<String> weak = policy.allowed().stream().filter(ReportLicensePolicy::copyleft).sorted().toList();
        String evidence = ReportTables.licenseEvidence(this.report.inventory());
        return ReportOutline.section(
            "policy", ReportMarkup.paragraphs(
                List.of(
                    "The build enforces this policy, rendered from the same allowlist the license check reads. A "
                        + "supplier&#39;s declared license is normalized only by the aliases that check declares. An "
                        + "artifact that declares several licenses is admitted when any one of them is."
                )
            ) + ReportOutline.subheading("policy-permissive") + ReportMarkup.paragraphs(
                List.of(
                    "The policy admits " + ReportCounts.plural(permissive.size(), "license")
                        + " with no copyleft obligation. Attribution, where the license requires it, is satisfied by "
                        + ReportOutline.reference("notices") + "."
                )
            ) + ReportMarkup.idList(permissive) + ReportOutline.subheading("policy-weak-copyleft")
                + ReportMarkup.paragraphs(
                    List.of(
                        "The policy admits " + ReportCounts.plural(weak.size(), "license")
                            + " only for dependencies used unmodified. Their copyleft, whether file-level or "
                            + "library-level, doesn&#39;t extend to the project&#39;s own source when the component is "
                            + "used as published."
                    )
                ) + ReportMarkup.idList(weak) + ReportOutline.subheading("policy-outside") + ReportMarkup.paragraphs(
                    List.of(
                        "Every other license is outside the policy, including strong copyleft such as the GNU General "
                            + "Public License, licenses that restrict the field of use, and any artifact whose license "
                            + "isn&#39;t declared. A single such artifact fails the build."
                    )
                ) + ReportOutline.subheading("policy-evidence") + (evidence.isEmpty()
                    ? ReportMarkup.empty("No license was supplemented from upstream release evidence.") : evidence)
        );
    }

    private String classStack() {
        ReportInventory inventory = this.report.inventory();
        long total = inventory.packages().size();
        Map<String, Long> counts = inventory.packages().stream().collect(
            Collectors.groupingBy(entry -> inventory.policy().classification(entry.licenses()), Collectors.counting())
        );
        Function<String, String> share = name -> ReportCounts.percentOf(counts.getOrDefault(name, 0L), total);
        String description = CLASSES.stream().map(name -> name + " " + share.apply(name) + " percent")
            .collect(Collectors.joining(", "));
        return "<div class=\"stack\" role=\"img\" aria-label=\"" + description + "\">" + CLASSES.stream().map(
            name -> "<i class=\"seg-" + ReportChips.classKey(name) + "\" style=\"width:" + share.apply(name)
                + "%\"></i>"
        ).collect(Collectors.joining()) + "</div><ul class=\"legend\">" + CLASSES.stream().map(
            name -> "<li class=\"key-" + ReportChips.classKey(name) + "\">" + name + ": "
                + counts.getOrDefault(name, 0L) + " (" + share.apply(name) + "%)</li>"
        ).collect(Collectors.joining()) + "</ul>";
    }

    private String licenseBars() {
        ReportInventory inventory = this.report.inventory();
        Map<String, List<ReportPackage>> grouped = inventory.packages().stream()
            .collect(Collectors.groupingBy(ReportPackage::licenseSummary));
        List<Map.Entry<String, List<ReportPackage>>> ordered = grouped.entrySet().stream().sorted(
            Comparator.comparingInt((Map.Entry<String, List<ReportPackage>> entry) -> entry.getValue().size())
                .reversed()
                .thenComparing(Map.Entry::getKey)
        ).toList();
        List<Map.Entry<String, List<ReportPackage>>> shown = ordered.stream().limit(CHART_LICENSES).toList();
        List<Map.Entry<String, List<ReportPackage>>> rest = ordered.stream().skip(CHART_LICENSES).toList();
        long largest = shown.isEmpty() ? 0 : shown.getFirst().getValue().size();
        long others = rest.stream().mapToLong(entry -> entry.getValue().size()).sum();
        return "<div class=\"bars\">" + Stream.concat(
            shown.stream().map(
                entry -> bar(
                    entry.getKey(), entry.getValue().size(), largest, ReportChips.classKey(
                        inventory.policy().classification(entry.getValue().getFirst().licenses())
                    )
                )
            ),
            rest.isEmpty() ? Stream.empty() : Stream.of(
                bar(ReportCounts.plural(rest.size(), "other license"), others, largest, "other")
            )
        ).collect(Collectors.joining()) + "</div>";
    }

    private static String bar(String label, long count, long largest, String tone) {
        return "<span class=\"label\">" + ReportMarkup.escape(label) + "</span><span class=\"track\"><span "
            + "class=\"fill fill-" + tone + "\" style=\"width:" + ReportCounts.percentOf(count, largest)
            + "%\"></span></span><span class=\"count\">" + count + "</span>";
    }
}
