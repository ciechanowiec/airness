package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 *
 * <p>A name that resolves only because {@code java.lang} is imported into every file is read as an
 * ordinary word where it opens a sentence, because that is where every word is capitalized, and a sentence
 * opening on the word for a count or a fault speaks of neither type. Elsewhere in a sentence, and wherever
 * the file names the type itself, it is a type.
 */
@UtilityClass
final class JavadocLinkRules {

    // The lexer is JavaCode's. Two copies of a Java tokenizer maintained side by side is how the fourth
    // edit to one of them leaves the other reading a text block as live code.
    private static final Pattern TOKEN = JavaCode.TOKEN;
    private static final Pattern LINK = Pattern.compile("\\{@(?:link|linkplain)\\s+[^}]*}");
    private static final Pattern SAMPLE = Pattern.compile("(?s)<pre>.*?</pre>");
    private static final Pattern TAG_TARGET = Pattern.compile("@(?:param|throws|exception|see)\\s+\\S+");
    // A name opening a hyphenated compound is an ordinary word, because no Java type name carries a
    // hyphen. Reading one as a type made the verdict depend on an unrelated import: the same sentence
    // about a repository-relative path was reported in the file that imports Repository and passed in the
    // file beside it that does not.
    private static final Pattern NAME = Pattern.compile("\\b[A-Z][A-Za-z0-9]*\\b(?!-[a-z])");
    // What a sentence opens after, so the end of a match is where its first word starts: the comment's
    // opening, a paragraph or list item, or the stop of the sentence before followed by blank space.
    private static final Pattern SENTENCE_OPENING = Pattern.compile(
        "(?i)(?:/\\*\\*|<p>|<li>)[\\s*]*|[.!?][\\s*]+"
    );
    private static final String SPACE = " ";

    /**
     * The type names {@code source} mentions in Javadoc without linking them, in encounter order and
     * without repeats.
     *
     * @param source   the Java source
     * @param named    whether a name resolves through what the file names itself
     * @param implicit whether a name resolves through what every file resolves without naming it
     * @return the unlinked names
     */
    static List<String> unlinked(CharSequence source, Predicate<String> named, Predicate<String> implicit) {
        return TOKEN.matcher(source).results()
            .map(MatchResult::group)
            .filter(token -> token.startsWith("/**"))
            .flatMap(comment -> namesIn(comment, named, implicit))
            .distinct()
            .toList();
    }

    static String codeOnly(CharSequence source) {
        return TOKEN.matcher(source).replaceAll(SPACE);
    }

    private static Stream<String> namesIn(CharSequence comment, Predicate<String> named, Predicate<String> implicit) {
        String prose = accountedFor(comment);
        Set<Integer> openings = SENTENCE_OPENING.matcher(prose).results()
            .map(MatchResult::end)
            .collect(Collectors.toUnmodifiableSet());
        Predicate<String> anywhere = named.or(implicit);
        return NAME.matcher(prose).results()
            .filter(name -> (openings.contains(name.start()) ? named : anywhere).test(name.group()))
            .map(MatchResult::group);
    }

    private static String accountedFor(CharSequence comment) {
        String withoutSamples = SAMPLE.matcher(comment).replaceAll(SPACE);
        String withoutLinks = LINK.matcher(withoutSamples).replaceAll(SPACE);
        return TAG_TARGET.matcher(withoutLinks).replaceAll(SPACE);
    }
}
