package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * A value of the secret scanner's configuration as gitleaks reads it: the strings it holds, and whether an
 * exception's regex excuses one exact value.
 *
 * <p>Exactness is judged by how gitleaks matches, not by how the value reads. It searches an exception's
 * regex anywhere in its target, so a literal with no metacharacter still excuses every finding that
 * contains it. An exact value is therefore anchored as {@code ^value$} and long enough to be one credential
 * rather than a fragment many share.
 *
 * <p>A literal string, {@code '''...'''} or {@code '...'}, reaches gitleaks exactly as written. A
 * double-quoted one has its escapes decoded first, so {@link SecretScanConfiguration} refuses it as a regex
 * and only the literal strings are judged here.
 */
@UtilityClass
final class SecretScanValue {

    private static final Pattern STRING = Pattern.compile("'''(.*?)'''|\"([^\"]*)\"|'([^']*)'", Pattern.DOTALL);
    private static final int MULTI_LINE_LITERAL = 1;
    private static final int DOUBLE_QUOTED = 2;
    private static final int LITERAL = 3;
    /**
     * The body of one exact value between its anchors: ordinary characters, punctuation escaped with a
     * backslash, and {@code \n} for the line break a multi-line fixture carries. Tested by what widens a
     * match rather than by an alphabet a value is allowed to draw from, because an exact credential is
     * entitled to a space, a dash or any other ordinary character, and a whole key carries a {@code +}
     * that only an escape lets it spell. An escaped letter or digit other than {@code n} is a class such as
     * {@code \d}, which widens the match.
     */
    private static final Pattern EXACT_BODY = Pattern.compile("(?:[^.*+?|()\\[\\]{}^$\\\\]|\\\\[^A-Za-z0-9]|\\\\n)*");
    // One character of an exact body, an escape counting as the single character it spells.
    private static final Pattern EXACT_CHARACTER = Pattern.compile("\\\\.|.", Pattern.DOTALL);
    private static final String ANCHOR_START = "^";
    private static final String ANCHOR_END = "$";
    private static final int SHORTEST_EXACT_VALUE = 12;

    /**
     * The value with every string it holds removed, which leaves the punctuation that gives it its shape.
     *
     * @param value a value as written
     * @return what of it lies outside its strings
     */
    static String outsideStrings(CharSequence value) {
        return STRING.matcher(value).replaceAll("");
    }

    /**
     * Whether the value holds a double-quoted string, whose escapes gitleaks decodes before it reads it.
     *
     * @param value a value as written
     * @return whether any of its strings is double-quoted
     */
    static boolean doubleQuoted(CharSequence value) {
        return STRING.matcher(value).results().anyMatch(SecretScanValue::isDoubleQuoted);
    }

    /**
     * The literal strings of a value, which reach gitleaks exactly as they are written.
     *
     * @param value a value as written
     * @return the content of each literal string, in value order
     */
    static List<String> literals(CharSequence value) {
        return STRING.matcher(value).results()
            .filter(hit -> !isDoubleQuoted(hit))
            .map(
                hit -> Objects.requireNonNullElse(
                    hit.group(MULTI_LINE_LITERAL), Objects.requireNonNullElse(hit.group(LITERAL), "")
                )
            ).toList();
    }

    /**
     * Every string of a value, however it is quoted.
     *
     * @param value a value as written
     * @return the content of each string, in value order
     */
    static List<String> strings(CharSequence value) {
        return STRING.matcher(value).results()
            .map(
                hit -> Objects.requireNonNullElse(
                    hit.group(MULTI_LINE_LITERAL), Objects.requireNonNullElse(
                        hit.group(DOUBLE_QUOTED), Objects.requireNonNullElse(hit.group(LITERAL), "")
                    )
                )
            ).toList();
    }

    /**
     * Why an allowlist value is not one exact value, or nothing when it is.
     *
     * <p>A value fails this for any of three unrelated reasons, and each asks for a different repair.
     * Reporting them as the same sentence sent the reader of a short literal looking for a
     * metacharacter that was never there, so each says what it means.
     *
     * @param value the allowlist value as written
     * @return the reason the value is not exact, or nothing when it is
     */
    static Optional<String> inexactness(String value) {
        boolean anchored = value.startsWith(ANCHOR_START) && value.endsWith(ANCHOR_END);
        String body = anchored
            ? value.substring(ANCHOR_START.length(), value.length() - ANCHOR_END.length())
            : value;
        if (!EXACT_BODY.matcher(body).matches()) {
            return Optional.of("is a pattern rather than an exact value");
        }
        return anchored
            ? shortness(body)
            : Optional.of("matches every finding that contains it; write it as ^value$ so it matches one whole secret");
    }

    // Whichever of the three spellings matched carries the content, and the groups of the other two are empty.
    private static boolean isDoubleQuoted(MatchResult hit) {
        return Optional.ofNullable(hit.group(DOUBLE_QUOTED)).isPresent();
    }

    private static Optional<String> shortness(CharSequence body) {
        return EXACT_CHARACTER.matcher(body).results().count() < SHORTEST_EXACT_VALUE
            ? Optional.of(
                "is too short to be an exact value, which needs at least "
                    + SHORTEST_EXACT_VALUE + " characters"
            )
            : Optional.empty();
    }
}
