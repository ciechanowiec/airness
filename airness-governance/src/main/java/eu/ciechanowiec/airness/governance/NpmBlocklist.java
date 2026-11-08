package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * The npm packages Airness refuses by name, whatever license their manifest declares.
 *
 * <p>The license allowlist judges a package by the identifier its own manifest carries, and that is the wrong
 * question for three families. A driver is permissively licensed while the only server it reaches is not. A
 * package downloads a binary under terms its manifest never mentions. A project relicensed at a known release
 * keeps its old permissive versions. Where a project turned non-permissive at a known release, the
 * entry carries that floor and earlier releases are left to the allowlist. Every entry names the reason and the
 * replacement. These are the framework-neutral entries of the list the node layer kept before. The entries that
 * exist because of one framework belong to that framework's layer.
 */
@UtilityClass
public final class NpmBlocklist {

    private static final String POSTGRES = "PostgreSQL, through pg";
    private static final String PDF = "pdfjs-dist with @napi-rs/canvas";
    private static final String EDITOR = "an MIT-licensed editor such as lexical";
    private static final String N8N = "n8n is under the Sustainable Use License, which is not open source";
    private static final List<Entry> ENTRIES = List.of(
        Entry.of(
            "eslint-plugin-jsx-a11y", "the retired JSX accessibility analyzer duplicates the checks the oxlint gate"
                + " owns", "the harness's oxlint gate; remove the plugin"
        ),
        Entry.of("mongodb", BlocklistEntries.MONGODB, POSTGRES),
        Entry.of("mongoose", BlocklistEntries.MONGODB, POSTGRES),
        Entry.of("mongodb-memory-server*", "downloads the SSPL MongoDB server binary at install time", POSTGRES),
        Entry.of("@mongodb-js/*", BlocklistEntries.MONGODB, POSTGRES),
        Entry.of("mongodb-client-encryption", BlocklistEntries.MONGODB, POSTGRES),
        Entry.of("@elastic/elasticsearch", BlocklistEntries.ELASTIC, "@opensearch-project/opensearch"),
        Entry.of(
            "@elastic/eui", "Elastic UI is dual-licensed SSPL and Elastic License 2.0, neither of which is"
                + " permissive", "@opensearch-project/oui"
        ),
        Entry.of("elastic-apm-node", BlocklistEntries.ELASTIC_APM, "@opentelemetry/sdk-node"),
        Entry.of(
            "puppeteer", "downloads Chrome for Testing under Google terms, which are not open source",
            "playwright, or puppeteer-core pointed at the Chromium Playwright installs"
        ),
        Entry.of("pdf2pic", BlocklistEntries.GHOSTSCRIPT, PDF),
        Entry.of("node-gs", BlocklistEntries.GHOSTSCRIPT, PDF),
        Entry.of("ghostscript4js", BlocklistEntries.GHOSTSCRIPT, PDF),
        Entry.of("mupdf", "MuPDF is AGPL", PDF),
        Entry.of("n8n", N8N, "an open-source workflow engine"),
        Entry.of("n8n-*", "an n8n extension serves only the Sustainable Use License host", "nothing"),
        Entry.of(
            "directus", "Directus is under the Business Source License, which is not open source",
            "an open-source headless CMS"
        ),
        new Entry(
            "ua-parser-js", Optional.of("2.0.0"), "ua-parser-js 2 is AGPL with a paid exemption",
            "ua-parser-js 1, or bowser"
        ),
        new Entry(
            "mapbox-gl", Optional.of("2.0.0"), "Mapbox GL JS 2 onward is under proprietary Mapbox terms",
            "maplibre-gl"
        ),
        new Entry("tinymce", Optional.of("7.0.0"), "TinyMCE 7 onward is GPL or commercial", EDITOR),
        Entry.of("@ckeditor/*", "CKEditor 5 is GPL or commercial", EDITOR),
        Entry.of("highcharts", "Highcharts is under a proprietary licence", "chart.js or echarts"),
        new Entry(
            "@apollo/gateway", Optional.of("2.0.0"), "Apollo Gateway 2 onward is Elastic License 2.0, which is"
                + " not open source", "@graphql-hive/gateway"
        )
    );

    /**
     * Why a resolved npm package is refused.
     *
     * @param name     the package name
     * @param version  the resolved version
     * @param location the node project whose inventory resolves it
     * @return the refusal, one offence line naming the reason and the replacement, or nothing
     */
    public static Optional<String> refusal(String name, String version, String location) {
        return ENTRIES.stream().filter(entry -> entry.refuses(name, version)).findFirst().map(
            entry -> new Refusal(name + '@' + version, entry.reason(), entry.replacement()).at(location)
        );
    }

    /**
     * One refused package family.
     *
     * @param name        the package name, a trailing asterisk matching every name under that prefix
     * @param floor       the first refused version, when the package was once permissively licensed
     * @param reason      why it is refused
     * @param replacement what to depend on instead
     */
    private record Entry(String name, Optional<String> floor, String reason, String replacement) {

        static Entry of(String name, String reason, String replacement) {
            return new Entry(name, Optional.empty(), reason, replacement);
        }

        boolean refuses(String candidate, String version) {
            return NamePattern.matches(this.name, candidate) && this.floor.map(
                first -> new ComparableVersion(version).compareTo(new ComparableVersion(first)) >= 0
            ).orElse(true);
        }
    }
}
