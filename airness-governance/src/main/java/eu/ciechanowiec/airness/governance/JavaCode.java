package eu.ciechanowiec.airness.governance;

import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Tells the code of a Java source apart from the text that merely sits inside it.
 *
 * <p>A rule that reads structure has to read code alone. A fixture in this repository quotes whole Java
 * sources inside a text block, so a scan that took the file at face value would find the annotations,
 * the braces and the calls of the quoted source and report them against the file doing the quoting. The
 * same holds for a brace inside a string literal, which closes nothing and would end a method body
 * early for anything counting braces.
 *
 * <p>Both blanking methods replace what they remove with spaces of the same width and leave every line
 * break in place, so an offset into the result is the same offset into the source, and a line number
 * derived from one is the line number of the other. That is what lets one method find a region and
 * another read it.
 *
 * <p>The two differ in what counts as text. {@link #blanked} removes every literal, which is what a
 * reader of structure wants. {@link #withoutComments} keeps a literal that fits on one line, which is
 * what a rule about the operands of a call has to see, and still removes a text block, that being how a
 * fixture in this repository carries a quoted source.
 */
@UtilityClass
final class JavaCode {

    /*
     * One alternation over text blocks, string literals, character literals and both comment forms, so
     * whichever starts first consumes the rest. Scanning them separately gets the boundaries wrong in
     * both directions: a "//" inside a string opens no comment, and a quotation mark inside a comment
     * opens no string.
     *
     * The text block branch consumes its own escapes and allows one or two quotation marks that are not
     * a delimiter, so a block embedding its own delimiter stays a single token. Ending it early leaves
     * the rest of the block to be read as live code, which is the failure this class exists to prevent.
     *
     * Every branch reads runs rather than single characters, and reads them possessively. A matcher
     * spends a stack frame on each turn of a group it repeats, so a branch spelled one character at a
     * time costs a frame for every character of the literal it masks, and the depth of the scan comes to
     * follow the length of the source. Every rule in this package reads its sources through here, so the
     * limit that spelling sets would be the limit of all of them.
     */
    static final Pattern TOKEN = Pattern.compile(
        "(?s)\"\"\"(?:\\\\.|[^\"\\\\]++|\"{1,2}+(?!\"))*+\"\"\"|\"(?:\\\\.|[^\"\\\\\\n]++)*+\"|'(?:\\\\.|[^'\\\\]++)*+'"
            + "|/\\*.*?\\*/|//[^\\n]*"
    );
    private static final String TEXT_BLOCK = "\"\"\"";
    private static final String BLOCK_COMMENT = "/*";
    private static final String LINE_COMMENT = "//";
    private static final char NEWLINE = '\n';
    private static final char SPACE = ' ';

    /**
     * The source with every literal and every comment blanked out.
     *
     * @param source the source to read
     * @return the source at its original width, carrying code alone
     */
    static String blanked(CharSequence source) {
        return blank(source, _ -> true);
    }

    /**
     * The source with comments and text blocks blanked out, and one-line literals left in place.
     *
     * @param source the source to read
     * @return the source at its original width, carrying code and its one-line literals
     */
    static String withoutComments(CharSequence source) {
        return blank(source, JavaCode::quotesAnotherSource);
    }

    private static boolean quotesAnotherSource(MatchResult token) {
        String text = token.group();
        return text.startsWith(TEXT_BLOCK) || text.startsWith(BLOCK_COMMENT) || text.startsWith(LINE_COMMENT);
    }

    private static String blank(CharSequence source, Predicate<MatchResult> removed) {
        StringBuilder result = new StringBuilder(source);
        TOKEN.matcher(source).results().filter(removed).forEach(token -> widen(result, token));
        return result.toString();
    }

    private static void widen(StringBuilder target, MatchResult token) {
        for (int index = token.start(); index < token.end(); index += 1) {
            if (target.charAt(index) != NEWLINE) {
                target.setCharAt(index, SPACE);
            }
        }
    }

    /**
     * The one-based line an offset falls on.
     *
     * @param source the source the offset points into
     * @param offset the offset
     * @return the line number
     */
    static int lineOf(CharSequence source, int offset) {
        return (int) source.subSequence(0, offset).chars().filter(character -> character == NEWLINE).count() + 1;
    }
}
