package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The writing rules a commit message answers to beside the policy {@link CommitMessageRules} states: no
 * semicolon in the header or the body, and each paragraph of the body written on one source line.
 *
 * <p>A semicolon joins two sentences a reader has to pull apart again, so the writing guideline bans it in
 * every piece of prose an author writes, and a commit message is prose of that kind. A semicolon inside a
 * code span, or on an indented line, belongs to the code or the output quoted there, and passes.
 *
 * <p>A paragraph broken across lines is wrapped a second time by every viewer narrower than the break, which
 * leaves every second line holding a word or two, so a paragraph is one source line as it is in Markdown. A
 * line may still follow a non-blank one when it opens a list item, is indented, or belongs to the trailer
 * block that closes the message, since each of those is a line of its own by design rather than a paragraph
 * cut in two. Lines are split at any line terminator, so a message written with CRLF endings reads as one
 * written with LF.
 */
@UtilityClass
final class CommitProseRules {

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    /**
     * One code span: a maximal run of backticks, then the shortest text up to a run of exactly as many. A run
     * that never closes opens no span and stays literal, as CommonMark reads it.
     */
    private static final Pattern CODE_SPAN = Pattern.compile("(?<!`)(`+)(?!`).*?(?<!`)\\1(?!`)");
    private static final Pattern LIST_ITEM = Pattern.compile("^(?:[-*+]|\\d+[.)]) ");
    private static final Pattern TRAILER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]*: ");
    private static final String SEMICOLON = ";";

    static List<String> validate(CommitMessage message) {
        List<String> body = lines(message.body());
        return Stream.of(semicolonViolation(message.header(), body), paragraphViolation(body))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<String> semicolonViolation(String header, List<String> body) {
        return Stream.concat(Stream.of(header), body.stream())
            .filter(line -> !indented(line))
            .filter(line -> CODE_SPAN.matcher(line).replaceAll("").contains(SEMICOLON))
            .findFirst()
            .map(
                line -> "A semicolon outside a code span, where two sentences or a connecting word belong: "
                    + line.strip()
            );
    }

    private static Optional<String> paragraphViolation(List<String> body) {
        return IntStream.range(1, trailerBlockStart(body))
            .filter(index -> !body.get(index - 1).isBlank())
            .mapToObj(body::get)
            .filter(line -> !standsAlone(line))
            .findFirst()
            .map(line -> "A body paragraph runs onto a second source line, so join it into one: " + line.strip());
    }

    /**
     * Where the trailer block starts: the last paragraph of the body, when its first line is a
     * {@code Key: value} trailer and every line after it is one too or an indented continuation.
     *
     * @return the index of the block's first line, or the size of the body when it closes with no trailer block
     */
    private static int trailerBlockStart(List<String> body) {
        int end = runStart(body, body.size(), String::isBlank);
        int start = runStart(body, end, line -> !line.isBlank());
        List<String> block = body.subList(start, end);
        boolean trailers = !block.isEmpty() && TRAILER.matcher(block.getFirst()).find()
            && block.stream().allMatch(line -> TRAILER.matcher(line).find() || indented(line));
        return trailers ? start : body.size();
    }

    /**
     * Where the run of lines that ends just before {@code end} and that all match {@code member} starts.
     *
     * @return the index of the run's first line, which is {@code end} itself when the line before it does not match
     */
    private static int runStart(List<String> body, int end, Predicate<String> member) {
        int start = end;
        while (start > 0 && member.test(body.get(start - 1))) {
            start--;
        }
        return start;
    }

    /**
     * Whether a line may follow a non-blank one: it is blank itself, or it opens a list item, or it is indented.
     */
    private static boolean standsAlone(String line) {
        return line.isBlank() || indented(line) || LIST_ITEM.matcher(line).find();
    }

    private static List<String> lines(CharSequence text) {
        return List.of(LINE_BREAK.split(text, -1));
    }

    private static boolean indented(String line) {
        return line.startsWith(" ") || line.startsWith("\t");
    }
}
