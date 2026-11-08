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
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the two things a Dockerfile names that the blocklist judges: the images its {@code FROM} lines
 * or external {@code COPY --from} references pull, and the system packages its {@code RUN} lines install.
 *
 * <p>A line reader over a known syntax, in the manner of {@link SpringConfiguration}: what has to be
 * recognised is one keyword and the tokens after it. Continued lines are joined first, so a
 * {@code RUN} split across lines reads as the one command it is, and every value keeps the line its
 * instruction started on. Docker drops a comment line and a blank line inside a continuation rather than
 * ending the instruction there, and so does this. The body of a here-document a {@code RUN}, {@code COPY}
 * or {@code ADD} opens is part of that instruction, each of its lines one more command.
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
    private static final Pattern COPY = Pattern.compile("(?i)^[ \t]*COPY[ \t]+(?<rest>\\S.*)$");
    private static final Pattern STAGE_INDEX = Pattern.compile("[0-9]+");
    private static final String REST = "rest";
    private static final Pattern ARGUMENT = Pattern.compile(
        "(?i)^[ \\t]*ARG[ \\t]+(?<name>[A-Za-z_]\\w*)=(?<value>\\S+)"
    );
    private static final Pattern RUN = Pattern.compile("(?i)^[ \\t]*RUN[ \\t]+(?<rest>\\S.*)$");
    // The install verbs of the package managers a base image ships, after any options the manager takes
    // before its verb, as in apt-get -y install or apk --no-cache add. Language installs such as pip and
    // npm are not system packages and are judged nowhere here.
    private static final Pattern INSTALL = Pattern.compile(
        "\\b(?:apt-get|apt|apk|dnf|microdnf|yum|zypper)(?:[ \\t]+-\\S+)*[ \\t]+(?:install|add)\\b"
    );
    private static final String SEPARATOR = " ; ";
    private static final Pattern COMMAND_SEPARATOR = Pattern.compile("&&|\\|\\||;|\\|");
    // What ends a package name in an install token: apt's version and suite, and apk's version.
    private static final Pattern PACKAGE_END = Pattern.compile("[=/~]");
    private static final Pattern BLANK = Pattern.compile("[ \\t]+");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");
    // A line a backslash ends continues the instruction onto the next, and every other one ends it.
    private static final Pattern CONTINUED = Pattern.compile("\\\\[ \\t]*$");
    private static final Pattern CONTINUATION = Pattern.compile("\\\\\\r?\\n");
    private static final Pattern HEREDOC_INSTRUCTION = Pattern.compile("(?i)^[ \\t]*(?:RUN|COPY|ADD)[ \\t]");
    // A shell word opening a here-document: <<EOT, <<-EOT, <<"EOT" or <<'EOT', after an optional descriptor.
    private static final Pattern HEREDOC = Pattern.compile(
        "(?<![^ \\t])[0-9]*<<(?<tabs>-?)(?<quote>[\"']?)(?<word>[A-Za-z_][\\w.-]*)\\k<quote>"
    );
    private static final Pattern LEADING_TABS = Pattern.compile("^\\t+");
    private static final Pattern VARIABLE = Pattern.compile(
        "\\$(?:\\{(?<braced>[A-Za-z_]\\w*)}|(?<bare>[A-Za-z_]\\w*))"
    );
    private static final String COMMENT = "#";
    private static final String SCRATCH = "scratch";
    private static final String STAGE = "as";
    private static final String OPTION = "-";
    private static final String NEWLINE = "\n";
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
        List<Located<String>> lines = contentLines(text);
        Set<String> copyStages = stageNames(lines);
        for (Located<String> line : lines) {
            readArgument(line.value(), defaults);
            readFrom(line, defaults, stages).ifPresent(images::add);
            readCopy(line, copyStages).ifPresent(images::add);
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
     * The logical lines: backslash-continued lines joined, and the here-documents an instruction opens
     * appended to it, each carrying the line it started on.
     *
     * @param text the Dockerfile
     * @return one entry per logical line
     */
    static List<Located<String>> logicalLines(String text) {
        List<String> physical = Arrays.asList(LINE_BREAK.split(text, -1));
        List<Located<String>> lines = new ArrayList<>();
        int next = 0;
        while (next < physical.size()) {
            StringBuilder logical = new StringBuilder();
            int start = next;
            next = heredocs(physical, joined(physical, next, logical), logical);
            lines.add(new Located<>(start + 1, logical.toString()));
        }
        return lines;
    }

    // Appends the line at index and every line a backslash continues it onto, and returns the index after
    // the last line read. A comment line ends nothing, not even with a backslash of its own, and inside a
    // continuation it and a blank line are skipped, which is what Docker does with them.
    private static int joined(List<String> physical, int index, StringBuilder logical) {
        String last = physical.get(index);
        logical.append(last);
        int next = index + 1;
        while (next < physical.size() && continues(last)) {
            String line = physical.get(next);
            next++;
            if (!comment(line) && !line.isBlank()) {
                String joined = CONTINUED.matcher(logical).replaceFirst(" ") + line;
                logical.setLength(0);
                logical.append(joined);
                last = line;
            }
        }
        return next;
    }

    // Appends the body of every here-document the instruction opens, in the order it opens them, and
    // returns the index after the last terminator read. A body is a script, so its own continued lines
    // join, a shell comment in it is no command, and each remaining line is a command of its own.
    private static int heredocs(List<String> physical, int index, StringBuilder logical) {
        if (!HEREDOC_INSTRUCTION.matcher(logical).find()) {
            return index;
        }
        int next = index;
        Matcher marker = HEREDOC.matcher(logical.toString());
        while (marker.find()) {
            boolean tabs = !marker.group("tabs").isEmpty();
            String word = marker.group("word");
            List<String> body = new ArrayList<>();
            while (next < physical.size() && !terminates(physical.get(next), word, tabs)) {
                body.add(physical.get(next));
                next++;
            }
            next = Math.min(next + 1, physical.size());
            logical.append(script(body));
        }
        return next;
    }

    private static boolean terminates(String line, String word, boolean tabs) {
        return word.equals(tabs ? LEADING_TABS.matcher(line).replaceFirst("") : line);
    }

    private static String script(List<String> body) {
        String joined = CONTINUATION.matcher(String.join(NEWLINE, body)).replaceAll(" ");
        return Arrays.stream(joined.split(NEWLINE))
            .filter(line -> !comment(line))
            .map(line -> SEPARATOR + line)
            .collect(Collectors.joining());
    }

    private static boolean continues(String line) {
        return !comment(line) && CONTINUED.matcher(line).find();
    }

    private static boolean comment(String line) {
        return line.strip().startsWith(COMMENT);
    }

    private static List<Located<String>> contentLines(String text) {
        return logicalLines(text).stream()
            .filter(line -> !comment(line.value()))
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
            ? image(from.group(REST), defaults, stages).map(image -> new Located<>(line.line(), image))
            : Optional.empty();
    }

    private static Optional<String> image(String rest, Map<String, String> defaults, Set<String> stages) {
        List<String> tokens = fromTokens(rest);
        Optional<String> reference = tokens.stream().findFirst().map(first -> substitute(first, defaults));
        stageName(tokens).ifPresent(stages::add);
        return reference
            .filter(image -> !SCRATCH.equalsIgnoreCase(image))
            .filter(image -> !stages.contains(image.toLowerCase(Locale.ROOT)));
    }

    private static List<String> fromTokens(String rest) {
        return Arrays.stream(BLANK.split(rest.strip())).filter(token -> !token.startsWith(OPTION)).toList();
    }

    private static Optional<String> stageName(List<String> tokens) {
        return Optional.of(tokens).filter(values -> values.size() > STAGE_NAME)
            .filter(values -> STAGE.equalsIgnoreCase(values.get(1)))
            .map(values -> values.get(STAGE_NAME).toLowerCase(Locale.ROOT));
    }

    private static Set<String> stageNames(List<Located<String>> lines) {
        return lines.stream().map(line -> FROM.matcher(line.value())).filter(Matcher::find)
            .flatMap(from -> stageName(fromTokens(from.group(REST))).stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<Located<String>> readCopy(Located<String> line, Set<String> stages) {
        Matcher copy = COPY.matcher(line.value());
        return copy.find() ? copyImage(copy.group(REST), stages)
            .map(image -> new Located<>(line.line(), image)) : Optional.empty();
    }

    private static Optional<String> copyImage(String rest, Set<String> stages) {
        return DockerCopyOptions.source(rest)
            .filter(reference -> !STAGE_INDEX.matcher(reference).matches())
            .filter(reference -> !stages.contains(reference.toLowerCase(Locale.ROOT)));
    }

    // Both spellings of a variable are substituted, each by its whole name, so $REDIS is not read inside
    // $REDIS_VERSION. A name no ARG gives a default stays as written, and what a default of the form
    // ${NAME:-value} leaves is resolved afterwards, so a FROM written either way reads as the image it pulls.
    private static String substitute(String reference, Map<String, String> defaults) {
        return VariableDefaults.applied(
            VARIABLE.matcher(reference).replaceAll(variable -> Matcher.quoteReplacement(value(variable, defaults)))
        );
    }

    private static String value(MatchResult variable, Map<String, String> defaults) {
        String name = Optional.ofNullable(variable.group("braced")).orElseGet(() -> variable.group("bare"));
        return defaults.getOrDefault(name, variable.group());
    }

    private static Stream<String> packagesIn(Located<String> line) {
        Matcher run = RUN.matcher(line.value());
        return run.find()
            ? Arrays.stream(COMMAND_SEPARATOR.split(run.group(REST))).flatMap(DockerfileReader::packagesInCommand)
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
