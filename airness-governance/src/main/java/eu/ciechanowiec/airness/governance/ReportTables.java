package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The package inventory and the dependency rows other sections review, rendered from their evaluated
 * evidence.
 */
@UtilityClass
final class ReportTables {

    /**
     * The inventory group of a Maven artifact a module resolves as a dependency.
     */
    static final String MAVEN = "maven";

    /**
     * The inventory group of an npm package.
     */
    static final String NPM = "npm";

    /**
     * The inventory group of a tool that only runs the build, such as a Maven plugin or the pinned Node.
     */
    static final String TOOLS = "tools";

    private static final String CLASS_OPTIONS = Stream.of(LicenseClass.values())
        .map(licenseClass -> "<option value=\"" + licenseClass.key() + "\">" + licenseClass.label() + "</option>")
        .collect(Collectors.joining());

    private static final String CONTROLS = """
        <div class="controls"><input id="filter-text" type="search" aria-label="Filter by package, license or module"
        placeholder="Filter by package, license or module">
        <select id="filter-scope" aria-label="Scope"><option value="">All scopes</option>
        <option value="production">Production</option><option value="provided">Provided</option>
        <option value="development">Development</option><option value="test">Test</option>
        <option value="build">Build</option></select>
        <select id="filter-class" aria-label="License class"><option value="">All license classes</option>
        """ + CLASS_OPTIONS + """
        </select>
        <output id="filter-count" aria-live="polite"></output></div>
        """;
    private static final List<String> HEADINGS = List.of("Package", "Version", "License", "Class", "Use");
    private static final List<Integer> WIDTHS = List.of(36, 12, 20, 13, 19);
    private static final List<String> MODULE_HEADINGS = List.of(
        "Package", "Version", "License", "Class", "Module: use"
    );
    private static final List<Integer> MODULE_WIDTHS = List.of(30, 10, 18, 12, 30);
    private static final List<String> REVIEW_HEADINGS = List.of("Package", "Version", "License", "Use");
    private static final List<Integer> REVIEW_WIDTHS = List.of(42, 14, 26, 18);
    private static final List<String> IMAGE_HEADINGS = List.of("Package", "Version", "Declared license");
    private static final List<Integer> IMAGE_WIDTHS = List.of(44, 22, 34);
    private static final Set<String> BUILD = Set.of(ReportUse.BUILD);
    private static final Map<String, String> EMPTY = Map.of(
        MAVEN, "No Java (Maven) package is a dependency of the project's modules. Any Java package the build runs as a "
            + "tool is listed in " + ReportOutline.reference("inventory-tools") + ".", NPM,
        "The project uses no JavaScript (npm) package.", TOOLS,
        "The build runs no tool that is listed separately."
    );
    private static final Map<String, String> INTRODUCTIONS = Map.of(
        MAVEN, "Java libraries and other packages from Maven repositories that the project&#39;s modules use.", NPM,
        "JavaScript packages from the npm registry that the project&#39;s JavaScript applications use.", TOOLS,
        "Tools the build downloads and runs, such as its plugins, code checkers and the JavaScript runtime. They "
            + "aren&#39;t distributed with the product. They are listed because the license policy and the "
            + "vulnerability check cover them too."
    );

    static String inventory(ComplianceReport report) {
        ReportInventory inventory = report.inventory();
        List<String> modules = modules(inventory);
        Comparator<ReportPackage> byName = Comparator
            .comparing((ReportPackage entry) -> ReportCoordinate.of(entry.coordinate()).name())
            .thenComparing(ReportPackage::coordinate);
        Map<String, List<ReportPackage>> grouped = inventory.packages().stream().sorted(byName)
            .collect(Collectors.groupingBy(ReportTables::group));
        String where = modules.size() == 1
            ? "All packages belong to the project&#39;s single build module, " + ReportMarkup.code(modules.getFirst())
                + "."
            : "Where several of the project&#39;s modules use a package, its row names each module with the "
                + "package&#39;s use there.";
        return ReportOutline.section(
            "inventory", ReportMarkup.paragraphs(
                List.of(
                    "This section lists every package version in use, "
                        + ReportCounts.plural(inventory.packages().size(), "row") + ", with its license by standard "
                        + "SPDX identifier, the license&#39;s class, and how the package is used.",
                    "The <em>Use</em> column says whether the package ships with the product (<em>Production</em>), "
                        + "is used only for <em>Development</em>, <em>Test</em> or the <em>Build</em>, or is "
                        + "<em>Provided</em> by the environment the product runs in. It also says whether the project "
                        + "names the package itself (<em>direct</em>) or another package needs it "
                        + "(<em>transitive</em>). A Java package ships when the product carries it to run, which "
                        + "Maven calls the compile and runtime scopes. A JavaScript package ships when one of the "
                        + "project&#39;s applications depends on it outside its development dependencies, directly "
                        + "or through other packages.",
                    where
                )
            ) + downloads(report.fileStem()) + CONTROLS
                + Stream.of(MAVEN, NPM, TOOLS).map(
                    key -> listing(key, grouped.getOrDefault(key, List.of()), inventory.policy(), modules)
                ).collect(Collectors.joining()) + ReportOutline.subheading("inventory-images") + images(inventory)
        );
    }

