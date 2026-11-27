package eu.ciechanowiec.airness.governance;

import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds the repository's root pom to metadata of the project's own.
 *
 * <p>Maven hands a child its parent's description, address, inception year, licenses, developers and source
 * control, and the chain above a governed project ends in the Airness aggregator. A root pom that states none of
 * them claims, in its effective model, its published pom and its software bill of materials, that the project is
 * Airness's: under Airness's license, written by Airness's author, kept in Airness's repository. Maven cannot
 * unset an inherited license or developer, so the project states its own, and the root pom is where it does,
 * because every module that inherits the root receives them from there.
 *
 * <p>Presence is read from the raw model, since a value the pom inherited is exactly the one this refuses. Every
 * other rule judges a value the pom declares by what it resolves to in the effective model, so a value a property
 * spells is judged by its text, and a value the pom does not declare is left to the presence rule alone.
 */
public final class RootMetadataCheck {

    private static final String HTTPS = "https://";
    private static final String GIT = "scm:git:";
    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern PLACEHOLDER = Pattern.compile("@[A-Z][A-Z0-9_]*@");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private final ProjectMetadata declared;
    private final ProjectMetadata effective;
    private final MetadataBounds bounds;

    /**
     * Records the models and bounds the check reads.
     *
     * @param declared  the metadata the root pom itself declares
     * @param effective the metadata the root pom's effective model resolves to
     * @param bounds    what the metadata is judged against
     */
    public RootMetadataCheck(ProjectMetadata declared, ProjectMetadata effective, MetadataBounds bounds) {
        this.declared = declared;
        this.effective = effective;
        this.bounds = bounds;
    }

