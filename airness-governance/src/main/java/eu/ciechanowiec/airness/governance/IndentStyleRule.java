package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import org.ec4j.core.model.PropertyType.IndentStyleValue;

/**
 * The whitespace that opens each line is the kind {@code indent_style} names.
 *
 * <p>Under {@code space} no tab appears anywhere in it, since a tab renders at whatever width the reader's
 * editor chooses and the nesting a reader sees then differs from the nesting the file means. Under
 * {@code tab} it does not begin with a space. Spaces after the tabs stay allowed, because aligning a
 * continuation under the line above it is a matter of columns rather than of nesting, and a tab cannot
 * express a column.
 */
@UtilityClass
final class IndentStyleRule {

    private static final char TAB = '\t';
    private static final char SPACE = ' ';

    /**
     * The first line whose leading whitespace is not the kind the style names.
     *
     * @param lines the file's lines, in order
     * @param style the {@code indent_style} the file answers to
     * @return the first departure, or nothing when every line keeps to the style
     */
    static Optional<IndentationDeviation> first(List<String> lines, IndentStyleValue style) {
        return IntStream.range(0, lines.size())
            .filter(index -> departs(leading(lines.get(index)), style))
            .mapToObj(index -> deviation(style, index + 1))
            .findFirst();
    }

    private static boolean departs(String leading, IndentStyleValue style) {
        return switch (style) {
            case space -> leading.indexOf(TAB) >= 0;
            case tab -> !leading.isEmpty() && leading.charAt(0) == SPACE;
        };
    }

    private static IndentationDeviation deviation(IndentStyleValue style, int line) {
        String found = switch (style) {
            case space -> "a tab";
            case tab -> "a space";
        };
        return new IndentationDeviation(
            line, "indent_style = %s, but line %d indents with %s".formatted(style.name(), line, found)
        );
    }

    private static String leading(String line) {
        int end = IntStream.range(0, line.length())
            .filter(index -> !indents(line.charAt(index)))
            .findFirst()
            .orElse(line.length());
        return line.substring(0, end);
    }

    private static boolean indents(char character) {
        return character == SPACE || character == TAB;
    }
}
