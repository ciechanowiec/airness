package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The names a handler takes out of a request path, read against the path it is mapped to.
 *
 * <p>A path variable is bound by name to a placeholder of the mapping, and both are strings. The
 * container checks neither at startup: a handler naming a variable its mapping never declares is
 * registered, answers the route, and fails on the first request with a missing path variable, which
 * the caller sees as a server error and the log as a stack trace from inside the framework.
 *
 * <p>The mapping is the class-level path joined with the method-level one, and a name is written
 * plainly as a literal or as a constant of the same source. A path or a name the source does not
 * state, such as a constant declared in another type, leaves the question unanswerable and is passed
 * over rather than guessed at. A mapping inherited from an interface is not read either, because the
 * placeholder it declares is declared there rather than in the file in front of the rule.
 */
@UtilityClass
final class SpringWebRules {

    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface)\\s+\\w+");
    private static final Pattern CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(");
    private static final Pattern METHOD_MAPPING = Pattern.compile(
        "@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\b(\\s*\\()?"
    );
    // An array of paths carries the braces of its own templates, so one level of nesting is read. The
    // array is read in runs, so however many paths it lists costs the scan no depth.
    private static final Pattern MEMBER = Pattern.compile(
        "\\b(?:path|value)\\s*=\\s*(\\{(?:[^{}]++|\\{[^{}]*+})*+}|\"[^\"]*\"|[A-Z][A-Z0-9_]*)"
    );
    private static final Pattern WRITTEN = Pattern.compile("\"[^\"]*\"|\\b[A-Z][A-Z0-9_]*\\b");
    private static final Pattern TEMPLATE = Pattern.compile("\\{(\\w+)(?::[^}]*)?}");
    private static final Pattern VARIABLE = Pattern.compile(
        "@PathVariable\\s*\\(\\s*(?:(?:name|value)\\s*=\\s*)?(\"[^\"]*\"|[A-Z][A-Z0-9_]*)"
    );
    private static final char ASSIGNMENT = '=';

    /**
     * Every path variable of a handler that its mapping declares no placeholder for.
     *
     * @param source the source as written
     * @return one offence per unbound variable, by line
     */
    static List<String> unboundPathVariables(CharSequence source) {
        String code = JavaCode.blanked(source);
        String read = JavaCode.withoutComments(source);
        Readings reading = new Readings(source, code, read, SpringSourceRules.stringConstants(read));
        int type = TYPE.matcher(code).results().findFirst().map(MatchResult::start).orElse(code.length());
        Optional<Set<String>> shared = shared(reading, type);
        return shared.stream()
            .flatMap(
                base -> METHOD_MAPPING.matcher(code).results()
                    .filter(mapping -> mapping.start() > type)
                    .flatMap(mapping -> unbound(reading, base, mapping))
            )
            .toList();
    }

    private static Optional<Set<String>> shared(Readings reading, int type) {
        List<Optional<Set<String>>> declared = CLASS_MAPPING.matcher(reading.code()).results()
            .filter(mapping -> mapping.start() < type)
            .map(mapping -> templates(reading, mapping.end() - 1))
            .toList();
        return declared.stream().anyMatch(Optional::isEmpty)
            ? Optional.empty()
            : Optional.of(
                declared.stream().flatMap(Optional::stream).flatMap(Set::stream).collect(Collectors.toSet())
            );
    }

    private static Stream<String> unbound(Readings reading, Set<String> base, MatchResult mapping) {
        boolean parenthesised = mapping.start(1) >= 0;
        int closes = parenthesised ? SpringMembers.closing(reading.code(), mapping.end() - 1) : mapping.end();
        Optional<Set<String>> own = parenthesised
            ? templates(reading, mapping.end() - 1)
            : Optional.of(Set.of());
        return own.stream().flatMap(
            declared -> SpringParameters.after(reading.code(), closes).stream().flatMap(
                range -> variables(reading, range, joined(base, declared))
            )
        );
    }

    private static Set<String> joined(Set<String> base, Set<String> own) {
        Set<String> all = new TreeSet<>(base);
        all.addAll(own);
        return all;
    }

    /*
     * The path is the one unnamed argument, or the path or value member when the arguments are named.
     * An annotation naming members but no path maps the class or method to nothing of its own.
     */
    private static Optional<Set<String>> templates(Readings reading, int opens) {
        int closes = SpringMembers.closing(reading.code(), opens);
        String arguments = reading.read().substring(opens + 1, closes);
        boolean named = reading.code().substring(opens + 1, closes).indexOf(ASSIGNMENT) >= 0;
        String expression = named
            ? MEMBER.matcher(arguments).results().findFirst().map(found -> found.group(1)).orElse("")
            : arguments;
        List<Optional<String>> paths = WRITTEN.matcher(expression).results()
            .map(written -> reading.resolved(written.group()))
            .toList();
        return paths.stream().anyMatch(Optional::isEmpty)
            ? Optional.empty()
            : Optional.of(
                paths.stream()
                    .flatMap(Optional::stream)
                    .flatMap(path -> TEMPLATE.matcher(path).results().map(found -> found.group(1)))
                    .collect(Collectors.toSet())
            );
    }

    private static Stream<String> variables(
        Readings reading, SpringParameters.Range range, Set<String> templates
    ) {
        String parameters = reading.read().substring(range.opens() + 1, range.closes());
        return VARIABLE.matcher(parameters).results()
            .flatMap(
                variable -> reading.resolved(variable.group(1)).stream()
                    .filter(name -> !templates.contains(name))
                    .map(name -> offence(reading.source(), range.opens() + 1 + variable.start(), name, templates))
            );
    }

    private static String offence(CharSequence source, int at, String name, Set<String> templates) {
        return "line " + JavaCode.lineOf(source, at) + ": @PathVariable binds " + name
            + ", and the mapping declares " + (templates.isEmpty() ? "no placeholder" : "only " + templates)
            + ", so the handler answers the route and fails on the first request with a missing path variable";
    }

    /**
     * One source in the three readings a rule needs of it.
     *
     * @param source    the source as written, which a line number is counted over
     * @param code      the source with comments and literals blanked, which structure is read from
     * @param read      the source with comments blanked and literals kept, which values are read from
     * @param constants the string constants the source declares
     */
    private record Readings(CharSequence source, String code, String read, Map<String, String> constants) {

        Optional<String> resolved(String written) {
            return SpringSourceRules.resolvedString(this.constants, written);
        }
    }
}
