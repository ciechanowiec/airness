package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The mappings a ready application left open to an anonymous caller, read against the patterns the
 * project says it meant to leave open.
 *
 * <p>Which endpoints an application exposes without authentication is not written anywhere. The
 * mapping is in a controller, the matcher is in a security configuration, and the path that joins
 * them is composed by the container out of a class-level annotation, a method-level annotation and a
 * context path. Every rule that reads one file, and every rule that reads two, can be satisfied by a
 * project whose endpoints are open, because being open is not a thing either file says. It is a
 * thing the built context does, which is why the first half of this rule is evidence rather than
 * text.
 *
 * <p>The second half is the declaration the project made. A matcher that names the pattern is that
 * declaration: it states one path and admits one path, and adding an endpoint later does not widen
 * it. A matcher that names a prefix is not, because it admits whatever is mapped under that prefix
 * afterwards by anyone, and the moment that widens it is the moment nobody reads it. Airness already
 * refuses the two widest spellings of this, {@code anyRequest().permitAll()} and
 * {@code requestMatchers("/**").permitAll()}. What an intermediate prefix admits stays invisible
 * until something asks the running container, and this asks it.
 *
 * <p>Comparison is by the pattern the container mapped against the literal the project wrote, as
 * strings. Nothing here re-implements path matching: the evidence keeps the pattern as the mapping
 * declared it, so a project that names the same pattern in its matcher and its mapping is naming one
 * string twice, and a project that names anything else has not named this endpoint.
 */
@UtilityClass
final class SpringEndpointRules {

    private static final Pattern OPEN = Pattern.compile("^open\\s+(\\S+)\\s+(\\S+)$");
    private static final Pattern MATCHERS = Pattern.compile("\\brequestMatchers\\s*\\(");
    private static final Pattern PERMITTED = Pattern.compile("^\\s*\\.\\s*permitAll\\s*\\(");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");

    /**
     * Every mapping the evidence found open that no matcher of the module names.
     *
     * @param open  the {@code open <METHOD> <pattern>} lines the ready application wrote
     * @param types the types of the module, whose production sources the matchers are read from
     * @return one offence per open mapping the project never declared public
     */
    static List<String> undeclared(List<String> open, SpringTypes types) {
        Set<String> named = permitted(types);
        return open.stream()
            .map(OPEN::matcher)
            .filter(Matcher::matches)
            .filter(line -> !named.contains(line.group(2)))
            .map(line -> offence(line.group(1), line.group(2)))
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Every pattern a matcher of the module admits without asking anything of the caller.
     *
     * @param types the types of the module
     * @return the literals written inside a {@code requestMatchers(...)} that {@code permitAll}
     *         closes, taken from production sources only, a test being free to admit what it likes
     */
    private static Set<String> permitted(SpringTypes types) {
        return types.all().stream()
            .filter(SpringTypes.Declared::production)
            .flatMap(SpringEndpointRules::literals)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Stream<String> literals(SpringTypes.Declared type) {
        String code = type.code();
        String read = type.quoted();
        return MATCHERS.matcher(code).results()
            .filter(matcher -> permits(code, matcher))
            .flatMap(matcher -> named(read, code, matcher));
    }

    /**
     * Whether the call opened here is the one {@code permitAll} closes.
     *
     * @param code    the source with its literals blanked, which the parentheses are counted over
     * @param matcher the {@code requestMatchers(} that was found
     * @return whether the next thing written after the call is the permitting one
     */
    private static boolean permits(String code, MatchResult matcher) {
        int closes = SpringMembers.closing(code, matcher.end() - 1);
        return closes < code.length() && PERMITTED.matcher(code.substring(closes + 1)).find();
    }

    private static Stream<String> named(String read, String code, MatchResult matcher) {
        int opens = matcher.end() - 1;
        int closes = Math.min(SpringMembers.closing(code, opens), read.length());
        return LITERAL.matcher(read.substring(Math.min(opens + 1, closes), closes))
            .results()
            .map(literal -> literal.group(1));
    }

    private static String offence(String method, String pattern) {
        return method + ' ' + pattern
            + ": the security chain let an unauthenticated request reach this mapping, and no"
            + " permitAll matcher of this module names the pattern, so the endpoint answers whoever"
            + " asks and no source of the project says that was meant."
            + " Name the pattern with requestMatchers(\"" + pattern + "\").permitAll() where it is"
            + " genuinely public, or cover it with a rule that requires something of the caller";
    }
}
