package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Finds names in a Javadoc comment that denote a type the compiler could resolve from that file, yet
 * are written as prose or as {@code @code} rather than as a link. A type that resolves is always
 * written as a link, so a reader can reach it and the Javadoc tool can check it. {@code @code} is what
 * is left for the things a link cannot reach, such as literals, command names and configuration keys.
 *
 * <p>What a name denotes is not decidable from the text, so the caller decides: it supplies the names
 * that resolve from the file being read, and this class reports the ones it finds unlinked. Three
 * regions are read past, because a name in them is already accounted for: an existing link, a
 * {@code <pre>} sample, which is code rather than prose, and the target of a {@code @param},
 * {@code @throws} or {@code @see} tag, which the Javadoc tool links on its own.
 */
@UtilityClass
final class JavadocLinkRules {

    // A text block consumes its escapes, so a block embedding its own delimiter is one token rather than
    // two and a half. Ending the token early leaves the rest of the block to be read as code, which is
    // what decides the imports and the declared types this rule resolves names against.
    private static final Pattern TOKEN = Pattern.compile(
        "(?s)\"\"\"(?:\\\\.|[^\\\\])*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\])*'"
            + "|/\\*.*?\\*/|//[^\\n]*"
    );
    private static final Pattern LINK = Pattern.compile("\\{@(?:link|linkplain)\\s+[^}]*}");
    private static final Pattern SAMPLE = Pattern.compile("(?s)<pre>.*?</pre>");
    private static final Pattern TAG_TARGET = Pattern.compile("@(?:param|throws|exception|see)\\s+\\S+");
    // A name opening a hyphenated compound is an ordinary word, because no Java type name carries a
    // hyphen. Reading one as a type made the verdict depend on an unrelated import: the same sentence
    // about a repository-relative path was reported in the file that imports Repository and passed in the
    // file beside it that does not.
    private static final Pattern NAME = Pattern.compile("\\b[A-Z][A-Za-z0-9]*\\b(?!-[a-z])");
    private static final String SPACE = " ";

    /**
     * The type names {@code source} mentions in Javadoc without linking them, in encounter order and
     * without repeats.
     */
    static List<String> unlinked(CharSequence source, Predicate<String> resolves) {
        return TOKEN.matcher(source).results()
            .map(MatchResult::group)
            .filter(token -> token.startsWith("/**"))
            .flatMap(comment -> namesIn(comment, resolves))
            .distinct()
            .toList();
    }

    static String codeOnly(CharSequence source) {
        return TOKEN.matcher(source).replaceAll(SPACE);
    }

    private static Stream<String> namesIn(CharSequence comment, Predicate<String> resolves) {
        String prose = accountedFor(comment);
        return NAME.matcher(prose).results()
            .map(MatchResult::group)
            .filter(resolves);
    }

    private static String accountedFor(CharSequence comment) {
        String withoutSamples = SAMPLE.matcher(comment).replaceAll(SPACE);
        String withoutLinks = LINK.matcher(withoutSamples).replaceAll(SPACE);
        return TAG_TARGET.matcher(withoutLinks).replaceAll(SPACE);
    }
}
