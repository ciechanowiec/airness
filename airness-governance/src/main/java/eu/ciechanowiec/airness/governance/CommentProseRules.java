package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the natural-language prose of a Java comment and reports the two punctuation marks this
 * repository does not spell that way.
 *
 * <p>The first is the semicolon. It joins two clauses that could each stand alone, so it always has a
 * shorter reading: a full stop, which lets each clause be read on its own, or a comma when the second
 * clause is genuinely subordinate. Comments are read under time pressure and out of order, and the
 * shorter reading is the one that survives that.
 *
 * <p>The second is the full stop inside a {@code @return} tag. That tag completes the sentence "returns
 * ...", so it is a fragment and takes no full stop, which is also why the semicolon ban does not reach
 * inside it: with no full stop available, a semicolon is the right way to hang a second clause off a
 * fragment. The two rules are a pair, and splitting a {@code @return} into sentences to satisfy the
 * first is what made them one.
 *
 * <p>Every comment form is read, in every tracked Java source, because a reader of a comment does not
 * care which syntax carried it. A {@code Justification} value is read alongside them, because it is a
 * comment that happens to be written as an annotation: the same sentence, addressed to the same reader,
 * and exempting it would let a suppression's reason say what a comment beside it may not. Literals are
 * tokenized out first rather than stripped by a lone comment
 * pattern, so a {@code "https://example.test";} cannot be mistaken for a line comment ending in a
 * semicolon. Four regions inside a comment are skipped, because punctuation in them is syntax: a
 * {@code <pre>} sample and an inline {@code @code} or {@code @literal} tag are code, an inline
 * {@code @link} may qualify a name, and an HTML entity ends in a semicolon by construction, that being
 * how an entity is written. The scan takes decoded text and never touches the filesystem, so it stays a
 * pure, unit-testable rule.
 *
 * <p>The em dash and en dash are not this rule's concern even though they raise the same question of
 * how a sentence is joined. They are banned outright, in every tracked file and every commit message,
 * by {@link TypographyRules}, so repeating them here would give one guideline two owners that could
 * later disagree.
 */
@UtilityClass
final class CommentProseRules {

    /*
     * One pass over the source, matching a literal or a comment, whichever starts first. Scanning both
     * with one alternation is what makes the comment boundaries right: a "//" inside a string is
     * consumed by the string alternative before the comment alternative can see it, and a quotation
     * mark inside a comment is consumed by the comment. A pattern that looked for comments alone would
     * read the tail of every URL as one.
     */
    private static final Pattern TOKEN = Pattern.compile(
        "(?s)\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\])*'|/\\*.*?\\*/|//[^\\n]*"
    );
    private static final Pattern JUSTIFICATION = Pattern.compile("@Justification\\s*\\(");
    private static final Pattern SAMPLE = Pattern.compile("(?s)<pre>.*?</pre>");
    private static final Pattern INLINE_TAG = Pattern.compile("(?s)\\{@(?:code|literal|link|linkplain)\\s[^}]*}");
    private static final Pattern ENTITY = Pattern.compile("&(?:#\\d+|#x[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]*);");
    private static final Pattern LEADING_ASTERISK = Pattern.compile("(?m)^\\s*[*]\\s?");
    private static final Pattern RETURN_TAG = Pattern.compile("(?s)@return\\b(.*?)(?=\\n\\s*@\\w|$)");
    private static final Pattern BLOCK_OPEN = Pattern.compile("^/\\*+");
    private static final Pattern BLOCK_CLOSE = Pattern.compile("\\*/$");
    private static final Pattern LINE_OPEN = Pattern.compile("^//+");
    private static final String COMMENT_START = "/";
    private static final String QUOTE = "\"";
    private static final String TEXT_BLOCK = "\"\"\"";
    private static final String SEMICOLON = ";";
    private static final String PERIOD = ".";
    private static final String SPACE = " ";
    private static final String NEWLINE = "\n";
    private static final char OPEN_PARENTHESIS = '(';
    private static final char CLOSE_PARENTHESIS = ')';

    /**
     * The comment lines of {@code source} that join clauses with a semicolon, in encounter order
     *
     * @param source the text of one Java source file
     * @return the offending lines, trimmed, without repeats
     */
    static List<String> semicolons(CharSequence source) {
        return Stream.concat(commentProse(source), justifications(source))
            .flatMap(CommentProseRules::offendingLines)
            .distinct()
            .toList();
    }

    /**
     * The {@code @return} tags of {@code source} that carry a full stop, in encounter order
     *
     * @param source the text of one Java source file
     * @return the offending tag bodies, trimmed, without repeats
     */
    static List<String> returnPeriods(CharSequence source) {
        return comments(source)
            .map(CommentProseRules::readable)
            .flatMap(comment -> RETURN_TAG.matcher(comment).results())
            .map(tag -> tag.group(1).strip())
            .filter(tag -> tag.contains(PERIOD))
            .distinct()
            .toList();
    }

