package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Everything one image holds: the distribution it is built on and its packages.
 *
 * <p>OSV keeps a distribution's advisories in an ecosystem per release, and reads Alpine, Debian and Ubuntu, which
 * the base images a Java or Node.js application ships on are built on. An image whose system packages belong to
 * another distribution cannot be judged, and is refused rather than passed unread. Of the language packages, those of
 * an ecosystem OSV reads by package URL are asked. Java archives never reach the list, since the dependency scan
 * judges the ones the Maven build put there.
 *
 * @param distribution the distribution's identifier, such as {@code alpine}, empty for an image without one
 * @param release      the distribution's release, such as {@code 3.24.1}
 * @param packages     every package the image holds
 */
record ImageInventory(String distribution, String release, List<ImagePackage> packages) {

    private static final Map<String, String> NAMES = Map.of("alpine", "Alpine", "debian", "Debian", "ubuntu", "Ubuntu");
    private static final Set<String> LANGUAGE_TYPES = Set.of(
        "pkg:npm/", "pkg:golang/", "pkg:pypi/", "pkg:gem/", "pkg:cargo/", "pkg:nuget/", "pkg:composer/", "pkg:hex/",
        "pkg:pub/"
    );
    private static final String LTS_MONTH = "04";
    private static final int LTS_EVERY = 2;

    ImageInventory {
        packages = List.copyOf(packages);
    }

    /**
     * Reads Trivy's document of one image.
     *
     * @param sbom the document
     * @return the inventory
     */
    static ImageInventory of(JsonNode sbom) {
        JsonNode system = sbom.path("components").valueStream()
            .filter(component -> "operating-system".equals(component.path("type").asText())).findFirst()
            .orElse(sbom.path("absent"));
        return new ImageInventory(
            system.path("name").asText().toLowerCase(Locale.ROOT), system.path("version").asText(),
            sbom.path("components").valueStream().map(ImagePackage::of).flatMap(Optional::stream).toList()
        );
    }

    /**
     * The distribution's name as a reader writes it.
     *
     * @return such as {@code Alpine}
     */
    String distributionName() {
        return NAMES.getOrDefault(this.distribution, this.distribution);
    }

    /**
     * The OSV ecosystem of the distribution's release: {@code Alpine:v3.24}, {@code Debian:13},
     * {@code Ubuntu:24.04:LTS}
     * for a long-term-support release and {@code Ubuntu:25.10} for another.
     *
     * @param dockerfile the Dockerfile the image was built from, which a refusal names
     * @return the ecosystem
     * @throws IllegalStateException when the image holds system packages of a distribution OSV is not read for here
     */
    String ecosystem(String dockerfile) {
        String[] levels = this.release.split("[.]", -1);
        return switch (this.distribution) {
            case "alpine" -> "Alpine:v" + levels[0] + (levels.length > 1 ? '.' + levels[1] : "");
            case "debian" -> "Debian:" + levels[0];
            case "ubuntu" -> ubuntu(levels);
            case null, default -> throw this.unsupported(dockerfile);
        };
    }

    /**
     * What OSV is asked about this image: each system package by its source package in the release's ecosystem, and
     * each language package of an ecosystem OSV reads by package URL.
     *
     * @param dockerfile the Dockerfile the image was built from, which a refusal names
     * @return the queries, each reported against the package's URL
     */
    List<OsvQuery> queries(String dockerfile) {
        List<ImagePackage> system = this.packages.stream().filter(ImagePackage::system).toList();
        Stream<OsvQuery> systemQueries = system.isEmpty() ? Stream.empty() : system.stream().map(
            entry -> OsvQuery.system(entry.purl(), this.ecosystem(dockerfile), entry.source(), entry.sourceVersion())
        );
        Stream<OsvQuery> languageQueries = this.packages.stream().filter(entry -> !entry.system())
            .filter(entry -> LANGUAGE_TYPES.stream().anyMatch(entry.purl()::startsWith))
            .map(entry -> OsvQuery.purl(entry.purl()));
        return Stream.concat(systemQueries, languageQueries).toList();
    }

    /**
     * The package the image holds under a package URL.
     *
     * @param purl the package URL a finding names
     * @return the package
     */
    Optional<ImagePackage> held(String purl) {
        return this.packages.stream().filter(entry -> entry.purl().equals(purl)).findFirst();
    }

    private IllegalStateException unsupported(String dockerfile) {
        String named = this.distribution.isEmpty() ? "an unnamed distribution" : this.distribution + ' ' + this.release;
        return new IllegalStateException(
            dockerfile + " builds an image on " + named + ", whose system packages Airness cannot look up; build it on"
                + " an Alpine, Debian or Ubuntu base"
        );
    }

    private static String ubuntu(String... levels) {
        String version = levels[0] + (levels.length > 1 ? '.' + levels[1] : "");
        boolean longTerm = levels.length > 1 && LTS_MONTH.equals(levels[1])
            && Integer.parseInt(levels[0]) % LTS_EVERY == 0;
        return "Ubuntu:" + version + (longTerm ? ":LTS" : "");
    }
}
