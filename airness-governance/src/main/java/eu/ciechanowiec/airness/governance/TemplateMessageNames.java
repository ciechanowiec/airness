package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Literal message names in expressions, without executing model values or arguments.
 */
@UtilityClass
final class TemplateMessageNames {

    private static final Pattern NAME = Pattern.compile(
        "\\s*(?:'([A-Za-z0-9_.-]+)'|([A-Za-z0-9_.-]+))\\s*(?:\\((?s:.*)\\))?\\s*"
    );

    static List<String> in(String expression) {
        List<String> names = new ArrayList<>();
        int at = 0;
        while (at < expression.length()) {
            at = advance(expression, at, names);
        }
        return List.copyOf(names);
    }

    private static int advance(String expression, int at, Collection<String> names) {
        if (quoted(expression.charAt(at))) {
            return afterQuote(expression, at);
        }
        return expression.startsWith("#{", at) ? message(expression, at, names) : at + 1;
    }

    private static int message(String expression, int at, Collection<String> names) {
        int end = afterMessage(expression, at + 2);
        if (end > 0) {
            String body = expression.substring(at + 2, end - 1);
            named(body).ifPresent(names::add);
        }
        return end < 0 ? expression.length() : at + 2;
    }

    private static Optional<String> named(String body) {
        Matcher name = NAME.matcher(body);
        return name.matches() && balanced(body) ? Optional.ofNullable(name.group(1)).or(
            () -> Optional.of(name.group(2))
        )
            : Optional.empty();
    }

    private static boolean balanced(String body) {
        int depth = 0;
        int at = 0;
        while (at < body.length() && depth >= 0) {
            char current = body.charAt(at);
            depth += step(current, '(', ')');
            at = next(body, at);
        }
        return depth == 0;
    }

    private static int afterMessage(String expression, int begin) {
        int depth = 1;
        int at = begin;
        while (at < expression.length() && depth > 0) {
            char current = expression.charAt(at);
            depth += step(current, '{', '}');
            at = next(expression, at);
        }
        return depth == 0 ? at : -1;
    }

    private static int next(String expression, int at) {
        return quoted(expression.charAt(at)) ? afterQuote(expression, at) : at + 1;
    }

    private static int step(char current, char opens, char closes) {
        int opening = current == opens ? 1 : 0;
        return current == closes ? -1 : opening;
    }

    private static boolean quoted(char current) {
        return current == '\'' || current == '"';
    }

    private static int afterQuote(String expression, int begin) {
        int at = begin + 1;
        while (at < expression.length()) {
            if (expression.charAt(at) == expression.charAt(begin) && !escaped(expression, at)) {
                return at + 1;
            }
            at++;
        }
        return expression.length();
    }

    private static boolean escaped(String expression, int at) {
        int escapes = 0;
        for (int before = at - 1; before >= 0 && expression.charAt(before) == '\\'; before--) {
            escapes++;
        }
        return escapes % 2 != 0;
    }
}
