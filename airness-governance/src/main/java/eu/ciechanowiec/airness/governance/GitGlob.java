package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Translates the glob of a {@code .gitignore} pattern into the regular expression git matches it as.
 *
 * <p>Git matches with path semantics: {@code *} and {@code ?} never cross a separator, and neither does a
 * bracket expression. Two asterisks that make up a whole segment cross any number of directories: a leading
 * {@code **}{@code /} matches in every directory, a trailing {@code /**} matches everything inside, and a
 * {@code /**}{@code /} in the middle matches zero or more directories. Any other run of asterisks is one
 * asterisk. A backslash makes the character after it literal.
 *
 * <p>A bracket expression that names a POSIX class, holds a backslash, is left open, or spells a range Java
 * refuses is a form this reader does not model. Its translation is empty, so that a caller judging what a
 * pattern can match treats it as matching anything rather than guessing.
 */
@UtilityClass
final class GitGlob {

    private static final int UNSUPPORTED = -1;
    private static final char SEPARATOR = '/';
    private static final char ASTERISK = '*';
    private static final int WHOLE_SEGMENT_RUN = 2;
    private static final String ANY_IN_SEGMENT = "[^/]*";
    private static final String ANY_DIRECTORIES = "(?:.*/)?";
    private static final String ANYTHING = ".*";
    private static final char RANGE = '-';
    private static final List<String> UNMODELLED_MEMBERS = List.of("[:", "\\");

    /**
     * The regular expression a glob matches as.
     *
     * @param glob the glob, without the negation, the leading separator or the trailing separator of its line
     * @return the expression, matched against a whole path, or empty where the glob uses a form not modelled
     */
    static Optional<Pattern> regex(String glob) {
        StringBuilder regex = new StringBuilder();
        int index = 0;
        while (index >= 0 && index < glob.length()) {
            index = translate(glob, index, regex);
        }
        return index < 0 ? Optional.empty() : compiled(regex.toString());
    }

    /**
     * Whether a glob holds no wildcard and no escape, so that it names exactly the text it spells.
     *
     * @param glob the glob
     * @return whether the glob is literal
     */
    static boolean literal(String glob) {
        return glob.chars().noneMatch(character -> "*?[\\".indexOf(character) >= 0);
    }

    private static Optional<Pattern> compiled(String regex) {
        try {
            return Optional.of(Pattern.compile(regex));
        } catch (PatternSyntaxException _) {
            return Optional.empty();
        }
    }

    // Appends the translation of the construct that starts at the index and returns the index after it.
    private static int translate(String glob, int index, StringBuilder regex) {
        char character = glob.charAt(index);
        return switch (character) {
            case ASTERISK -> asterisks(glob, index, regex);
            case '?' -> append(regex, "[^/]", index + 1);
            case '[' -> bracket(glob, index, regex);
            case '\\' -> escaped(glob, index, regex);
            default -> append(regex, Pattern.quote(String.valueOf(character)), index + 1);
        };
    }

    private static int append(StringBuilder regex, String translation, int next) {
        regex.append(translation);
        return next;
    }

    private static int asterisks(String glob, int start, StringBuilder regex) {
        int end = start;
        while (end < glob.length() && glob.charAt(end) == ASTERISK) {
            end++;
        }
        if (!wholeSegment(glob, start, end)) {
            return append(regex, ANY_IN_SEGMENT, end);
        }
        return end == glob.length() ? append(regex, ANYTHING, end) : append(regex, ANY_DIRECTORIES, end + 1);
    }

    // Whether the run of asterisks is exactly two and makes up a whole segment of the glob.
    private static boolean wholeSegment(String glob, int start, int end) {
        boolean opensSegment = start == 0 || glob.charAt(start - 1) == SEPARATOR;
        boolean closesSegment = end == glob.length() || glob.charAt(end) == SEPARATOR;
        return opensSegment && closesSegment && end - start == WHOLE_SEGMENT_RUN;
    }

    private static int bracket(String glob, int start, StringBuilder regex) {
        int open = start + 1;
        boolean negated = glob.startsWith("!", open) || glob.startsWith("^", open);
        int first = negated ? open + 1 : open;
        int close = closingBracket(glob, first);
        String negation = negated ? "^" : "";
        return members(glob, first, close)
            .map(members -> append(regex, "(?:(?!/)[" + negation + classMembers(members) + "])", close + 1))
            .orElse(UNSUPPORTED);
    }

    // A closing bracket right after the opening one is a member, so the search for the end starts past it.
    private static int closingBracket(String glob, int first) {
        return first < glob.length() ? glob.indexOf(']', first + 1) : UNSUPPORTED;
    }

    // The members of a closed bracket expression, when they hold no form this reader does not model.
    private static Optional<String> members(String glob, int first, int close) {
        return Optional.of(close)
            .filter(end -> end >= 0)
            .map(end -> glob.substring(first, end))
            .filter(members -> UNMODELLED_MEMBERS.stream().noneMatch(members::contains));
    }

    // Every member literal except an unescaped hyphen, which keeps spelling a range as it does for git.
    private static String classMembers(String members) {
        return members.chars().mapToObj(GitGlob::classMember).collect(Collectors.joining());
    }

    private static String classMember(int member) {
        String literal = Character.toString(member);
        return member == RANGE || Character.isLetterOrDigit(member) ? literal : "\\" + literal;
    }

    private static int escaped(String glob, int index, StringBuilder regex) {
        int literal = Math.min(index + 1, glob.length() - 1);
        return append(regex, Pattern.quote(String.valueOf(glob.charAt(literal))), literal + 1);
    }
}