    /**
     * The inventory group a package is listed under.
     *
     * @param entry a resolved package
     * @return {@link #NPM}, {@link #MAVEN} or {@link #TOOLS}
     */
    static String group(ReportPackage entry) {
        if (NPM.equals(ReportCoordinate.ecosystem(entry.coordinate()))) {
            return NPM;
        }
        boolean tool = entry.uses().stream().map(ReportUse::scope).allMatch(BUILD::contains);
        return tool ? TOOLS : MAVEN;
    }

    static String review(ReportLicensePolicy policy, ReportPackage entry) {
        ReportCoordinate coordinate = ReportCoordinate.of(entry.coordinate());
        return ReportMarkup.row(
            List.of(
                ReportMarkup.breakable(coordinate.name()), ReportMarkup.mono(coordinate.version()),
                license(entry.licenseIds(policy)), uses(entry, List.of())
            )
        );
    }

    static List<String> reviewHeadings() {
        return REVIEW_HEADINGS;
    }

    static List<Integer> reviewWidths() {
        return REVIEW_WIDTHS;
    }

    /**
     * A license expression as a chip that breaks only between licenses, never inside an identifier.
     *
     * @param expression the normalized expression
     * @return the chip markup
     */
    static String license(String expression) {
        return "<span class=\"license\">" + Stream.of(expression.split(" OR ", -1))
            .map(alternative -> "<code>" + ReportMarkup.escape(alternative) + "</code>")
            .collect(Collectors.joining("<span class=\"or\">or</span>")) + "</span>";
    }

    // One inventory group's table, which names each use's module only when several modules take part.
    private static String listing(
        String group, List<ReportPackage> packages, ReportLicensePolicy policy, List<String> modules
    ) {
        String id = "inventory-" + group;
        if (packages.isEmpty()) {
            return ReportOutline.subheading(id) + ReportMarkup.empty(EMPTY.getOrDefault(group, ""));
        }
        String introduction = ReportMarkup.paragraphs(List.of(INTRODUCTIONS.getOrDefault(group, "")));
        List<String> named = modules.size() > 1 ? modules : List.of();
        String rows = packages.stream().map(entry -> row(policy, entry, named)).collect(Collectors.joining());
        return ReportOutline.subheading(id) + introduction + "<div class=\"table-wrap\">" + ReportMarkup.tableStart(
            named.isEmpty() ? HEADINGS : MODULE_HEADINGS, named.isEmpty() ? WIDTHS : MODULE_WIDTHS,
            " data-inventory=\"" + id + "\""
        ) + rows + "</tbody></table></div>";
    }

    private static String images(ReportInventory inventory) {
        Set<String> listed = inventory.packages().stream().map(ReportPackage::coordinate)
            .collect(Collectors.toUnmodifiableSet());
        Map<String, List<ReportImagePackage>> images = inventory.images().stream()
            .filter(entry -> entry.system() || !listed.contains(withoutQualifiers(entry.purl())))
            .collect(Collectors.groupingBy(ReportImagePackage::image));
        if (images.isEmpty()) {
            return ReportMarkup.empty("No container image was scanned.");
        }
        return ReportMarkup.paragraphs(
            List.of(
                "Each container image the product ships holds the operating system packages listed below, besides "
                    + "the JavaScript packages of " + ReportOutline.reference("inventory-npm") + ". Each carries the "
                    + "license its operating system distribution declares, as written. The license policy doesn&#39;t "
                    + "judge these packages: they come with the image&#39;s base, the operating system the image is "
                    + "built on, and ship as part of it unchanged."
            )
        ) + images.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(
            image -> "<p class=\"table-title\">" + ReportMarkup.escape(image.getKey()) + "</p>" + ReportMarkup.table(
                IMAGE_HEADINGS, IMAGE_WIDTHS, image.getValue().stream()
                    .sorted(Comparator.comparing(ReportImagePackage::purl)).map(ReportTables::imageRow).toList(),
                "The image holds no package."
            )
        ).collect(Collectors.joining());
    }

    private static String imageRow(ReportImagePackage entry) {
        ReportCoordinate coordinate = ReportCoordinate.of(entry.purl());
        String licenses = String.join(" OR ", entry.licenses());
        return ReportMarkup.row(
            List.of(
                ReportMarkup.breakable(coordinate.name()), ReportMarkup.mono(coordinate.version()),
                licenses.isEmpty() ? "<span class=\"empty\">Not declared</span>" : license(licenses)
            )
        );
    }

