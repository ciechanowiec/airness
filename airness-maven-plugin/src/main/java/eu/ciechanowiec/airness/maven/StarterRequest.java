package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.LicenseCatalogue;
import eu.ciechanowiec.airness.governance.MavenNames;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;

/**
 * What an application made from a starter is called and who it belongs to: its Maven coordinates, the Java package
 * its code moves to, the name its pages and documents show, and the owner its pom names.
 *
 * @param groupId     the Maven group of the application
 * @param artifactId  the Maven artifact of the application, which also names its archive, its database and the
 *                    user its container runs as
 * @param javaPackage the package the starter's code moves to
 * @param name        the name the pages, the README and the agent instructions show
 * @param owner       who the application belongs to and on what terms
 */
record StarterRequest(String groupId, String artifactId, String javaPackage, String name, StarterOwner owner) {

    private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    // Letters and digits only, because the name is written into HTML, XML, YAML and properties files unescaped.
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}]+( [\\p{L}\\p{N}]+)*");
    private static final String HYPHEN = "-";
    private static final String PREFIX = "airness.";

    /**
     * Answers the request for the given coordinates. Without a package of its own the code moves to the group
     * followed by the artifact, and without a name of its own the application is called by the words of its
     * artifact, each capitalised.
     *
     * @param groupId     the Maven group of the application
     * @param artifactId  the Maven artifact of the application
     * @param javaPackage the package the code moves to, if one is given
     * @param name        the name the application shows, if one is given
     * @return the request, owned by nobody until {@link #owned(StarterOwner)} names an owner
     */
    static StarterRequest of(
        String groupId, String artifactId, Optional<String> javaPackage, Optional<String> name
    ) {
        String packaged = javaPackage.orElseGet(
            () -> groupId.replace(HYPHEN, "") + "." + artifactId.replace(HYPHEN, "")
        );
        return new StarterRequest(
            groupId, artifactId, packaged, name.orElseGet(() -> titled(artifactId)),
            new StarterOwner("", "", "", Year.of(1))
        );
    }

    /**
     * Answers the request the properties of a command line make, which is where a goal that runs outside any
     * project reads its parameters: {@code airness.groupId}, {@code airness.artifactId} and the owner's
     * {@code airness.organization} and {@code airness.url}, and optionally {@code airness.package},
     * {@code airness.name} and {@code airness.license}. A property that is missing or blank reads as empty, which
     * {@link #problems(LicenseCatalogue)} refuses.
     *
     * @param properties the properties of the command line
     * @param year       the year the application is made
     * @return the request, which may still hold a value {@link #problems(LicenseCatalogue)} refuses
     */
    static StarterRequest of(Properties properties, Year year) {
        return of(
            given(properties, "groupId").orElse(""), given(properties, "artifactId").orElse(""),
            given(properties, "package"), given(properties, "name")
        ).owned(StarterOwner.of(properties, year));
    }

    /**
     * Answers the same request under an owner.
     *
     * @param owner who the application belongs to and on what terms
     * @return the request with that owner
     */
    StarterRequest owned(StarterOwner owner) {
        return new StarterRequest(this.groupId, this.artifactId, this.javaPackage, this.name, owner);
    }

    /**
     * Answers a property of the command line that names something, when it is set and not blank.
     *
     * @param properties the properties of the command line
     * @param parameter  the name of the parameter, without the airness prefix
     * @return the stripped value
     */
    static Optional<String> given(Properties properties, String parameter) {
        return Optional.ofNullable(properties.getProperty(PREFIX + parameter))
            .map(String::strip)
            .filter(value -> !value.isEmpty());
    }

    /**
     * Answers why the request cannot be made, one sentence for each value it refuses.
     *
     * @param licenses the SPDX license list the owner's license is named from
     * @return the refusals, none for a request that can be made
     */
    List<String> problems(LicenseCatalogue licenses) {
        Optional<String> keyword = SourceVersion.isName(this.javaPackage) || !PACKAGE.matcher(this.javaPackage)
            .matches()
                ? Optional.empty()
                : Optional.of("airness.package is '" + this.javaPackage + "', but a part of it is a Java keyword");
        return Stream.concat(
            Stream.of(
                this.groupProblem(), this.artifactProblem(),
                refusal(
                    PACKAGE, this.javaPackage, "airness.package",
                    "a lower-case Java package name, such as org.sample.tracker"
                ),
                keyword,
                refusal(NAME, this.name, "airness.name", "words of letters and digits separated by single spaces")
            ).flatMap(Optional::stream),
            this.owner.problems(licenses).stream()
        ).toList();
    }

    private Optional<String> groupProblem() {
        return Optional.of(this.groupId)
            .filter(group -> !MavenNames.reverseDomain(group) || MavenNames.exampleDomain(group))
            .map(
                group -> "airness.groupId is '" + group + "', but it must be a reverse domain the owner holds, of"
                    + " lower-case words joined by dots, such as org.sample"
            );
    }

    private Optional<String> artifactProblem() {
        return Optional.of(this.artifactId)
            .filter(artifact -> !MavenNames.kebab(artifact))
            .map(
                artifact -> "airness.artifactId is '" + artifact + "', but it must be lower-case words joined by"
                    + " hyphens, such as task-tracker"
            );
    }

    /**
     * Answers the directory path the package's code lives under.
     *
     * @return the package with each dot read as a directory separator
     */
    String packagePath() {
        return this.javaPackage.replace('.', '/');
    }

    private static Optional<String> refusal(Pattern pattern, String value, String property, String expected) {
        return pattern.matcher(value).matches()
            ? Optional.empty()
            : Optional.of(property + " is '" + value + "', but it must be " + expected);
    }

    private static String titled(String artifactId) {
        return Stream.of(artifactId.split(HYPHEN))
            .filter(word -> !word.isEmpty())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    }
}
