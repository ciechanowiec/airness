package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Compares the names a repository query binds with the parameters its method declares.
 *
 * <p>A query is text rather than Java, so neither the compiler nor a rename knows that {@code :owner}
 * and a method parameter are one binding. The annotation on the parameter is the declaration that joins
 * the two. This reader requires that declaration and compares both sides, while passing over the
 * infrastructure parameters Spring Data consumes before the query is built.
 */
@UtilityClass
final class SpringQueryParameterRules {

    private static final Pattern QUERY = Pattern.compile("@(?:Query|NativeQuery)\\s*\\(");
    private static final Pattern PARAM = Pattern.compile(
        "@Param\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\""
    );
    private static final Pattern POSITIONAL = Pattern.compile(
        "\\?[0-9]+|[?:]#\\{[^}]*\\[[0-9]+][^}]*}|#args\\s*\\[[0-9]+]"
    );
    private static final Pattern NAMED = Pattern.compile("(?<!:):([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern SPEL = Pattern.compile("[?:]#\\{([^}]*)}");
    private static final Pattern REFERENCE = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern LITERAL = Pattern.compile(
        "(?s)\"\"\"(?:\\\\.|[^\"\\\\]++|\"{1,2}+(?!\"))*+\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\""
    );
    private static final Pattern QUERY_BLOCK_COMMENT = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern QUERY_LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern QUERY_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern NON_LINE_BREAK = Pattern.compile("[^\\n]");
    private static final Pattern INFRASTRUCTURE = Pattern.compile(
        "\\b(?:Pageable|Sort|Limit|ScrollPosition)\\b|\\bClass\\s*<"
    );
    private static final Set<String> SUPPLIED = Set.of(
        "entityName", "principal", "root", "this", "args", "escape", "escapeCharacter"
    );
    private static final String VALUE = "value";
    private static final String COUNT_QUERY = "countQuery";

    /**
     * Positional bindings used by inline repository queries.
     *
     * @param source Java source as written
     * @return one offence per query that binds by position
     */
    static List<String> positional(CharSequence source) {
        return queries(source).stream()
            .flatMap(query -> query.texts().stream())
            .flatMap(
                text -> POSITIONAL.matcher(plain(text.value())).results()
                    .map(found -> offence(source, text.offset() + found.start(), "binds a parameter by position"))
            )
            .toList();
    }

    /**
     * Inline queries whose method parameters and written placeholders disagree.
     *
     * @param source Java source as written
     * @return one offence per mismatched query method
     */
    static List<String> mismatched(CharSequence source) {
        return queries(source).stream().flatMap(query -> mismatch(source, query).stream()).toList();
    }

    private static Optional<String> mismatch(CharSequence source, DeclaredQuery query) {
        Parameters parameters = parameters(query.parameters());
        Optional<String> declarations = parameters.problems().isEmpty()
            ? Optional.empty()
            : Optional.of(offence(source, query.offset(), String.join("; ", parameters.problems())));
        return declarations
            .or(() -> mainMismatch(source, query, parameters))
            .or(() -> countMismatch(source, query, parameters));
    }

    private static Optional<String> mainMismatch(
        CharSequence source, DeclaredQuery query, Parameters parameters
    ) {
        Set<String> bound = names(query.main().value(), parameters.infrastructure());
        return bound.equals(parameters.named())
            ? Optional.empty()
            : Optional.of(
                offence(
                    source, query.offset(),
                    "declares @Param names " + parameters.named() + " but its query binds " + bound
                )
            );
    }

    private static Optional<String> countMismatch(
        CharSequence source, DeclaredQuery query, Parameters parameters
    ) {
        return query.count().stream()
            .map(text -> names(text.value(), parameters.infrastructure()))
            .filter(counted -> !counted.equals(parameters.named()))
            .findFirst()
            .map(
                counted -> offence(
                    source, query.offset(),
                    "declares @Param names " + parameters.named() + " but its countQuery binds " + counted
                )
            );
    }

    private static Parameters parameters(Collection<SpringParameters.Parameter> declared) {
        Set<String> infrastructure = declared.stream()
            .filter(parameter -> INFRASTRUCTURE.matcher(parameter.text()).find())
            .map(SpringParameters.Parameter::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SpringParameters.Parameter> bindable = declared.stream()
            .filter(parameter -> !infrastructure.contains(parameter.name()))
            .toList();
        Map<String, Long> counts = bindable.stream()
            .flatMap(parameter -> PARAM.matcher(parameter.text()).results().map(name -> name.group(1)))
            .collect(
                Collectors.groupingBy(
                    name -> name, LinkedHashMap::new, Collectors.counting()
                )
            );
        Set<String> named = new LinkedHashSet<>(counts.keySet());
        List<String> problems = new ArrayList<>();
        bindable.stream()
            .filter(parameter -> !PARAM.matcher(parameter.text()).find())
            .map(parameter -> parameter.name() + " has no @Param")
            .forEach(problems::add);
        counts.entrySet().stream()
            .filter(entry -> entry.getKey().isBlank() || entry.getValue() > 1)
            .map(entry -> entry.getKey().isBlank() ? "an @Param name is empty" : entry.getKey() + " is duplicated")
            .forEach(problems::add);
        return new Parameters(Set.copyOf(named), Set.copyOf(infrastructure), List.copyOf(problems));
    }

    private static Set<String> names(String query, Collection<String> infrastructure) {
        String readable = plain(query);
        Set<String> found = NAMED.matcher(readable).results()
            .map(name -> name.group(1))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        SPEL.matcher(readable).results()
            .flatMap(expression -> REFERENCE.matcher(expression.group(1)).results())
            .map(reference -> reference.group(1))
            .filter(reference -> !SUPPLIED.contains(reference))
            .filter(reference -> !infrastructure.contains(reference))
            .forEach(found::add);
        return Set.copyOf(found);
    }

    private static List<DeclaredQuery> queries(CharSequence source) {
        String code = JavaCode.blanked(source);
        String read = source.toString();
        return QUERY.matcher(code).results()
            .map(marker -> declared(read, code, marker))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<DeclaredQuery> declared(String read, String code, MatchResult marker) {
        int opens = marker.end() - 1;
        int closes = SpringMembers.closing(code, opens);
        QueryTexts texts = texts(read, opens, closes);
        return texts.main().flatMap(
            main -> SpringParameters.after(code, closes).map(
                range -> new DeclaredQuery(
                    marker.start(), main, texts.count(), List.copyOf(texts.all()),
                    SpringParameters.in(read, code, range)
                )
            )
        );
    }

    private static QueryTexts texts(String source, int opens, int closes) {
        String annotation = source.substring(opens + 1, closes);
        List<NamedText> written = LITERAL.matcher(annotation).results()
            .map(literal -> namedText(annotation, opens, literal))
            .toList();
        Optional<QueryText> main = written.stream()
            .filter(text -> text.member().isEmpty() || VALUE.equals(text.member()))
            .map(NamedText::text)
            .findFirst();
        Optional<QueryText> count = written.stream()
            .filter(text -> COUNT_QUERY.equals(text.member()))
            .map(NamedText::text)
            .findFirst();
        List<QueryText> all = written.stream()
            .filter(text -> text.member().isEmpty() || VALUE.equals(text.member()) || COUNT_QUERY.equals(text.member()))
            .map(NamedText::text)
            .toList();
        return new QueryTexts(main, count, all);
    }

    private static NamedText namedText(String annotation, int opens, MatchResult literal) {
        return new NamedText(
            member(annotation, literal.start()),
            new QueryText(
                unquoted(literal.group()),
                opens + 1 + literal.start() + delimiter(literal.group())
            )
        );
    }

    private static String member(String annotation, int before) {
        int comma = annotation.lastIndexOf(',', before - 1);
        String prefix = annotation.substring(comma + 1, before).trim();
        int equals = prefix.lastIndexOf('=');
        return equals < 0 ? "" : prefix.substring(0, equals).trim();
    }

    private static int delimiter(String literal) {
        return literal.startsWith("\"\"\"") ? 3 : 1;
    }

    private static String unquoted(String literal) {
        int delimiter = delimiter(literal);
        return literal.substring(delimiter, literal.length() - delimiter);
    }

    private static String plain(String query) {
        String withoutBlocks = masked(QUERY_BLOCK_COMMENT, query);
        String withoutLines = masked(QUERY_LINE_COMMENT, withoutBlocks);
        return masked(QUERY_LITERAL, withoutLines);
    }

    private static String masked(Pattern pattern, String text) {
        return pattern.matcher(text).replaceAll(found -> NON_LINE_BREAK.matcher(found.group()).replaceAll(" "));
    }

    private static String offence(CharSequence source, int at, String problem) {
        return "line " + JavaCode.lineOf(source, at) + ": the repository query " + problem;
    }

    private record QueryText(String value, int offset) {
    }

    private record NamedText(String member, QueryText text) {
    }

    private record QueryTexts(Optional<QueryText> main, Optional<QueryText> count, List<QueryText> all) {
    }

    private record DeclaredQuery(
        int offset,
        QueryText main,
        Optional<QueryText> count,
        List<QueryText> texts,
        List<SpringParameters.Parameter> parameters
    ) {
    }

    private record Parameters(Set<String> named, Set<String> infrastructure, List<String> problems) {
    }
}
