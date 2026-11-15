package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the credentials a Dockerfile writes into the image it builds.
 *
 * <p>A build argument's value stays in the image's build history and an environment variable's in its
 * configuration, so whoever pulls the image reads both. A credential a build needs reaches it as a secret mount,
 * {@code RUN --mount=type=secret}, and one the container needs reaches it at run time.
 */
@UtilityClass
final class DockerfileCredentials {

    private static final Pattern DECLARATION = Pattern.compile(
        "^[ \\t]*(?<instruction>ARG|ENV)[ \\t]+(?<rest>\\S.*)$", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BLANKS = Pattern.compile("[ \\t]+");
    // A NAME=value pair of the modern ENV form, or the bare name of an ARG, at the start of a word.
    private static final Pattern PAIR_NAME = Pattern.compile("(?:^|[ \\t])(?<name>[A-Za-z_]\\w*)=");
    private static final String ASSIGNMENT = "=";
    private static final String ENV = "ENV";

    /**
     * Every build argument and environment variable whose name marks a credential.
     *
     * @param dockerfile the Dockerfile
     * @return the instruction and the name, at the line the instruction starts on, in file order
     */
    static List<Located<String>> declared(String dockerfile) {
        return DockerfileLines.of(dockerfile).stream()
            .filter(line -> !DockerfileLines.comment(line.value()))
            .flatMap(DockerfileCredentials::credentialsOf)
            .toList();
    }

    private static Stream<Located<String>> credentialsOf(Located<String> line) {
        Matcher declaration = DECLARATION.matcher(line.value());
        if (!declaration.matches()) {
            return Stream.empty();
        }
        String instruction = declaration.group("instruction").toUpperCase(Locale.ROOT);
        String rest = declaration.group("rest");
        return names(instruction, rest).filter(EnvironmentRules::privateCredential)
            .map(name -> new Located<>(line.line(), instruction + ' ' + name));
    }

    // ARG declares one name or several, each with an optional default. ENV assigns NAME=value pairs, or, in the
    // legacy form without '=' in its first word, one name and the rest of the line as its value.
    private static Stream<String> names(String instruction, String rest) {
        if (!ENV.equals(instruction)) {
            return Stream.of(BLANKS.split(rest.strip())).map(word -> word.split(ASSIGNMENT, 2)[0]);
        }
        String first = BLANKS.split(rest.strip(), 2)[0];
        return first.contains(ASSIGNMENT) ? PAIR_NAME.matcher(rest).results().map(pair -> pair.group("name"))
            : Stream.of(first);
    }
}
