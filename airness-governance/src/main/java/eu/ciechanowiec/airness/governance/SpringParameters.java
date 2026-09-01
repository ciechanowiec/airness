package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Finds and divides the parameter list of a Spring-annotated method.
 *
 * <p>An annotation and a method both read as a name followed by parentheses. A reader that takes the
 * first pair after an annotation therefore takes the arguments of a second annotation whenever one is
 * written between the two. This reader skips annotation names and balances every nested delimiter before
 * it divides the method list, so an annotation argument or a generic type contributes no parameter of its
 * own.
 */
@UtilityClass
final class SpringParameters {

    private static final Pattern MEMBER = Pattern.compile("\\b(\\w+)\\s*\\(");
    private static final Pattern NAME = Pattern.compile("(\\w+)\\s*(?:\\[\\s*])?\\s*$");
    private static final char ANNOTATION = '@';
    private static final char COMMA = ',';

    /**
     * The method parameter list that follows an annotation.
     *
     * @param code source with comments and literals blanked
     * @param from offset immediately after the annotation
     * @return the range inside the method parentheses
     */
    static Optional<Range> after(String code, int from) {
        return MEMBER.matcher(code).results()
            .filter(match -> match.start() >= from)
            .filter(match -> code.charAt(match.start(1) - 1) != ANNOTATION)
            .findFirst()
            .map(match -> new Range(match.end() - 1, SpringMembers.closing(code, match.end() - 1)));
    }

    /**
     * The parameters inside a range, preserving their source offsets and written annotations.
     *
     * @param source source as written
     * @param code   source with comments and literals blanked
     * @param range  method parameter range
     * @return one segment per parameter
     */
    static List<Parameter> in(CharSequence source, String code, Range range) {
        List<Parameter> parameters = new ArrayList<>();
        int start = range.opens() + 1;
        int round = 0;
        int square = 0;
        int angle = 0;
        for (int index = start; index < range.closes(); index += 1) {
            char character = code.charAt(index);
            round += delta(character, '(', ')');
            square += delta(character, '[', ']');
            angle += delta(character, '<', '>');
            if (character == COMMA && round + square + angle == 0) {
                add(parameters, source, start, index);
                start = index + 1;
            }
        }
        add(parameters, source, start, range.closes());
        return List.copyOf(parameters);
    }

    private static int delta(char character, char opening, char closing) {
        if (character == opening) {
            return 1;
        }
        return character == closing ? -1 : 0;
    }

    private static void add(List<Parameter> parameters, CharSequence source, int start, int end) {
        String text = source.subSequence(start, end).toString().trim();
        if (!text.isEmpty()) {
            parameters.add(new Parameter(text, start, name(text)));
        }
    }

    private static String name(String parameter) {
        String readable = JavaCode.withoutComments(parameter);
        Matcher found = NAME.matcher(readable);
        if (!found.find()) {
            throw new IllegalArgumentException("Unreadable Java parameter: " + parameter);
        }
        return found.group(1);
    }

    /**
     * The parentheses occupied by a method parameter list.
     *
     * @param opens  offset of the opening parenthesis
     * @param closes offset of the closing parenthesis
     */
    public record Range(int opens, int closes) {
    }

    /**
     * One method parameter as written.
     *
     * @param text   complete parameter declaration
     * @param offset source offset at which the declaration begins
     * @param name   Java parameter name
     */
    public record Parameter(String text, int offset, String name) {
    }
}
