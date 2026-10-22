package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Evaluated variable and selection bodies, separated from template literal text.
 */
@UtilityClass
final class TemplateEvaluations {

    private static final int PAIR = 2;
    private static final char SUBSTITUTION = '|';
    private static final String QUOTES = "'\"";
    private static final Pattern LITERALS = Pattern.compile(
        "'(?:\\\\.|''|[^'\\\\])*'|\"(?:\\\\.|\"\"|[^\"\\\\])*\""
    );

    static List<String> regions(String written) {
        List<String> found = new ArrayList<>();
        int offset = 0;
        int substitutions = 0;
        while (offset < written.length()) {
            Scan step = scan(written, offset, substitutions);
            step.expression().ifPresent(found::add);
            offset = step.next();
            substitutions = step.substitutions();
        }
        return List.copyOf(found);
    }

    private static Scan scan(String written, int offset, int substitutions) {
        if (opens(written, offset)) {
            int end = ending(written, offset + PAIR);
            String expression = LITERALS.matcher(written.substring(offset + PAIR, end)).replaceAll(" ");
            return new Scan(end + 1, substitutions, Optional.of(expression));
        }
        int count = written.charAt(offset) == SUBSTITUTION ? substitutions + 1 : substitutions;
        boolean literal = count % PAIR == 0 && quoted(written.charAt(offset));
        int next = literal ? literalEnd(written, offset) : offset + 1;
        return new Scan(next, count, Optional.empty());
    }

    private static boolean opens(String written, int offset) {
        return written.startsWith("${", offset) || written.startsWith("*{", offset);
    }

    private static boolean quoted(char character) {
        return QUOTES.indexOf(character) >= 0;
    }

    private static int literalEnd(String written, int offset) {
        Matcher literal = LITERALS.matcher(written).region(offset, written.length());
        return literal.lookingAt() ? literal.end() : written.length();
    }

    private static int ending(String written, int from) {
        int depth = 1;
        int offset = from;
        while (offset < written.length() && depth > 0) {
            Boundary step = boundary(written, offset);
            depth += step.nestingChange();
            offset = step.next();
        }
        return depth == 0 ? offset - 1 : written.length();
    }

    private static Boundary boundary(String written, int offset) {
        return quoted(written.charAt(offset)) ? new Boundary(literalEnd(written, offset), 0)
            : new Boundary(offset + 1, brace(written.charAt(offset)));
    }

    private static int brace(char character) {
        return switch (character) {
            case '{' -> 1;
            case '}' -> -1;
            default -> 0;
        };
    }

    private record Scan(int next, int substitutions, Optional<String> expression) {
    }

    private record Boundary(int next, int nestingChange) {
    }
}
