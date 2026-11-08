package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The logical lines of a Dockerfile, which are what {@link DockerfileReader} reads its instructions from.
 *
 * <p>Continued lines are joined first, so a {@code RUN} split across lines reads as the one command it is,
 * and every logical line keeps the line its instruction started on. Docker drops a comment line and a blank
 * line inside a continuation rather than ending the instruction there, and so does this. The body of a
 * here-document a {@code RUN}, {@code COPY} or {@code ADD} opens is part of that instruction, each of its
 * lines one more command.
 */
@UtilityClass
final class DockerfileLines {

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
    private static final String SEPARATOR = " ; ";
    private static final String COMMENT = "#";
    private static final String NEWLINE = "\n";

    /**
     * The logical lines: backslash-continued lines joined, and the here-documents an instruction opens
     * appended to it, each carrying the line it started on.
     *
     * @param text the Dockerfile
     * @return one entry per logical line
     */
    static List<Located<String>> of(String text) {
        List<String> physical = Arrays.asList(LINE_BREAK.split(text, -1));
        List<Located<String>> lines = new ArrayList<>();
        int next = 0;
        while (next < physical.size()) {
            Span instruction = heredocs(physical, joined(physical, next));
            lines.add(new Located<>(next + 1, instruction.text()));
            next = instruction.next();
        }
        return lines;
    }

    /**
     * Whether a line is a comment, which Docker reads as no instruction and no part of one.
     *
     * @param line a physical or logical line
     * @return whether its first character that is not blank opens a comment
     */
    static boolean comment(String line) {
        return line.strip().startsWith(COMMENT);
    }

    // Reads the line at index and every line a backslash continues it onto. A comment line ends nothing, not
    // even with a backslash of its own, and inside a continuation it and a blank line are skipped, which is
    // what Docker does with them.
    private static Span joined(List<String> physical, int index) {
        String logical = physical.get(index);
        String last = logical;
        int next = index + 1;
        while (next < physical.size() && continues(last)) {
            String line = physical.get(next);
            next++;
            if (!skipped(line)) {
                logical = CONTINUED.matcher(logical).replaceFirst(" ") + line;
                last = line;
            }
        }
        return new Span(logical, next);
    }

    // Appends the body of every here-document the instruction opens, in the order it opens them.
    private static Span heredocs(List<String> physical, Span instruction) {
        if (!HEREDOC_INSTRUCTION.matcher(instruction.text()).find()) {
            return instruction;
        }
        Span read = instruction;
        for (MatchResult marker : HEREDOC.matcher(instruction.text()).results().toList()) {
            read = heredoc(physical, read, marker);
        }
        return read;
    }

    // Appends the body of one here-document and steps past its terminator. A body is a script, so its own
    // continued lines join, a shell comment in it is no command, and each remaining line is a command of its
    // own.
    private static Span heredoc(List<String> physical, Span read, MatchResult marker) {
        boolean tabs = !marker.group("tabs").isEmpty();
        String word = marker.group("word");
        int end = read.next();
        while (end < physical.size() && !terminates(physical.get(end), word, tabs)) {
            end++;
        }
        String body = script(physical.subList(read.next(), end));
        return new Span(read.text() + body, Math.min(end + 1, physical.size()));
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

    private static boolean skipped(String line) {
        return comment(line) || line.isBlank();
    }

    /**
     * What has been read of one instruction so far.
     *
     * @param text the logical line read so far
     * @param next the index of the first physical line not yet read
     */
    private record Span(String text, int next) {
    }
}