    private static Stream<String> commentProse(CharSequence source) {
        return comments(source)
            .map(CommentProseRules::readable)
            .map(CommentProseRules::withoutReturnTags);
    }

    // Taken off the same token pass as the comments, not off the raw source, and that is what makes the
    // scan right rather than merely shorter. A text block is one token, so an annotation quoted inside a
    // fixture stays a fixture: reading the raw source would report this file's own examples. What marks a
    // literal as prose is the code before it, since only there can the annotation name appear at all. The
    // {@code @return} exemption does not reach here, an annotation value being a statement rather than a
    // tag completing a sentence.
    private static Stream<String> justifications(CharSequence source) {
        List<MatchResult> tokens = TOKEN.matcher(source).results().toList();
        List<Region> arguments = justificationArguments(source, tokens);
        return tokens.stream()
            .filter(CommentProseRules::isString)
            .filter(token -> arguments.stream().anyMatch(region -> region.contains(token)))
            .map(MatchResult::group)
            .map(CommentProseRules::literalBody)
            .map(CommentProseRules::prose);
    }

    private static boolean isString(MatchResult token) {
        return token.group().startsWith(QUOTE);
    }

    private static String literalBody(String literal) {
        int delimiter = delimiterLength(literal);
        return literal.substring(delimiter, literal.length() - delimiter);
    }

    private static int delimiterLength(String literal) {
        return (literal.startsWith(TEXT_BLOCK) ? TEXT_BLOCK : QUOTE).length();
    }

    private static List<Region> justificationArguments(
        CharSequence source, Iterable<MatchResult> tokens
    ) {
        String masked = masked(source, tokens);
        Collection<Region> regions = new ArrayList<>();
        JUSTIFICATION.matcher(masked).results()
            .mapToInt(match -> match.end() - 1)
            .forEach(open -> closing(masked, open).ifPresent(close -> regions.add(new Region(open, close))));
        return List.copyOf(regions);
    }

    private static String masked(CharSequence source, Iterable<MatchResult> tokens) {
        StringBuilder masked = new StringBuilder(source);
        tokens.forEach(
            token -> {
                for (int index = token.start(); index < token.end(); index++) {
                    masked.setCharAt(index, ' ');
                }
            }
        );
        return masked.toString();
    }

    private static OptionalInt closing(CharSequence source, int open) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            depth += parenthesisDepth(source.charAt(index));
            if (depth == 0) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private static int parenthesisDepth(char character) {
        return switch (character) {
            case OPEN_PARENTHESIS -> 1;
            case CLOSE_PARENTHESIS -> -1;
            default -> 0;
        };
    }

    private static Stream<String> comments(CharSequence source) {
        return TOKEN.matcher(source).results()
            .map(MatchResult::group)
            .filter(token -> token.startsWith(COMMENT_START));
    }

    // The delimiters go first, so what is reported is the sentence rather than the wrapper around it and
    // a line comment reads the same as the Javadoc line beside it. The skipped regions go next, and that
    // order is what makes a tag boundary findable: a leading asterisk hides the start of a line from
    // "the next tag begins here", and a return tag written inline, as a code sample of one, would
    // otherwise read as the tag itself and swallow every line after it.
    private static String readable(CharSequence comment) {
        String withoutOpen = LINE_OPEN.matcher(BLOCK_OPEN.matcher(comment).replaceFirst("")).replaceFirst("");
        String withoutDelimiters = BLOCK_CLOSE.matcher(withoutOpen).replaceFirst("");
        return prose(LEADING_ASTERISK.matcher(withoutDelimiters).replaceAll(""));
    }

    private static Stream<String> offendingLines(String comment) {
        return comment.lines()
            .map(String::strip)
            .filter(line -> line.contains(SEMICOLON));
    }

    // A @return is exempt from the semicolon ban, so it is removed before that scan rather than
    // special-cased inside it. Its own ban on the full stop is what keeps the exemption from being a
    // licence to write a sentence there. The replacement keeps the line break, so removing a tag cannot
    // run the line above it into the line below.
    private static String withoutReturnTags(CharSequence comment) {
        return RETURN_TAG.matcher(comment).replaceAll(NEWLINE);
    }

    // Every skipped region is replaced by a space rather than removed, so the words on either side of it
    // cannot be run together into something that reads like a different sentence.
    private static String prose(CharSequence comment) {
        String withoutSamples = SAMPLE.matcher(comment).replaceAll(SPACE);
        String withoutTags = INLINE_TAG.matcher(withoutSamples).replaceAll(SPACE);
        return ENTITY.matcher(withoutTags).replaceAll(SPACE);
    }

    private record Region(int open, int close) {

        boolean contains(MatchResult token) {
            return token.start() > this.open && token.end() <= this.close;
        }
    }
}
