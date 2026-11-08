package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;

/**
 * What an application made from a starter is called: its Maven coordinates, the Java package its code moves to
 * and the name its pages and documents show.
 *
 * @param groupId     the Maven group of the application
 * @param artifactId  the Maven artifact of the application, which also names its archive, its database and the
 *                    user its container runs as
 * @param javaPackage the package the starter's code moves to
 * @param name        the name the pages, the README and the agent instructions show
 */
record StarterRequest(String groupId, String artifactId, String javaPackage, String name) {

    private static final Pattern GROUP = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z][a-z0-9_-]*)*");
    private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern ARTIFACT = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
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
     * @return the request, which may still hold a value {@link #problems()} refuses
     */
    static StarterRequest of(
        String groupId, String artifactId, Optional<String> javaPackage, Optional<String> name
    ) {
        String packaged = javaPackage.orElseGet(
            () -> groupId.replace(HYPHEN, "") + "." + artifactId.replace(HYPHEN, "")
        );
        return new StarterRequest(groupId, artifactId, packaged, name.orElseGet(() -> titled(artifactId)));
    }

    /**
     * Answers the request the properties of a command line make, which is where a goal that runs outside any
     * project reads its parameters: {@code airness.groupId} and {@code airness.artifactId}, and optionally
     * {@code airness.package} and {@code airness.name}. A property that is missing or blank reads as empty, which
     * {@link #problems()} refuses.
     *
     * @param properties the properties of the command line
     * @return the request, which may still hold a value {@link #problems()} refuses
     */
    static StarterRequest of(Properties properties) {
        return of(
            given(properties, "groupId").orElse(""), given(properties, "artifactId").orElse(""),
            given(properties, "package"), given(properties, "name")
        );
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
     * @return the refusals, none for a request that can be made
     */
    List<String> problems() {
        Optional<String> keyword = SourceVersion.isName(this.javaPackage) || !PACKAGE.matcher(this.javaPackage)
            .matches()
                ? Optional.empty()
                : Optional.of("airness.package is '" + this.javaPackage + "', but a part of it is a Java keyword");
        return Stream.of(
            refusal(GROUP, this.groupId, "airness.groupId", "lower-case words joined by dots, such as com.example"),
            refusal(
                ARTIFACT, this.artifactId, "airness.artifactId",
                "lower-case words joined by hyphens, such as task-tracker"
            ),
            refusal(
                PACKAGE, this.javaPackage, "airness.package",
                "a lower-case Java package name, such as com.example.tracker"
            ),
            keyword,
            refusal(NAME, this.name, "airness.name", "words of letters and digits separated by single spaces")
        ).flatMap(Optional::stream).toList();
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
