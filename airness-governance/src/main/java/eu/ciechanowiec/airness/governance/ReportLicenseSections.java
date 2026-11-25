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

    private static final int CHART_LICENSES = 10;
    private static final List<String> ALIAS_HEADINGS = List.of("Name as declared", "Treated as", "Package versions");
    private static final List<Integer> ALIAS_WIDTHS = List.of(56, 28, 16);
    private static final List<String> CLASSES = List.of(
        "Every package version falls into one class by its license. <em>Permissive</em> licenses require that "
            + "copyright and license notices are kept. <em>Weak copyleft</em> licenses also require, when the "
            + "component is distributed, that its source code, including any changes to it, is available under the "
            + "same license, and some add duties such as letting users replace the component. The code that uses it "
            + "isn&#39;t affected. <em>Outside the policy</em> means the policy doesn&#39;t allow the license."
    );
    private static final List<String> OUTSIDE = List.of(
        "Each package version below has a license the policy doesn&#39;t allow, and fails the license check. It has "
            + "to be replaced, or its license approved and added to the policy."
    );

    String licenses() {
        ReportInventory inventory = this.report.inventory();
        long total = inventory.packages().size();
        long production = inventory.packages().stream().filter(ReportPackage::production).count();
        return ReportOutline.section(
            "licenses", ReportMarkup.paragraphs(
                List.of(
                    ReportCounts.plural(total, "package version") + " " + ReportCounts.agreeing(total, "is", "are")
                        + " in use: " + production + " " + ReportCounts.agreeing(production, "ships", "ship")
                        + " with the product, marked <em>Production</em>, and " + (total - production) + " "
                        + ReportCounts.agreeing(total - production, "is", "are") + " used only to develop, test or "
                        + "build it, or supplied by the environment it runs in, and "
                        + ReportCounts.agreeing(total - production, "isn&#39;t", "aren&#39;t")
                        + " part of the delivered product.",
                    "Each license is checked against the policy in " + ReportOutline.reference("policy") + ", and "
                        + ReportOutline.reference("inventory") + " lists every package."
                )
            ) + ReportOutline.subheading("license-classes") + ReportMarkup.paragraphs(CLASSES) + this.classStack()
                + ReportOutline.subheading("common-licenses") + ReportMarkup.paragraphs(
                    List.of("The number of package versions under each license, by its standard SPDX identifier.")
                ) + this.licenseBars() + ReportOutline.subheading("review-weak-copyleft") + ReportMarkup.paragraphs(
                    List.of(
                        "The policy allows weak copyleft licenses only for components used as published, without "
                            + "changes. Keeping them unmodified is the project&#39;s responsibility. Only the rows "
                            + "marked <em>Production</em> are distributed with the product, and only for them do the "
                            + "license&#39;s terms for distribution apply, such as making the component&#39;s source "
                            + "code available."
                    )
                ) + ReportMarkup.table(
                    ReportTables.reviewHeadings(), ReportTables.reviewWidths(),
                    inventory.weakCopyleft().stream().map(entry -> ReportTables.review(inventory.policy(), entry))
                        .toList(),
                    "No package version has a weak copyleft license."
                ) + ReportOutline.subheading("review-outside") + (inventory.refused().isEmpty()
                    ? ReportMarkup.empty("No package version has a license outside the policy.")
                    : ReportMarkup.paragraphs(OUTSIDE) + ReportMarkup.table(
                        ReportTables.reviewHeadings(), ReportTables.reviewWidths(),
                        inventory.refused().stream().map(entry -> ReportTables.review(inventory.policy(), entry))
                            .toList(), ""
                    ))
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
                    "This is the license policy every package is checked against. It is printed from the same list the "
                        + "license check uses, so the two can&#39;t differ. The policy is part of the project&#39;s "
                        + "build configuration, so a change to it is reviewed like any other change to the project.",
                    "A package offered under a choice of licenses is allowed when any one of them is allowed. A "
                        + "package under several licenses that all apply at once is allowed only when every one of "
                        + "them is. Different spellings of a license name are matched to one standard identifier, as "
                        + ReportOutline.reference("policy-aliases") + " shows."
                )
            ) + ReportOutline.subheading("policy-permissive") + ReportMarkup.paragraphs(
                List.of(
                    "The policy allows " + ReportCounts.plural(permissive.size(), "license") + " that mainly require "
                        + "that copyright and license notices are kept with the software. The license files found in "
                        + "the packages the product ships are reproduced in " + ReportOutline.reference("notices")
                        + ", which also lists the shipped packages that contain none."
                )
            ) + ReportMarkup.idList(permissive) + ReportOutline.subheading("policy-weak-copyleft")
                + ReportMarkup.paragraphs(
                    List.of(
                        "The policy allows " + ReportCounts.plural(weak.size(), "license") + " only for components "
                            + "used unmodified. Used that way, their terms don&#39;t extend to the project&#39;s own "
                            + "code. Their terms for the component itself, such as making its source code available "
                            + "when it is distributed, still apply."
                    )
                ) + ReportMarkup.idList(weak) + ReportOutline.subheading("policy-outside") + ReportMarkup.paragraphs(
                    List.of(
                        "Every other license is outside the policy, such as strong copyleft licenses like the GNU "
                            + "General Public License and licenses that restrict how the software may be used. So is a "
                            + "package that declares no license. A single such package fails the license check."
                    )
                ) + ReportOutline.subheading("policy-aliases") + this.aliases()
                + ReportOutline.subheading("policy-evidence") + (evidence.isEmpty()
                    ? ReportMarkup.empty("No package needed its license confirmed from release evidence.") : evidence)
        );
    }

    // Every declared spelling the report reads as another name, so a reader can trace each identifier the report
    // shows back to what the supplier wrote.
    private String aliases() {
        ReportInventory inventory = this.report.inventory();
        ReportLicensePolicy policy = inventory.policy();
        Map<String, Long> declared = inventory.packages().stream()
            .flatMap(entry -> entry.licenses().stream().distinct())
            .filter(name -> !policy.display(name).equals(name))
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return ReportMarkup.paragraphs(
            List.of(
                "Package authors write license names freely, for example &quot;The Apache Software License, Version "
                    + "2.0&quot;. The report shows each license by its standard SPDX identifier, such as "
                    + "<code>Apache-2.0</code>, and this table shows every name it converted, so a reviewer can check "
                    + "each conversion. Some names are ambiguous, such as a bare &quot;BSD&quot; or a name that offers "
                    + "a choice of licenses, so their conversion is a judgement a reviewer should confirm. The CSV "
                    + "inventory in the HTML edition of this report keeps each name as written."
            )
        ) + ReportMarkup.table(
            ALIAS_HEADINGS, ALIAS_WIDTHS, declared.entrySet().stream().sorted(
                Comparator.comparing((Map.Entry<String, Long> entry) -> policy.display(entry.getKey()))
                    .thenComparing(Map.Entry::getKey)
            ).map(
                entry -> ReportMarkup.row(
                    List.of(
                        ReportMarkup.escape(entry.getKey()), ReportTables.license(policy.display(entry.getKey())),
                        String.valueOf(entry.getValue())
                    )
                )
            ).toList(), "Every declared license name is already a standard identifier."
        );
    }

    private String classStack() {
        ReportInventory inventory = this.report.inventory();
        long total = inventory.packages().size();
        Map<LicenseClass, Long> counts = inventory.packages().stream().collect(
            Collectors.groupingBy(entry -> inventory.policy().classification(entry.licenses()), Collectors.counting())
        );
        Function<LicenseClass, String> share = name -> ReportCounts.percentOf(counts.getOrDefault(name, 0L), total);
        List<LicenseClass> classes = List.of(LicenseClass.values());
        String description = classes.stream().map(name -> name.label() + " " + share.apply(name) + " percent")
            .collect(Collectors.joining(", "));
        return "<div class=\"stack\" role=\"img\" aria-label=\"" + description + "\">" + classes.stream().map(
            name -> "<i class=\"seg-" + name.key() + "\" style=\"width:" + share.apply(name) + "%\"></i>"
        ).collect(Collectors.joining()) + "</div><ul class=\"legend\">" + classes.stream().map(
            name -> "<li class=\"key-" + name.key() + "\">" + name.label() + ": " + counts.getOrDefault(name, 0L)
                + " (" + share.apply(name) + "%)</li>"
        ).collect(Collectors.joining()) + "</ul>";
    }

    private String licenseBars() {
        ReportInventory inventory = this.report.inventory();
        Map<String, List<ReportPackage>> grouped = inventory.packages().stream()
            .collect(Collectors.groupingBy(entry -> entry.licenseIds(inventory.policy())));
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
                    entry.getKey(), entry.getValue().size(), largest,
                    inventory.policy().classification(entry.getValue().getFirst().licenses()).key()
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
