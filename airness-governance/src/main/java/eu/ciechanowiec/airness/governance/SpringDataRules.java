package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports redundant stereotypes on interfaces Spring Data already discovers from their hierarchy.
 */
@UtilityClass
final class SpringDataRules {

    private static final String STEREOTYPE = "org.springframework.stereotype.Repository";
    private static final String INTERFACE = "interface ";
    private static final String ANNOTATION = "@";
    private static final char OPENING = '(';
    private static final Pattern REPOSITORY_ANNOTATION = Pattern.compile(
        "@(?:org\\.springframework\\.stereotype\\.)?Repository\\b"
    );
    private static final Pattern STEREOTYPE_IMPORT = Pattern.compile(
        "\\bimport\\s+org\\.springframework\\.stereotype\\.Repository\\s*;"
    );
    private static final Pattern SPRING_DATA_IMPORT = Pattern.compile(
        "\\bimport\\s+org\\.springframework\\.data\\.[\\w.]+\\.(\\w*Repository)\\s*;"
    );
    private static final Pattern QUALIFIED_REPOSITORY = Pattern.compile(
        "\\borg\\.springframework\\.data\\.[\\w.]+\\.\\w*Repository\\b"
    );
    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+([^{;]+)");
    private static final Pattern NAME = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern PACKAGE = Pattern.compile(
        "\\bpackage\\s+([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*;"
    );

    /**
     * Spring Data repository interfaces carrying an unnamed stereotype annotation.
     *
     * @param types the module already read
     * @return one offence per redundant stereotype
     */
    static List<String> redundantStereotypes(SpringTypes types) {
        Map<String, List<SpringTypes.Declared>> declarations = types.all().stream()
            .collect(Collectors.groupingBy(SpringTypes.Declared::name));
        return types.all().stream()
            .filter(type -> repository(type, declarations, Set.of()))
            .flatMap(SpringDataRules::stereotype)
            .toList();
    }

    private static boolean repository(
        SpringTypes.Declared type, Map<String, List<SpringTypes.Declared>> declarations,
        Set<Path> visited
    ) {
        return candidate(type, visited)
            && hierarchy(type).map(
                extended -> direct(type, extended) || inherited(type, declarations, visited, extended)
            ).orElse(false);
    }

    private static boolean candidate(SpringTypes.Declared type, Set<Path> visited) {
        return !visited.contains(type.source()) && type.code().contains(INTERFACE + type.name());
    }

    private static Optional<String> hierarchy(SpringTypes.Declared type) {
        return EXTENDS.matcher(type.code()).results().findFirst().map(found -> found.group(1));
    }

    private static boolean direct(SpringTypes.Declared type, String hierarchy) {
        Set<String> imports = SPRING_DATA_IMPORT.matcher(type.code()).results()
            .map(found -> found.group(1))
            .collect(Collectors.toSet());
        return QUALIFIED_REPOSITORY.matcher(hierarchy).find()
            || imports.stream().anyMatch(hierarchy::contains);
    }

    private static boolean inherited(
        SpringTypes.Declared type, Map<String, List<SpringTypes.Declared>> declarations,
        Set<Path> visited, String hierarchy
    ) {
        Set<Path> next = Stream.concat(visited.stream(), Stream.of(type.source()))
            .collect(Collectors.toUnmodifiableSet());
        return NAME.matcher(hierarchy).results()
            .map(found -> declarations.getOrDefault(found.group(1), List.of()))
            .flatMap(List::stream)
            .filter(parent -> visible(type, parent, hierarchy))
            .anyMatch(parent -> repository(parent, declarations, next));
    }

    private static boolean visible(
        SpringTypes.Declared source, SpringTypes.Declared candidate, String hierarchy
    ) {
        String qualified = qualified(candidate);
        String candidatePackage = packageName(candidate);
        boolean local = packageName(source).equals(candidatePackage);
        boolean imported = source.code().contains("import " + qualified + ';')
            || source.code().contains("import " + candidatePackage + ".*;");
        return local || imported || hierarchy.contains(qualified);
    }

    private static String qualified(SpringTypes.Declared type) {
        String packageName = packageName(type);
        return packageName.isEmpty() ? type.name() : packageName + '.' + type.name();
    }

    private static String packageName(SpringTypes.Declared type) {
        return PACKAGE.matcher(type.code()).results()
            .findFirst()
            .map(found -> found.group(1))
            .orElse("");
    }

    private static Stream<String> stereotype(SpringTypes.Declared type) {
        String readable = type.quoted();
        if (
            !readable.contains(ANNOTATION + STEREOTYPE)
                && !STEREOTYPE_IMPORT.matcher(type.code()).find()
        ) {
            return Stream.of();
        }
        return REPOSITORY_ANNOTATION.matcher(readable).results()
            .filter(marker -> unnamed(readable, marker.end()))
            .limit(1)
            .map(marker -> offence(type, marker));
    }

    private static String offence(SpringTypes.Declared type, MatchResult marker) {
        return type.source() + ": line " + JavaCode.lineOf(type.text(), marker.start())
            + ": this Spring Data repository interface is discovered from its repository"
            + " hierarchy; remove the redundant @Repository";
    }

    private static boolean unnamed(String source, int after) {
        int next = after;
        while (next < source.length() && Character.isWhitespace(source.charAt(next))) {
            next += 1;
        }
        if (next >= source.length() || source.charAt(next) != OPENING) {
            return true;
        }
        int closing = SpringMembers.closing(JavaCode.blanked(source), next);
        return source.substring(next + 1, closing).isBlank();
    }
}
