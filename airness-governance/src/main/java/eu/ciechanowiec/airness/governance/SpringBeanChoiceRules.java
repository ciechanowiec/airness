package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Finds an injection point that relies on an implicit choice among local unconditional beans.
 *
 * <p>The full Spring bean graph includes dependency artifacts and conditions evaluated only at startup,
 * so reconstructing it from source would manufacture certainty. This rule asks the narrower question it
 * can answer: two production {@code @Bean} methods in this module return the exact same declared type,
 * neither is conditional, and a direct injection of that exact type names neither candidate.
 */
@UtilityClass
final class SpringBeanChoiceRules {

    private static final Pattern BEAN = Pattern.compile("@Bean\\b");
    private static final Pattern TYPE = Pattern.compile("([A-Za-z_$][\\w.$]*(?:\\s*<[^<>]+>)?)\\s*$");
    private static final Pattern CONDITIONAL = Pattern.compile("@(?:Profile|Conditional\\w*)\\b");
    private static final Pattern PRIMARY = Pattern.compile("@Primary\\b");
    private static final Pattern QUALIFIER = Pattern.compile(
        "@Qualifier\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\""
    );
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern STEREOTYPE = Pattern.compile(
        "@(?:Component|Service|Repository|Controller|RestController|Configuration|SpringBootApplication)\\b"
    );
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+)\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern WRAPPER = Pattern.compile(
        "\\b(?:Collection|List|Set|Map|Stream|Optional|Provider|ObjectProvider)\\s*<|\\[\\s*]"
    );
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+(?:\\s*\\([^()]*\\))?\\s*");
    private static final String DOT = ".";

    /**
     * Direct injections whose local candidate choice is not declared.
     *
     * @param types module sources already read
     * @return one offence per injection point
     */
    static List<String> implicitChoices(SpringTypes types) {
        Map<String, List<Candidate>> candidates = candidates(types).stream()
            .collect(Collectors.groupingBy(Candidate::type, LinkedHashMap::new, Collectors.toList()));
        return candidates.values().stream()
            .filter(group -> group.size() > 1)
            .filter(group -> group.stream().filter(Candidate::primary).count() != 1)
            .flatMap(group -> injections(types, group))
            .toList();
    }

    private static List<Candidate> candidates(SpringTypes types) {
        return types.all().stream()
            .filter(SpringTypes.Declared::production)
            .filter(type -> !CONDITIONAL.matcher(type.code()).find())
            .flatMap(SpringBeanChoiceRules::beans)
            .toList();
    }

    private static Stream<Candidate> beans(SpringTypes.Declared source) {
        String code = source.code();
        String read = JavaCode.withoutComments(source.text());
        return SpringMembers.annotated(code, BEAN).stream()
            .flatMap(member -> candidate(source, code, read, member).stream());
    }

    private static Optional<Candidate> candidate(
        SpringTypes.Declared source, String code, String read, SpringMembers.Member member
    ) {
        int marker = code.lastIndexOf("@Bean", member.declaration());
        String prefix = code.substring(marker, member.declaration());
        Matcher declared = TYPE.matcher(prefix);
        if (!declared.find()) {
            return Optional.empty();
        }
        String written = read.substring(marker, member.declaration());
        Set<String> names = names(written, member.name());
        QUALIFIER.matcher(written).results().map(qualifier -> qualifier.group(1)).forEach(names::add);
        return Optional.of(
            new Candidate(
                resolved(source, declared.group(1)), Set.copyOf(names), PRIMARY.matcher(prefix).find()
            )
        );
    }

    private static Set<String> names(String prefix, String fallback) {
        Set<String> names = new LinkedHashSet<>();
        int marker = prefix.indexOf("@Bean");
        int opening = prefix.indexOf('(', marker);
        if (opening >= 0) {
            int closing = SpringMembers.closing(prefix, opening);
            QUOTED.matcher(prefix.substring(opening + 1, closing)).results()
                .map(name -> name.group(1))
                .forEach(names::add);
        }
        if (names.isEmpty()) {
            names.add(fallback);
        }
        return names;
    }

    private static Stream<String> injections(SpringTypes types, List<Candidate> candidates) {
        String type = candidates.getFirst().type();
        Set<String> names = candidates.stream()
            .flatMap(candidate -> candidate.names().stream())
            .collect(Collectors.toUnmodifiableSet());
        return types.all().stream()
            .filter(SpringTypes.Declared::production)
            .filter(source -> STEREOTYPE.matcher(source.code()).find())
            .filter(source -> !CONDITIONAL.matcher(source.code()).find())
            .flatMap(source -> parameters(source).stream())
            .filter(parameter -> !WRAPPER.matcher(parameter.text()).find())
            .filter(parameter -> type.equals(resolved(parameter.source(), parameter.type())))
            .filter(parameter -> !qualified(parameter.parameter().text(), names))
            .map(
                parameter -> offence(
                    parameter.source(), parameter.parameter().offset(), type, names
                )
            );
    }

    private static List<Injection> parameters(SpringTypes.Declared source) {
        String code = source.code();
        List<Injection> found = new ArrayList<>();
        Pattern constructor = Pattern.compile("\\b" + Pattern.quote(source.name()) + "\\s*\\(");
        constructor.matcher(code).results()
            .map(
                declaration -> new SpringParameters.Range(
                    declaration.end() - 1, SpringMembers.closing(code, declaration.end() - 1)
                )
            )
            .flatMap(range -> SpringParameters.in(source.text(), code, range).stream())
            .map(parameter -> injection(source, parameter))
            .forEach(found::add);
        SpringMembers.annotated(code, BEAN).stream()
            .map(member -> SpringParameters.after(code, member.declaration()))
            .flatMap(Optional::stream)
            .flatMap(range -> SpringParameters.in(source.text(), code, range).stream())
            .map(parameter -> injection(source, parameter))
            .forEach(found::add);
        return List.copyOf(found);
    }

    private static Injection injection(
        SpringTypes.Declared source, SpringParameters.Parameter parameter
    ) {
        String withoutAnnotations = ANNOTATION.matcher(parameter.text()).replaceAll(" ")
            .replaceFirst("^\\s*final\\s+", "");
        int name = withoutAnnotations.lastIndexOf(parameter.name());
        return new Injection(source, parameter, withoutAnnotations.substring(0, name).trim());
    }

    private static boolean qualified(String parameter, Collection<String> candidates) {
        return QUALIFIER.matcher(parameter).results()
            .map(qualifier -> qualifier.group(1))
            .anyMatch(candidates::contains);
    }

    private static String resolved(SpringTypes.Declared source, String written) {
        String compact = written.replaceAll("\\s+", "");
        int generic = compact.indexOf('<');
        String base = generic < 0 ? compact : compact.substring(0, generic);
        String suffix = generic < 0 ? "" : compact.substring(generic);
        if (base.contains(DOT)) {
            return base + suffix;
        }
        Optional<String> imported = IMPORT.matcher(source.code()).results()
            .map(found -> found.group(1))
            .filter(name -> name.endsWith('.' + base))
            .findFirst();
        String qualified = imported.orElseGet(() -> packageName(source) + base);
        return qualified + suffix;
    }

    private static String packageName(SpringTypes.Declared source) {
        return PACKAGE.matcher(source.code()).results()
            .findFirst()
            .map(found -> found.group(1) + '.')
            .orElse("");
    }

    private static String offence(
        SpringTypes.Declared source, int at, String type, Collection<String> names
    ) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), at)
            + ": the injection takes " + type + " from candidates " + names
            + " without a matching @Qualifier or one @Primary";
    }

    private record Candidate(String type, Set<String> names, boolean primary) {
    }

    private record Injection(
        SpringTypes.Declared source, SpringParameters.Parameter parameter, String type
    ) {

        String text() {
            return this.parameter.text();
        }
    }
}
