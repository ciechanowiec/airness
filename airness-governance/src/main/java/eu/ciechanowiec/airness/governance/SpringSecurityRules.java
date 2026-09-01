package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The values a method-security expression reads out of the call it guards.
 *
 * <p>An authorization expression is a string, and the only part of one that reaches back into the
 * method is a parameter reference. Nothing compiles that reference. The engine resolves it by asking
 * what the parameters of the method are called. Airness keeps those names in the class file, but a
 * source parameter rename then changes the value the expression addresses without changing the string.
 * Spring asks for an explicit naming annotation before reflection, so the annotation makes that binding
 * a declaration that an ordinary Java refactor cannot silently rewrite.
 *
 * <p>Resolving to nothing is not an error the engine reports. The reference becomes null, every
 * comparison against it is false, and the expression goes on deciding, in a way that no longer follows
 * what it says. A guard written to admit the owner of a record admits nobody, and one written around a
 * negation admits everybody. Both look right in review, both compile, and neither is a line in any log.
 *
 * <p>The repair is to state the name where the runtime reads it, which is what the parameter annotation
 * is for. Retained compiler metadata remains the reflective fallback for framework features that have
 * no explicit name, while a security decision keeps its binding written beside the parameter it means.
 *
 * <p>A reference the engine supplies itself is not a parameter and is left alone. A guard written on a
 * declaration that carries no body is read like any other, because an interface is an ordinary place to
 * put one and the parameter it names is declared there rather than in whatever implements it.
 */
@UtilityClass
final class SpringSecurityRules {

    // The four annotations that carry an expression. The two that name roles without one carry no
    // reference to resolve and are not read here.
    private static final Pattern GUARDED = Pattern.compile(
        "@(?:PreAuthorize|PostAuthorize|PreFilter|PostFilter)\\s*\\("
    );

    private static final Pattern REFERENCE = Pattern.compile("#(\\w+)");

    // The two annotations Spring reads a parameter name out of, the second being the one Spring Data
    // contributes and which the discoverer accepts wherever Spring Data is on the classpath.
    private static final Pattern NAMED = Pattern.compile("@(?:P|Param)\\s*\\(\\s*\"([^\"]*)\"");

    private static final Pattern MEMBER = Pattern.compile("\\b(\\w+)\\s*\\(");

    // What the engine puts in scope itself, which no parameter has to answer for.
    private static final Set<String> SUPPLIED = Set.of("root", "this");

    private static final char ANNOTATION = '@';

    /**
     * Every reference a security expression makes to a parameter that is not named for the runtime.
     *
     * @param source the source as written
     * @return one offence per unresolvable reference, by line
     */
    static List<String> unnamedSecurityParameters(CharSequence source) {
        String code = JavaCode.blanked(source);
        String read = JavaCode.withoutComments(source);
        return GUARDED.matcher(code).results()
            .flatMap(guard -> unresolved(code, read, guard))
            .toList();
    }

    private static Stream<String> unresolved(String code, String read, MatchResult guard) {
        int opens = guard.end() - 1;
        int closes = SpringMembers.closing(code, opens);
        Optional<Set<String>> named = names(code, read, closes);
        return named.stream().flatMap(
            answered -> references(read.substring(opens + 1, closes))
                .stream()
                .filter(reference -> !answered.contains(reference))
                .map(reference -> offence(read, guard.start(), reference))
        );
    }

    /**
     * Every name the parameter list of the guarded method states for the runtime.
     *
     * @param code the source with comments and literals blanked, which the structure is read from
     * @param read the source with its literals kept, which the names are read from
     * @param from the offset the annotation closes at
     * @return the stated names, and nothing when the guarded method could not be found
     */
    private static Optional<Set<String>> names(String code, String read, int from) {
        return list(code, from).map(
            taken -> NAMED.matcher(read.substring(taken.opens() + 1, taken.closes()))
                .results()
                .map(name -> name.group(1))
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    // The parameter list of the method the annotation guards, which opens at the first name followed by
    // a parenthesis that is not itself an annotation. Another annotation may sit between the two, and
    // one of those reads as a name and a parenthesis like any other.
    private static Optional<Range> list(String code, int from) {
        return MEMBER.matcher(code).results()
            .filter(match -> match.start() >= from)
            .filter(match -> code.charAt(match.start(1) - 1) != ANNOTATION)
            .findFirst()
            .map(match -> new Range(match.end() - 1, SpringMembers.closing(code, match.end() - 1)));
    }

    private static List<String> references(String expression) {
        return REFERENCE.matcher(expression)
            .results()
            .map(reference -> reference.group(1))
            .filter(reference -> !SUPPLIED.contains(reference))
            .distinct()
            .toList();
    }

    private static String offence(CharSequence source, int at, String reference) {
        return "line " + JavaCode.lineOf(source, at) + ": the security expression reads #" + reference
            + ", and the guarded method states no explicit parameter binding of that name."
            + " Retained compiler metadata would make a Java parameter rename silently retarget this"
            + " unchanged security expression."
            + " Name the parameter it means with @P(\"" + reference + "\")";
    }

    /**
     * The parenthesis pair a parameter list occupies.
     *
     * @param opens  the offset the list opens at
     * @param closes the offset it closes at
     */
    private record Range(int opens, int closes) {
    }
}
