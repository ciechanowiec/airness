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
 * <p>A matcher states a method as readily as it states a pattern, and one that states none declares
 * the pattern for every method mapped under it. That is the widening the paragraph above refuses,
 * one dimension over: a project that opens a collection to a post and reads the same collection with
 * a get has opened the read with the write, and the line that did it names only the path. So a
 * matcher naming no method is read as the declaration of the mapping it opens for as long as that
 * pattern opens one, and is asked for the method as soon as it opens two. A form drawn by a get and
 * submitted by a post is the ordinary case of that, and the two matchers it is written as are the
 * whole of what the rule costs.
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

    /**
     * What stands for the method of a matcher that names none. It is no method a caller can send, so
     * nothing the evidence reports is ever equal to it, and a mapping is covered by it only through
     * the rule that reads it.
     */
    private static final String ANY = "*";

    /**
     * The methods a matcher can name, which are the ones the evidence reports. A token is read as one
     * of these only after it has failed to resolve to a written string, so a constant of the module
     * called GET that holds a path is still a path.
     */
    private static final Set<String> METHODS = Set.of(
        "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE"
    );

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
        Set<Mapping> mappings = mappings(open);
        Map<String, Set<String>> declared = permitted(types);
        Map<String, Long> spread = spread(mappings);
        return mappings.stream()
            .flatMap(mapping -> offence(mapping, declared, spread).stream())
            .sorted()
            .toList();
    }

    /**
     * The mappings the evidence reports, each read once however many times it was written.
     *
     * @param open the evidence lines
     * @return the mapping each readable line states
     */
    private static Set<Mapping> mappings(List<String> open) {
        return open.stream()
            .map(OPEN::matcher)
            .filter(Matcher::matches)
            .map(line -> new Mapping(line.group(1), line.group(2)))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * How many methods each pattern is open under, which is what decides whether a matcher naming no
     * method named this mapping or several.
     *
     * @param mappings the mappings the evidence reports
     * @return each pattern with how many methods it is open under
     */
    private static Map<String, Long> spread(Set<Mapping> mappings) {
        return mappings.stream()
            .collect(Collectors.groupingBy(Mapping::pattern, Collectors.counting()));
    }

    /**
     * Every pattern a matcher of the module admits without asking anything of the caller.
     *
     * @param types the types of the module
     * @return the methods named for each pattern written inside a {@code requestMatchers(...)} that
     *         {@code permitAll} closes, taken from production sources only, a test being free to
     *         admit what it likes
     */
    private static Map<String, Set<String>> permitted(SpringTypes types) {
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
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toUnmodifiableSet())
                )
            );
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

    private static Stream<Map.Entry<String, String>> patterns(MatcherReading source, Map<String, String> qualified) {
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

    private static Stream<Map.Entry<String, String>> named(
        MatcherReading source, Map<String, String> qualified, MatchResult matcher
    ) {
        List<String> arguments = written(source, matcher);
        String method = method(source, qualified, arguments);
        return arguments.stream()
            .flatMap(argument -> source.resolved(qualified, argument).stream())
            .map(pattern -> Map.entry(pattern, method));
    }

    /**
     * The arguments of one matcher as the source states them, before anything is made of any of them.
     *
     * @param source  the source the matcher is written in
     * @param matcher the {@code requestMatchers(} that was found
     * @return one token per argument the call names
     */
    private static List<String> written(MatcherReading source, MatchResult matcher) {
        String read = source.read();
        int opens = matcher.end() - 1;
        int closes = Math.min(SpringMembers.closing(source.code(), opens), read.length());
        return WRITTEN.matcher(read.substring(Math.min(opens + 1, closes), closes))
            .results()
            .map(MatchResult::group)
            .toList();
    }

    /**
     * The method one matcher names, and {@link #ANY} where it names none.
     *
     * <p>Resolution runs first, so an argument the module declares as a string is a pattern whatever
     * it is called, and only what resolves to nothing is asked whether it is a method. That is what
     * keeps a constant named GET holding a path from being read as a verb.
     *
     * @param source    the source the matcher is written in
     * @param qualified the string constants of the module by their qualified name
     * @param arguments the arguments of the matcher
     * @return the method it names
     */
    private static String method(MatcherReading source, Map<String, String> qualified, List<String> arguments) {
        return arguments.stream()
            .filter(argument -> source.resolved(qualified, argument).isEmpty())
            .map(SpringEndpointRules::verb)
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(ANY);
    }

    /**
     * The method an argument names, read off the last segment of it, so that the qualified spelling
     * and the statically imported one answer alike.
     *
     * @param written the argument as the source states it
     * @return the method it names, or nothing where it names none
     */
    private static Optional<String> verb(String written) {
        String bare = written.substring(written.lastIndexOf('.') + 1);
        return METHODS.contains(bare) ? Optional.of(bare) : Optional.empty();
    }

    /**
     * What one open mapping is reported as, and nothing at all where the module declared it.
     *
     * @param mapping  the open mapping
     * @param declared the methods each pattern is named for
     * @param spread   how many methods each pattern is open under
     * @return the offence, or nothing where the module named this mapping
     */
    private static Optional<String> offence(
        Mapping mapping, Map<String, Set<String>> declared, Map<String, Long> spread
    ) {
        Set<String> methods = declared.getOrDefault(mapping.pattern(), Set.of());
        return covered(mapping, methods, spread)
            ? Optional.empty()
            : Optional.of(said(mapping, methods));
    }

    private static boolean covered(Mapping mapping, Set<String> methods, Map<String, Long> spread) {
        return methods.contains(mapping.method()) || alone(mapping, methods, spread);
    }

    // A matcher naming no method declares the pattern for whatever is mapped under it, which names
    // this mapping only for as long as this mapping is the one that pattern opens.
    private static boolean alone(Mapping mapping, Set<String> methods, Map<String, Long> spread) {
        return methods.contains(ANY) && 1L == spread.getOrDefault(mapping.pattern(), 0L);
    }

    private static String said(Mapping mapping, Set<String> methods) {
        return methods.isEmpty() ? silence(mapping) : widened(mapping);
    }

    private static String silence(Mapping mapping) {
        return mapping.line()
            + ": the security chain let an unauthenticated request reach this mapping, and no"
            + " permitAll matcher of this module names the pattern, so the endpoint answers whoever"
            + " asks and no source of the project says that was meant."
            + " Name the pattern with requestMatchers(\"" + mapping.pattern() + "\").permitAll() where"
            + " it is genuinely public, writing it out or naming a constant of this module that holds"
            + " that exact string, or cover it with a rule that requires something of the caller";
    }

    private static String widened(Mapping mapping) {
        return mapping.line()
            + ": the security chain let an unauthenticated request reach this mapping, and the"
            + " permitAll matchers of this module name the pattern without naming this method, so the"
            + " line that opened it opened every method mapped under that path rather than the one"
            + " its author read, and it will open the next one written there too."
            + " Name the method with requestMatchers(HttpMethod." + mapping.method() + ", \""
            + mapping.pattern() + "\").permitAll() where this method is genuinely public, and leave"
            + " the methods that are not to the rule that follows";
    }

    /**
     * One mapping the evidence reports open, which is a method and the pattern it is open under.
     *
     * @param method  the method a caller sends
     * @param pattern the pattern the container mapped, written as the mapping declared it
     */
    private record Mapping(String method, String pattern) {

        private String line() {
            return this.method + ' ' + this.pattern;
        }
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
