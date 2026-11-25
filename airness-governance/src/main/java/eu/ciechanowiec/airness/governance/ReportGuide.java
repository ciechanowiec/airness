package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The guide that opens the report: what it is, where each topic is, the words it uses, and what it leaves out. A
 * reader should need no package manager's vocabulary to follow the report, so every term the sections rely on is
 * explained here once.
 */
@UtilityClass
final class ReportGuide {

    private static final List<String> HEADINGS = List.of("Term", "Meaning");
    private static final List<Integer> WIDTHS = List.of(24, 76);
    private static final List<String> SECTIONS = List.of(
        "The first page gives the result. A line marked with a tick is a check that passed, a cross marks one that "
            + "needs action, and a warning sign marks one to watch.",
        opening("vulnerabilities") + " lists the known vulnerabilities and the package each one affects.",
        opening("licenses") + " shows the license of every package, and " + ReportOutline.reference("policy")
            + " the policy each license is checked against.",
        opening("inventory") + " lists every package with its version, license and use.",
        opening("notices") + " reproduces the license texts that accompany the product.",
        opening("provenance") + " says what was scanned, with which tools and data, and how to reproduce the report."
    );
    private static final List<List<String>> TERMS = List.of(
        List.of(
            "Package version", "One version of one third-party software component, such as a Java library from a "
                + "Maven repository or a JavaScript package from the npm registry."
        ),
        List.of(
            "Production", "The package is part of the delivered product: the product needs it to run and carries "
                + "it. The license terms for distribution apply to these packages, and their license texts are "
                + "reproduced in section 5."
        ),
        List.of(
            "Development, Test, Provided, Build", "Development and Test packages are used only while the product is "
                + "developed or tested. A Provided package is needed to compile the product but is supplied by the "
                + "environment the product runs in. A Build package is a tool the build runs, such as a compiler or a "
                + "code checker. None of them is a runtime part of the delivered product, though a build tool can "
                + "copy parts of itself into it, as described under what the report does not cover."
        ),
        List.of(
            "Direct, transitive", "Direct: the project names the package itself. Transitive: another package the "
                + "project uses needs it."
        ),
        List.of(
            "License classes", "Permissive licenses require that copyright and license notices are kept. Weak "
                + "copyleft licenses also require, when the component is distributed, that its source code, including "
                + "any changes to it, is available under the same license, and some add duties such as letting users "
                + "replace the component. The code that uses it is not affected. A license outside the policy is one "
                + "the policy does not allow."
        ),
        List.of(
            "Vulnerability status", "Open: nothing recorded explains why the vulnerability can&#39;t be exploited "
                + "here, so it needs action. Awaiting fix: it is in a container image and no release of the "
                + "image&#39;s base includes a fix yet. Mitigated: a recorded reason explains why it can&#39;t be "
                + "exploited in this product, with a plan for removing the exception."
        ),
        List.of(
            "Severity", "Critical, high, medium or low, as the vulnerability databases rate it. Unrated means no "
                + "database gave a rating."
        ),
        List.of(
            "Vulnerability identifiers and databases", "CVE and GHSA identify a vulnerability in public databases: "
                + "CVE in the National Vulnerability "
                + "Database (NVD) and GHSA in GitHub&#39;s advisory database. OSV is an open database that collects "
                + "advisories from many sources and has identifiers of its own. Each identifier links to its entry."
        ),
        List.of(
            "SPDX identifier", "The standard short name of a license, such as <code>Apache-2.0</code> or "
                + "<code>MIT</code>, from the SPDX license list."
        ),
        List.of(
            "SBOM", "Software bill of materials: a machine-readable list of every package and how the packages "
                + "depend on each other. The HTML edition of this report embeds one and offers it for download."
        ),
        List.of(
            "HTML edition", "The same report as an HTML file, written beside this PDF under the same name. Besides "
                + "the report, it offers the SBOM and a CSV list of every package for download."
        ),
        List.of(
            "Fingerprint (SHA-256)", "A value computed from the exact content of a file. Any change to the content "
                + "changes the value, so a fingerprint identifies one exact file."
        )
    );
    private static final List<String> LIMITS = List.of(
        "Code a build tool copies into the delivered product, such as helper code a JavaScript bundler inlines. The "
            + "package it comes from is listed with its own use, not as Production.",
        "Whether delivering the product counts as distribution under a license, for example when it is offered as a "
            + "hosted service. That is a legal question the report doesn&#39;t answer.",
        "Files that reach the product other than through a package manager, such as a library a script downloads or "
            + "source code copied into the project.",
        "Container images that were not scanned. Section 4.4 lists the packages of each scanned image, and the first "
            + "page warns when an image was not scanned.",
        "Whether each license obligation is met in practice, such as keeping weak copyleft components unmodified or "
            + "offering their source code where a license requires it. The report shows which terms apply, not how "
            + "they are fulfilled.",
        "Vulnerabilities not yet published in the databases at the dates given on the first page."
    );

    /**
     * The guide, set between the contents and the first section.
     *
     * @return the guide markup
     */
    static String render() {
        return "<div id=\"guide\"><h2>How to read this report</h2>" + ReportMarkup.paragraphs(
            List.of(
                "This report lists the open source software the product is built from: the license of every package "
                    + "and how it fits the license policy, and the known security vulnerabilities in those packages. "
                    + "It is generated from the project&#39;s build, from the source the first page identifies, not "
                    + "written by hand."
            )
        ) + list(SECTIONS) + "<p class=\"table-title\">Terms</p>" + ReportMarkup.table(
            HEADINGS, WIDTHS, TERMS.stream().map(ReportMarkup::row).toList(), ""
        ) + "<p class=\"table-title\">What the report does not cover</p>" + list(LIMITS) + "</div>";
    }

    // A reference that opens a sentence, as "Section 1".
    private static String opening(String id) {
        String reference = ReportOutline.reference(id);
        return Character.toUpperCase(reference.charAt(0)) + reference.substring(1);
    }

    private static String list(List<String> items) {
        return "<ul class=\"guide-list\">" + items.stream().map(item -> "<li>" + item + "</li>")
            .collect(Collectors.joining()) + "</ul>";
    }
}
