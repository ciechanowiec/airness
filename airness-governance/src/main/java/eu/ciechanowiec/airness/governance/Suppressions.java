package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Locates every rule a source turns off, one entry per rule rather than one per annotation.
 *
 * <p>Counting annotations would price a suppression by how it was written. One annotation naming three
 * rules sets three rules aside, and a project told to keep its annotation count down would learn to
 * bundle them, which is the opposite of what a ceiling is for.
 *
 * <p>Both suppression annotations a project can reach are read. {@link SuppressWarnings} carries the
 * findings of the compiler, of the static analyzers and of the inspection engine, while SpotBugs has an
 * annotation of its own and would otherwise be the one analyzer whose findings could be set aside for
 * free. The reason written beside a SpotBugs suppression is removed before the rules are counted,
 * because a justification is prose about the suppression rather than a second rule.
 *
 * <p>The scan reads the source with its text blocks blanked, so a fixture quoting a suppression is not
 * counted against the file that quotes it, and with its one-line literals intact, because the name of
 * the suppressed rule is written as one.
 */
@UtilityClass
final class Suppressions {

    /*
     * The argument list is matched as a run of string literals and non-parenthesis characters, so a
     * parenthesis inside a justification is consumed by the literal that holds it. Ending the list at
     * the first parenthesis instead cut the arguments in half, and which half survived depended on
     * whether the reason had been written before or after the rules it explains.
     *
     * Each run is taken whole rather than a character at a time, so a justification written at the
     * length these rules ask for costs one stack frame instead of one per character of the reason.
     */
    private static final Pattern DECLARATION = Pattern.compile(
        "@Suppress(?:FB)?Warnings\\s*\\(((?:\"(?:[^\"\\\\]++|\\\\.)*+\"|[^)\"]++)*+)\\)"
    );
    private static final Pattern JUSTIFICATION = Pattern.compile(
        "justification\\s*=\\s*\"(?:[^\"\\\\]++|\\\\.)*+\"(?:\\s*\\+\\s*\"(?:[^\"\\\\]++|\\\\.)*+\")*"
    );
    private static final Pattern RULE = Pattern.compile("\"([^\"]*)\"");
    private static final String SPACE = " ";

    /**
     * Every rule the source suppresses, in the order the source declares them.
     *
     * @param source the decoded text of a Java source
     * @return one entry per suppressed rule, naming its line and the rule
     */
    static List<String> in(CharSequence source) {
        String readable = JavaCode.withoutComments(source);
        return DECLARATION.matcher(readable).results()
            .flatMap(declaration -> rulesOf(declaration, readable))
            .toList();
    }

    private static Stream<String> rulesOf(MatchResult declaration, CharSequence readable) {
        int line = JavaCode.lineOf(readable, declaration.start());
        String named = JUSTIFICATION.matcher(declaration.group(1)).replaceAll(SPACE);
        return RULE.matcher(named).results()
            .map(rule -> "line %d: %s".formatted(line, rule.group(1)));
    }
}
