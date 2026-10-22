package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Leading COPY options, with Docker's quote, escape and option-terminator boundaries.
 */
@UtilityClass
final class DockerCopyOptions {

    private static final String FROM = "--from=";
    private static final Pattern OPTIONS = Pattern.compile(
        "\\G\\s*(--(?:[^\\s'\"\\\\]|\\\\."
            + "|'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\")*)(?=\\s|$)"
    );
    private static final Pattern PARTS = Pattern.compile(
        "\\\\(.)|'((?:\\\\.|[^'\\\\])*)'|\"((?:\\\\.|[^\"\\\\])*)\""
    );
    private static final Pattern ESCAPED = Pattern.compile("\\\\(.)");

    static Optional<String> source(CharSequence arguments) {
        return OPTIONS.matcher(arguments).results().map(option -> unquoted(option.group(1)))
            .takeWhile(option -> !"--".equals(option))
            .filter(option -> option.startsWith(FROM))
            .map(option -> option.substring(FROM.length()))
            .findFirst();
    }

    private static String unquoted(String option) {
        return PARTS.matcher(option).replaceAll(part -> Matcher.quoteReplacement(value(part)));
    }

    private static String value(MatchResult part) {
        String value = Stream.of(part.group(1), part.group(2), part.group(3))
            .flatMap(text -> Optional.ofNullable(text).stream()).findFirst().orElseThrow();
        return ESCAPED.matcher(value).replaceAll("$1");
    }
}