    /**
     * Reports every metadata value the root pom leaves to Airness or states wrongly.
     *
     * @return one group of findings per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings("Declare the project's own metadata in the root pom rather than inherit it", this.missing()),
            new Findings(
                "Name each license of the root pom by a current SPDX identifier or " + ProjectLicenses.PROPRIETARY,
                this.licenses()
            ),
            new Findings("Give the root pom one https address, and scm connections Git reads", this.addresses()),
            new Findings(
                "Date the root pom's inceptionYear no later than this year and the repository's first commit",
                this.inception()
            ),
            new Findings("Write the root pom's name and description as the project's own words", this.wording()),
            new Findings("Describe the project in the root pom rather than copy Airness's metadata", this.copied())
        );
    }

    private List<String> missing() {
        Map<String, Function<ProjectMetadata, String>> fields = fields();
        Stream<String> values = fields.entrySet().stream()
            .filter(field -> field.getValue().apply(this.declared).isBlank())
            .map(Map.Entry::getKey);
        Stream<String> lists = Stream.of(
            this.declared.ownership().licenses().isEmpty() ? "licenses" : "",
            this.declared.ownership().developers().isEmpty() ? "developers" : ""
        ).filter(name -> !name.isEmpty());
        return Stream.concat(values, lists).sorted().map(name -> "<" + name + "> is not declared").toList();
    }

    private List<String> licenses() {
        if (this.declared.ownership().licenses().isEmpty()) {
            return List.of();
        }
        return this.effective.ownership().licenses().stream()
            .flatMap(license -> ProjectLicenses.refusal(license, this.bounds.licenses()).stream())
            .toList();
    }

    private List<String> addresses() {
        Optional<String> url = this.value(ProjectMetadata::url);
        Optional<String> scmUrl = this.value(metadata -> metadata.scm().url());
        Stream<String> addresses = Stream.concat(
            url.map(value -> "url " + value).stream(), scmUrl.map(value -> "scm/url " + value).stream()
        );
        Stream<String> secure = addresses.filter(labelled -> !labelledValue(labelled).startsWith(HTTPS))
            .map(labelled -> labelled + " is not an https address");
        Stream<String> equal = url.flatMap(home -> scmUrl.filter(source -> !source.equals(home)))
            .map(source -> "scm/url " + source + " differs from url " + url.orElseThrow())
            .stream();
        Stream<String> declaredConnections = Stream.concat(
            this.value(metadata -> metadata.scm().connection()).map(value -> "scm/connection " + value).stream(),
            this.value(metadata -> metadata.scm().developerConnection())
                .map(value -> "scm/developerConnection " + value)
                .stream()
        );
        Stream<String> connections = declaredConnections.filter(labelled -> !gitConnection(labelled))
            .map(labelled -> labelled + " does not start with " + GIT);
        return Stream.of(secure, equal, connections).flatMap(stream -> stream).toList();
    }

    // The value of a labelled entry, with its scheme folded to lower case, since a scheme is read in any case.
    private static String labelledValue(String labelled) {
        return labelled.substring(labelled.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean gitConnection(String labelled) {
        return labelled.substring(labelled.indexOf(' ') + 1).startsWith(GIT);
    }

    private List<String> inception() {
        return this.value(ProjectMetadata::inceptionYear).stream().flatMap(this::inceptionProblem).toList();
    }

    private Stream<String> inceptionProblem(String value) {
        if (!YEAR.matcher(value).matches()) {
            return Stream.of("inceptionYear " + value + " is not a four-digit year");
        }
        Year year = Year.parse(value);
        Stream<String> future = year.isAfter(this.bounds.current())
            ? Stream.of("inceptionYear " + value + " is after this year, " + this.bounds.current())
            : Stream.empty();
        Stream<String> history = this.bounds.firstCommit()
            .filter(year::isAfter)
            .map(first -> "inceptionYear " + value + " is after the repository's first commit, made in " + first)
            .stream();
        return Stream.concat(future, history);
    }

    private List<String> wording() {
        Optional<String> name = this.value(ProjectMetadata::name);
        Optional<String> description = this.value(ProjectMetadata::description);
        Stream<String> named = Stream.concat(
            name.map(value -> "name " + value).stream(), description.map(value -> "description " + value).stream()
        );
        Stream<String> tokens = named.filter(labelled -> PLACEHOLDER.matcher(labelled).find())
            .map(labelled -> labelled + " holds a placeholder nobody replaced");
        Optional<String> foldedName = name.map(RootMetadataCheck::folded);
        Stream<String> repeated = description.filter(text -> foldedName.filter(folded(text)::equals).isPresent())
            .map(text -> "description " + text + " repeats the name")
            .stream();
        return Stream.concat(tokens, repeated).toList();
    }

    private List<String> copied() {
        Map<String, Function<ProjectMetadata, String>> fields = fields();
        Stream<String> values = fields.entrySet().stream()
            .filter(field -> !"inceptionYear".equals(field.getKey()))
            .filter(field -> this.value(field.getValue()).filter(this::harnessValue).isPresent())
            .map(field -> "<" + field.getKey() + "> repeats Airness's own");
        Stream<String> developers = this.declared.ownership().developers().isEmpty()
            ? Stream.empty()
            : this.effective.ownership().developers().stream()
                .filter(developer -> this.harnessDeveloper(folded(developer)))
                .map(developer -> "<developer> " + developer + " is Airness's own");
        return Stream.concat(values, developers).toList();
    }

    private boolean harnessValue(String value) {
        String folded = folded(value);
        return this.bounds.harness().stream()
            .flatMap(metadata -> fields().values().stream().map(field -> field.apply(metadata)))
            .filter(harness -> !harness.isBlank())
            .map(RootMetadataCheck::folded)
            .anyMatch(folded::equals);
    }

    private boolean harnessDeveloper(String developer) {
        return !developer.isEmpty() && this.bounds.harness().stream()
            .flatMap(metadata -> metadata.ownership().developers().stream())
            .map(RootMetadataCheck::folded)
            .anyMatch(developer::equals);
    }

    // A value the pom declares, read as the effective model resolves it.
    private Optional<String> value(Function<ProjectMetadata, String> field) {
        return Optional.of(field.apply(this.declared))
            .filter(declaredValue -> !declaredValue.isBlank())
            .map(_ -> field.apply(this.effective).strip());
    }

    private static Map<String, Function<ProjectMetadata, String>> fields() {
        return Map.of(
            "name", ProjectMetadata::name,
            "description", ProjectMetadata::description,
            "url", ProjectMetadata::url,
            "inceptionYear", ProjectMetadata::inceptionYear,
            "scm/url", metadata -> metadata.scm().url(),
            "scm/connection", metadata -> metadata.scm().connection(),
            "scm/developerConnection", metadata -> metadata.scm().developerConnection()
        );
    }

    private static String folded(String text) {
        return SPACE.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
