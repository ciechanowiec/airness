package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads what a query constructs and answers whether the type it names can take it.
 *
 * <p>A constructor expression inside a query is a call written as text. Nothing compiles it, so a
 * record given four components and constructed with three parses, passes every analyzer and fails when
 * the query is first run, which for a report nobody reads until the end of a month is a long way from
 * the edit that caused it. It is the same shape as a link expression reaching for a bean: correct
 * everywhere a tool looks, wrong the first time it is read by the thing that matters.
 *
 * <p>The expression is looked for inside string literals and text blocks alone, which is what makes the
 * scan exact rather than approximate: a fully-qualified name written in code is refused by the rule set
 * elsewhere, so the only place one occurs is a query.
 *
 * <p>Two limits, both deliberate. Only a record is measured, because a record states its components in
 * one place and a class states its constructors in several, and the projections these expressions build
 * are records wherever this rule set is followed. And only a type the module declares itself is
 * measured, because a name this module cannot see may be declared by a module beside it, and a rule
 * that reported one would report a project for being more than one module.
 */
@UtilityClass
final class SpringQueryRules {

    private static final Pattern CONSTRUCTED = Pattern.compile("\\bnew\\s+([\\w.]+\\.[A-Z]\\w*)\\s*\\(");
    private static final Pattern HEADER = Pattern.compile("\\brecord\\s+(\\w+)\\s*\\(");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    /*
     * What a comma at the top level of an argument list is not. A nested call, a type argument and a
     * quoted string each hold commas of their own, and each is blanked from the inside out until the
     * text holds none of them, which leaves the separators the caller is counting.
     */
    private static final Pattern NESTED = Pattern.compile("\\([^()]*\\)|<[^<>]*>|'[^']*'");
    private static final char QUOTE = '"';
    private static final char COMMA = ',';

    /**
     * How many components each record of the given sources declares, by the name a query would write.
     *
     * @param declared the types the module declares
     * @return the component count of every record, under its fully qualified name
     */
    static Map<String, Integer> records(Collection<SpringTypes.Declared> declared) {
        return declared.stream()
            .flatMap(type -> header(type).stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    /**
     * Every constructor expression in the given source that hands a record of the module the wrong
     * number of arguments.
     *
     * @param declared the source to read
     * @param records  the component count of every record the module declares
     * @return one offence per expression that cannot be built, in the order they are written
     */
    static List<String> mismatchedConstructors(SpringTypes.Declared declared, Map<String, Integer> records) {
        return constructed(declared.text())
            .flatMap(built -> offence(built, records).stream())
            .toList();
    }

    private static Optional<Map.Entry<String, Integer>> header(SpringTypes.Declared declared) {
        return HEADER.matcher(declared.code()).results()
            .filter(header -> declared.name().equals(header.group(1)))
            .findFirst()
            .map(header -> Map.entry(qualified(declared), arguments(declared.code(), header.end() - 1)));
    }

    private static String qualified(SpringTypes.Declared declared) {
        return PACKAGE.matcher(declared.code()).results()
            .findFirst()
            .map(found -> found.group(1) + '.' + declared.name())
            .orElseGet(declared::name);
    }

    // Only what a string literal or a text block holds. A comment is not read, because an expression
    // written in one builds nothing, and code is not read, because a fully qualified name written there
    // is refused by another rule before this one could reach it.
    private static Stream<Constructed> constructed(CharSequence text) {
        return JavaCode.TOKEN.matcher(text).results()
            .filter(token -> token.group().charAt(0) == QUOTE)
            .flatMap(token -> within(text, token));
    }

    private static Stream<Constructed> within(CharSequence text, MatchResult token) {
        String quoted = token.group();
        return CONSTRUCTED.matcher(quoted).results()
            .map(
                built -> new Constructed(
                    built.group(1),
                    arguments(quoted, built.end() - 1),
                    JavaCode.lineOf(text, token.start() + built.start())
                )
            );
    }

    private static Optional<String> offence(Constructed built, Map<String, Integer> records) {
        return Optional.ofNullable(records.get(built.type()))
            .filter(components -> components != built.arguments())
            .map(components -> said(built, components));
    }

    private static String said(Constructed built, int components) {
        return "line " + built.line() + ": the query constructs " + built.type() + " with "
            + built.arguments() + " argument(s), and it takes " + components;
    }

    // The arguments of the parenthesis opened at the given offset, which is also how the components of a
    // record header are counted, the two being one list read in two places.
    private static int arguments(String text, int opening) {
        String inside = text.substring(opening + 1, SpringMembers.closing(text, opening));
        String flat = flattened(inside);
        return flat.isBlank() ? 0 : (int) flat.chars().filter(character -> character == COMMA).count() + 1;
    }

    private static String flattened(String inside) {
        String reduced = NESTED.matcher(inside).replaceAll(" ");
        return reduced.equals(inside) ? reduced : flattened(reduced);
    }

    /**
     * One constructor expression, by what it names and what it hands over.
     *
     * @param type      the fully qualified type the expression constructs
     * @param arguments how many arguments it hands over
     * @param line      the one-based line the expression is written on
     */
    private record Constructed(String type, int arguments, int line) {
    }
}