    static String licenseEvidence(ReportInventory inventory) {
        String entries = inventory.packages().stream().flatMap(
            entry -> entry.notices().entrySet().stream()
                .filter(notice -> notice.getKey().startsWith(ReportPackage.LICENSE_EVIDENCE_PREFIX))
                .map(notice -> evidence(entry.coordinate(), notice))
        ).collect(Collectors.joining());
        return entries.isEmpty() ? "" : ReportMarkup.paragraphs(
            List.of(
                "Some packages don&#39;t state their license in their published metadata. For each package below, "
                    + "the license was confirmed from the original project&#39;s own release files instead, and that "
                    + "evidence is reproduced. Each entry applies only to the exact file its fingerprint identifies, "
                    + "so another version or a modified file gets no license from it. The license found is then "
                    + "checked against the policy like any other."
            )
        ) + entries;
    }

    private static String evidence(String coordinate, Map.Entry<String, String> notice) {
        String source = notice.getKey().substring(ReportPackage.LICENSE_EVIDENCE_PREFIX.length());
        String link = source.startsWith("https://")
            ? "<a href=\"" + ReportMarkup.escape(source) + "\" rel=\"noreferrer noopener\">"
                + ReportMarkup.breakable(source) + "</a>"
            : ReportMarkup.escape(source);
        return ReportLicenseEvidence.decode(notice.getValue()).map(
            evidence -> "<div class=\"notice evidence\"><p class=\"title\">" + ReportMarkup.escape(coordinate) + "</p>"
                + ReportMarkup.facts(
                    List.of(
                        Map.entry("License found", ReportMarkup.code(evidence.license())),
                        Map.entry("File fingerprint (SHA-256)", ReportMarkup.digest(evidence.sha256())),
                        Map.entry(
                            "License in the published metadata", evidence.declared().isEmpty() ? "None stated"
                                : ReportMarkup.escape(String.join(" OR ", evidence.declared()))
                        ),
                        Map.entry("Where the license was found", link),
                        Map.entry("How the evidence shows it", ReportNoticeText.prose(evidence.reason()))
                    ), "plan"
                ) + evidence.texts().entrySet().stream().map(
                    text -> "<p class=\"notice-packages\">" + ReportMarkup.escape(text.getKey())
                        + "</p><div class=\"license-text\">"
                        + ReportNoticeText.render(ReportNoticeText.reflow(text.getValue())) + "</div>"
                ).collect(Collectors.joining()) + "</div>"
        ).orElseGet(
            () -> "<div class=\"notice evidence\"><p class=\"title\">" + ReportMarkup.escape(coordinate) + "</p>"
                + "<p class=\"notice-packages\">" + link + "</p><div class=\"license-text\">"
                + ReportNoticeText.render(ReportNoticeText.reflow(notice.getValue())) + "</div></div>"
        );
    }

    private static String row(ReportLicensePolicy policy, ReportPackage entry, List<String> modules) {
        LicenseClass classification = policy.classification(entry.licenses());
        ReportCoordinate coordinate = ReportCoordinate.of(entry.coordinate());
        String scope = entry.uses().stream().map(use -> use.production() ? ReportUse.PRODUCTION : use.scope())
            .distinct().sorted().collect(Collectors.joining(" "));
        return "<tr data-scope=\"" + ReportMarkup.escape(scope) + "\" data-class=\"" + classification.key() + "\">"
            + "<td>" + ReportMarkup.breakable(coordinate.name()) + "</td><td>" + ReportMarkup.mono(coordinate.version())
            + "</td><td>" + license(entry.licenseIds(policy)) + "</td><td>" + ReportChips.classChip(classification)
            + "</td><td>" + uses(entry, modules) + "</td></tr>";
    }

    // "Production, direct", one line per distinct use. With several modules, each line names its module.
    private static String uses(ReportPackage entry, List<String> modules) {
        return entry.uses().stream().map(
            use -> (modules.isEmpty() ? "" : use.module() + ": ") + use.label() + ", "
                + use.relationLabel().toLowerCase(Locale.ROOT)
        ).distinct().sorted().map(ReportMarkup::escape).collect(Collectors.joining("<br>"));
    }

    private static List<String> modules(ReportInventory inventory) {
        return inventory.packages().stream().flatMap(entry -> entry.uses().stream()).map(ReportUse::module)
            .distinct().sorted().toList();
    }

    private static String withoutQualifiers(String purl) {
        int qualifiers = purl.indexOf('?');
        return qualifiers < 0 ? purl : purl.substring(0, qualifiers);
    }

    private static String downloads(String stem) {
        String safe = ReportMarkup.escape(stem);
        return "<div class=\"downloads\"><button type=\"button\" hidden data-download=\"sbom\" data-file=\"" + safe
            + ".cdx.json\">Download SBOM (CycloneDX JSON)</button>"
            + "<button type=\"button\" hidden data-download=\"csv\" data-file=\"" + safe
            + ".csv\">Download inventory (CSV)</button></div>";
    }
}
