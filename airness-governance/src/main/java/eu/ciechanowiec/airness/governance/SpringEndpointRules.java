package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>Comparison is by the pattern the container mapped against the pattern the matcher states, as
 * strings. Nothing here re-implements path matching: the evidence keeps the pattern as the mapping
 * declared it, so a project that names the same pattern in its matcher and its mapping is naming one
 * string twice, and a project that names anything else has not named this endpoint.
 *
 * <p>What a matcher states is a literal, or a name the module resolves to one. A security
 * configuration collects its paths into constants as soon as it holds more than a few, and a rule
 * reading literals alone tells the author of {@code requestMatchers(LOGIN, ICONS).permitAll()} to
 * name the patterns that call already names. So a bare name is read against the string constants of
 * the same source, and a qualified name against the constant that type declares anywhere in the
 * production sources of the module, which is unambiguous because one source declares one type. Only
 * the production sources are indexed, so a constant a test owns admits nothing here either. This
 * reaches further than the rules that read one file at a time, and the reason is that this one is
 * already the module's reader: it answers runtime evidence rather than the text of one source.
 *
 * <p>An argument that resolves to no written string states no pattern, which leaves the mapping
 * reported. A call and a constant declared outside the module are each of that kind. That is the
 * direction this has to fail in: a matcher the rule cannot read is a matcher nobody has shown to be
 * a declaration, and failing the other way would go quiet over exactly the spellings it cannot see.
 * A literal written inside a call is still read where it sits, since {@code antMatcher("/login")}
 * names the pattern as plainly as the literal does.
 */
@UtilityClass
final class SpringEndpointRules {

    private static final Pattern OPEN = Pattern.compile("^open\\s+(\\S+)\\s+(\\S+)$");
    private static final Pattern MATCHERS = Pattern.compile("\\brequestMatchers\\s*\\(");
    private static final Pattern PERMITTED = Pattern.compile("^\\s*\\.\\s*permitAll\\s*\\(");
    // A matcher argument as the source states it: a literal, a constant of another type, or a constant
    // of this one. The literal runs first, so a dot inside a string is text rather than a reference, and
    // the qualified name before the bare one, so HttpMethod.GET is one token rather than a stray GET.
    private static final Pattern WRITTEN = Pattern.compile(
        "\"[^\"]*\"|\\b[A-Za-z_$][\\w$]*\\.[A-Z][A-Z0-9_]*\\b|\\b[A-Z][A-Z0-9_]*\\b"
    );

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
        // The production filter runs before the index is built, which is the whole of what makes a
        // constant only a test declares admit nothing.
        List<MatcherReading> sources = types.all()
            .stream()
            .filter(SpringTypes.Declared::production)
            .map(MatcherReading::of)
            .toList();
        Map<String, String> qualified = qualified(sources);
        return sources.stream()
            .flatMap(source -> patterns(source, qualified))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Every string constant the production sources declare, under the name a matcher writes for it.
     *
     * @param sources the production sources, already read
     * @return the constants by their qualified name, the first declaration of a name winning
     */
    private static Map<String, String> qualified(List<MatcherReading> sources) {
        return sources.stream()
            .flatMap(
                source -> source.constants()
                    .entrySet()
                    .stream()
                    .map(constant -> Map.entry(source.name() + '.' + constant.getKey(), constant.getValue()))
            )
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first)
            );
    }

    private static Stream<String> patterns(MatcherReading source, Map<String, String> qualified) {
        String code = source.code();
        return MATCHERS.matcher(code).results()
            .filter(matcher -> permits(code, matcher))
            .flatMap(matcher -> named(source, qualified, matcher));
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

    private static Stream<String> named(MatcherReading source, Map<String, String> qualified, MatchResult matcher) {
        String read = source.read();
        int opens = matcher.end() - 1;
        int closes = Math.min(SpringMembers.closing(source.code(), opens), read.length());
        return WRITTEN.matcher(read.substring(Math.min(opens + 1, closes), closes))
            .results()
            .flatMap(written -> source.resolved(qualified, written.group()).stream());
    }

    private static String offence(String method, String pattern) {
        return method + ' ' + pattern
            + ": the security chain let an unauthenticated request reach this mapping, and no"
            + " permitAll matcher of this module names the pattern, so the endpoint answers whoever"
            + " asks and no source of the project says that was meant."
            + " Name the pattern with requestMatchers(\"" + pattern + "\").permitAll() where it is"
            + " genuinely public, writing it out or naming a constant of this module that holds that"
            + " exact string, or cover it with a rule that requires something of the caller";
    }

    /**
     * One production source in the three readings this rule takes of it, so that each is made once.
     *
     * @param name      the type the source declares, which a qualified name states
     * @param code      the source with comments and literals blanked, which parentheses are counted over
     * @param read      the source with comments blanked and literals kept, which arguments are read from
     * @param constants the string constants the source declares
     */
    private record MatcherReading(String name, String code, String read, Map<String, String> constants) {

        private static MatcherReading of(SpringTypes.Declared type) {
            String read = type.quoted();
            return new MatcherReading(type.name(), type.code(), read, SpringSourceRules.stringConstants(read));
        }

        private Optional<String> resolved(Map<String, String> qualified, String written) {
            return SpringSourceRules.resolvedString(this.constants, written)
                .or(() -> Optional.ofNullable(qualified.get(written)));
        }
    }
}
