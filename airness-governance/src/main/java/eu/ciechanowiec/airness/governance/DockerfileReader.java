package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the two things a Dockerfile names that the blocklist judges: the images its {@code FROM} lines
 * pull, and the system packages its {@code RUN} lines install.
 *
 * <p>A line reader over a known syntax, in the manner of {@link SpringConfiguration}: what has to be
 * recognised is one keyword and the tokens after it. Continued lines are joined first, so a
 * {@code RUN} split across lines reads as the one command it is, and every value keeps the line its
 * instruction started on.
 *
 * <p>{@code FROM node:${NODE_VERSION}} is the common way to state a version once, and the {@code ARG}
 * that gives it a default is the only place the value lives, so those defaults are substituted. An
 * {@code ARG} with no default leaves the variable in place, and the rule reports the reference as one
 * it cannot judge. A stage name an earlier {@code FROM} declared is not an image, and neither is
 * {@code scratch}, which no registry serves.
 */
@UtilityClass
final class DockerfileReader {

    private static final Pattern FROM = Pattern.compile("(?i)^[ \\t]*FROM[ \\t]+(?<rest>\\S.*)$");
    private static final Pattern ARGUMENT = Pattern.compile(
        "(?i)^[ \\t]*ARG[ \\t]+(?<name>[A-Za-z_]\\w*)=(?<value>\\S+)"
    );
    private static final Pattern RUN = Pattern.compile("(?i)^[ \\t]*RUN[ \\t]+(?<rest>\\S.*)$");
    // The install verbs of the package managers a base image ships. Language installs such as pip and
    // npm are not system packages and are judged nowhere here.
    private static final Pattern INSTALL = Pattern.compile(
        "\\b(?:apt-get|apt|apk|dnf|microdnf|yum|zypper)[ \\t]+(?:install|add)\\b"
    );
    private static final Pattern COMMAND_SEPARATOR = Pattern.compile("&&|\\|\\||;|\\|");
    // What ends a package name in an install token: apt's version and suite, and apk's version.
    private static final Pattern PACKAGE_END = Pattern.compile("[=/~]");
    private static final Pattern BLANK = Pattern.compile("[ \\t]+");
    // A line break a backslash precedes continues the instruction, and every other one ends it.
    private static final Pattern LOGICAL_BREAK = Pattern.compile("(?<!\\\\)\\r?\\n");
    private static final Pattern CONTINUATION = Pattern.compile("\\\\\\r?\\n");
    private static final String COMMENT = "#";
    private static final String SCRATCH = "scratch";
    private static final String STAGE = "as";
    private static final String OPTION = "-";
    private static final String VARIABLE = "$";
    private static final char OPEN = '{';
    private static final char CLOSE = '}';
    private static final char NEWLINE = '\n';
    private static final int STAGE_NAME = 2;

    /**
     * The images a Dockerfile pulls: every {@code FROM} that names a registry image rather than an
     * earlier stage, with {@code ARG} defaults substituted.
     *
     * @param text the Dockerfile
     * @return the references with the line each instruction starts on, in file order
     */
    static List<Located<String>> images(String text) {
        Map<String, String> defaults = new HashMap<>();
        Set<String> stages = new HashSet<>();
        List<Located<String>> images = new ArrayList<>();
        for (Located<String> line : contentLines(text)) {
            readArgument(line.value(), defaults);
            readFrom(line, defaults, stages).ifPresent(images::add);
        }
        return images;
    }

    /**
     * The system packages a Dockerfile installs through the base image's package manager.
     *
     * @param text the Dockerfile
     * @return every package name after an install verb, with its line, in file order
     */
    static List<Located<String>> installedPackages(String text) {
        return contentLines(text).stream()
            .flatMap(line -> packagesIn(line).map(name -> new Located<>(line.line(), name)))
            .toList();
    }

    /**
     * The logical lines: backslash-continued lines joined, each carrying the line it started on.
     *
     * @param text the Dockerfile
     * @return one entry per logical line
     */
    static List<Located<String>> logicalLines(String text) {
        List<Located<String>> lines = new ArrayList<>();
        int number = 1;
        for (String logical : LOGICAL_BREAK.split(text, -1)) {
            lines.add(new Located<>(number, CONTINUATION.matcher(logical).replaceAll(" ")));
            number += (int) logical.chars().filter(character -> character == NEWLINE).count() + 1;
        }
        return lines;
    }

    private static List<Located<String>> contentLines(String text) {
        return logicalLines(text).stream()
            .filter(line -> !line.value().strip().startsWith(COMMENT))
            .toList();
    }

    private static void readArgument(String line, Map<String, String> defaults) {
        Matcher argument = ARGUMENT.matcher(line);
        if (argument.find()) {
            defaults.put(argument.group("name"), Quotes.stripped(argument.group("value")));
        }
    }

    private static Optional<Located<String>> readFrom(
        Located<String> line, Map<String, String> defaults, Set<String> stages
    ) {
        Matcher from = FROM.matcher(line.value());
        return from.find()
            ? image(from.group("rest"), defaults, stages).map(image -> new Located<>(line.line(), image))
            : Optional.empty();
    }

    private static Optional<String> image(String rest, Map<String, String> defaults, Set<String> stages) {
        List<String> tokens = Arrays.stream(BLANK.split(rest.strip()))
            .filter(token -> !token.startsWith(OPTION))
            .toList();
        Optional<String> reference = tokens.stream().findFirst().map(first -> substitute(first, defaults));
        if (tokens.size() > STAGE_NAME && STAGE.equalsIgnoreCase(tokens.get(1))) {
            stages.add(tokens.get(STAGE_NAME).toLowerCase(Locale.ROOT));
        }
        return reference
            .filter(image -> !SCRATCH.equalsIgnoreCase(image))
            .filter(image -> !stages.contains(image.toLowerCase(Locale.ROOT)));
    }

    // Both spellings of a variable are substituted, and what a default of the form ${NAME:-value} leaves
    // is resolved afterwards, so a FROM written either way reads as the image it pulls.
    private static String substitute(String reference, Map<String, String> defaults) {
        String resolved = reference;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            resolved = resolved
                .replace(VARIABLE + OPEN + entry.getKey() + CLOSE, entry.getValue())
                .replace(VARIABLE + entry.getKey(), entry.getValue());
        }
        return VariableDefaults.applied(resolved);
    }

    private static Stream<String> packagesIn(Located<String> line) {
        Matcher run = RUN.matcher(line.value());
        return run.find()
            ? Arrays.stream(COMMAND_SEPARATOR.split(run.group("rest"))).flatMap(DockerfileReader::packagesInCommand)
            : Stream.empty();
    }

    private static Stream<String> packagesInCommand(String command) {
        Matcher verb = INSTALL.matcher(command);
        return verb.find()
            ? Arrays.stream(BLANK.split(command.substring(verb.end()).strip()))
                .filter(token -> !token.isEmpty())
                .filter(token -> !token.startsWith(OPTION))
                .map(token -> PACKAGE_END.split(token, 2)[0])
            : Stream.empty();
    }
}
