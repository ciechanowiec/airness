package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Finds the methods an annotation marks, and the calls made from inside one of them.
 *
 * <p>Two rule sets need the same two answers. {@link SpringSourceRules} asks them of {@code @Bean}, to
 * report a bean method that builds a second instance by calling another. {@link SpringProxyRules} asks
 * them of the annotations a proxy honours, to report a call that never leaves the object and so never
 * passes the proxy. The question is the same one twice, so it is answered here once.
 *
 * <p>Everything reads code with its comments and literals already blanked by {@link JavaCode}, which is
 * what keeps a method name inside a string or an explanation from being read as a declaration. Offsets
 * into the blanked code are offsets into the original, so a caller turns one into a line number by
 * asking {@link JavaCode} about the source it started from.
 */
@UtilityClass
final class SpringMembers {

    private static final Pattern DECLARATION = Pattern.compile("\\b(\\w+)\\s*\\(");
    private static final char ANNOTATION = '@';

    /**
     * Every method the given annotation marks, by name and by the range its body occupies.
     *
     * @param code       the source with comments and literals blanked
     * @param annotation the annotation to look for
     * @return one member per annotated declaration that opens a body, in the order they are written
     */
    static List<Member> annotated(String code, Pattern annotation) {
        return annotation.matcher(code).results()
            .map(marker -> declaration(code, marker.end()))
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * Every call to one of the given names made between two offsets.
     *
     * @param code  the source with comments and literals blanked
     * @param from  the offset the enclosing range opens at
     * @param upTo  the offset it closes at
     * @param names the method names a call to which is an offence
     * @return the matching calls, each carrying the called name as its first group
     */
    static List<MatchResult> callsWithin(String code, int from, int upTo, Collection<String> names) {
        return DECLARATION.matcher(code).results()
            .filter(call -> call.start() >= from && call.end() <= upTo)
            .filter(call -> names.contains(call.group(1)))
            .toList();
    }

    /**
     * The offset at which the brace opened at the given offset is closed again.
     *
     * @param code    the source with comments and literals blanked
     * @param opening the offset of the opening brace
     * @return the offset of the matching brace, or the end of the source when there is none
     */
    static int matching(String code, int opening) {
        return balanced(code, opening, '{', '}');
    }

    /**
     * The offset at which the parenthesis opened at the given offset is closed again.
     *
     * @param code    the text to read, which a caller has already narrowed to one construct
     * @param opening the offset of the opening parenthesis
     * @return the offset of the matching parenthesis, or the end of the text when there is none
     */
    static int closing(String code, int opening) {
        return balanced(code, opening, '(', ')');
    }

    private static Optional<Member> declaration(String code, int from) {
        return declarations(code, from)
            .filter(match -> code.charAt(match.start(1) - 1) != ANNOTATION)
            .findFirst()
            .flatMap(match -> member(code, match));
    }

    private static Stream<MatchResult> declarations(String code, int from) {
        return DECLARATION.matcher(code).results().filter(match -> match.start() >= from);
    }

    private static Optional<Member> member(String code, MatchResult match) {
        return body(code, match.end())
            .map(range -> new Member(match.group(1), match.start(1), range.start(), range.end()));
    }

    private static Optional<Range> body(String code, int parenthesis) {
        int opening = code.indexOf('{', balanced(code, parenthesis - 1, '(', ')'));
        return opening < 0 ? Optional.empty() : Optional.of(new Range(opening, matching(code, opening)));
    }

    private static int balanced(String code, int opening, char open, char shut) {
        int depth = 0;
        for (int at = opening; at < code.length(); at++) {
            depth += step(code.charAt(at), open, shut);
            if (depth == 0) {
                return at;
            }
        }
        return code.length();
    }

    private static int step(char character, char open, char shut) {
        int opened = character == open ? 1 : 0;
        return character == shut ? -1 : opened;
    }

    /**
     * The half-open source range one construct occupies.
     *
     * @param start the offset the construct opens at
     * @param end   the offset it closes at
     */
    private record Range(int start, int end) {
    }

    /**
     * One annotated method, by name, by where it is declared, and by the range of its body.
     *
     * <p>The declaration offset is what tells a call apart from the declaration it calls. Both read as a
     * name followed by an opening parenthesis, so a scan for calls finds the declaration too unless it
     * has somewhere to look the position up.
     *
     * @param name        the declared method name
     * @param declaration the offset the name is declared at
     * @param start       the offset its body opens at
     * @param end         the offset its body closes at
     */
    public record Member(String name, int declaration, int start, int end) {
    }
}
