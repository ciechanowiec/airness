package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Turns the license an npm package declares into the alternatives the license policy judges.
 *
 * <p>The policy admits a package when one declared alternative is admitted, which is how a Maven artifact
 * declaring two licenses is read. An npm package declares one SPDX expression instead, so the expression is
 * written out as its alternatives: {@code (MIT OR Apache-2.0)} is two, and {@code (MIT OR ISC) AND BSD-3-Clause}
 * is {@code MIT AND BSD-3-Clause} or {@code ISC AND BSD-3-Clause}, each of which the policy admits only when it
 * admits every license joined by {@code AND}. A declaration that is no SPDX expression, such as
 * {@code SEE LICENSE IN LICENSE.md}, stays one alternative exactly as written, which no allowlist admits.
 */
@UtilityClass
public final class LicenseExpression {

    /**
     * What joins the licenses of one alternative, all of which the policy must admit.
     */
    public static final String CONJUNCTION = " AND ";

    private static final Pattern TOKENS = Pattern.compile("[()]|[^\\s()]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+-]*");
    private static final String OR = "OR";
    private static final String AND = "AND";
    private static final String WITH = "WITH";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";

    /**
     * The alternatives a declaration offers.
     *
     * @param declared the declared license, empty when the package declares none
     * @return the alternatives, each a license or licenses joined by {@link #CONJUNCTION}, and none when nothing
     *         is declared
     */
    public static List<String> alternatives(String declared) {
        return alternatives(declared, UnaryOperator.identity());
    }

    /**
     * The alternatives a declaration offers, each license written the way the policy spells it.
     *
     * @param declared the declared license, empty when the package declares none
     * @param spelling the policy's spelling of a license identifier
     * @return the alternatives, each a license or licenses joined by {@link #CONJUNCTION}, and none when nothing
     *         is declared
     */
    public static List<String> alternatives(String declared, UnaryOperator<String> spelling) {
        return alternatives(declared, spelling, _ -> false);
    }

    /**
     * The alternatives a declaration offers, each license written the way the policy spells it, and a license
     * with a known exception judged as the license alone, since an exception only grants more than it does.
     *
     * @param declared  the declared license, empty when the package declares none
     * @param spelling  the policy's spelling of a license identifier
     * @param exception whether an exception named after {@code WITH} is a known one
     * @return the alternatives, each a license or licenses joined by {@link #CONJUNCTION}, and none when nothing
     *         is declared
     */
    public static List<String> alternatives(
        String declared, UnaryOperator<String> spelling, Predicate<String> exception
    ) {
        String text = declared.strip();
        return text.isEmpty() ? List.of() : parsed(text, spelling, exception);
    }

    private static List<String> parsed(String text, UnaryOperator<String> spelling, Predicate<String> exception) {
        List<String> tokens = TOKENS.matcher(text).results().map(MatchResult::group).toList();
        UnaryOperator<String> judged = license -> excepted(license, spelling, exception);
        return disjunction(tokens, 0).filter(parsed -> parsed.next() == tokens.size()).map(
            parsed -> parsed.alternatives().stream()
                .map(terms -> terms.stream().map(judged).collect(Collectors.joining(CONJUNCTION)))
                .distinct().toList()
        ).orElseGet(() -> List.of(text));
    }

    // A recursive descent over the tokens. Each step gives its subexpression as alternatives, each a list of
    // licenses, with the index of the token after it, or nothing at a token the grammar does not accept.
    private static Optional<Parsed> disjunction(List<String> tokens, int start) {
        return conjunction(tokens, start).flatMap(first -> disjuncts(tokens, first));
    }

    private static Optional<Parsed> disjuncts(List<String> tokens, Parsed left) {
        if (!OR.equals(at(tokens, left.next()))) {
            return Optional.of(left);
        }
        return conjunction(tokens, left.next() + 1).flatMap(
            right -> disjuncts(
                tokens, new Parsed(
                    Stream.concat(left.alternatives().stream(), right.alternatives().stream()).toList(), right.next()
                )
            )
        );
    }

    private static Optional<Parsed> conjunction(List<String> tokens, int start) {
        return term(tokens, start).flatMap(first -> conjuncts(tokens, first));
    }

    private static Optional<Parsed> conjuncts(List<String> tokens, Parsed left) {
        if (!AND.equals(at(tokens, left.next()))) {
            return Optional.of(left);
        }
        return term(tokens, left.next() + 1).flatMap(
            right -> conjuncts(tokens, new Parsed(product(left.alternatives(), right.alternatives()), right.next()))
        );
    }

    private static List<List<String>> product(List<List<String>> left, List<List<String>> right) {
        return left.stream().flatMap(
            terms -> right.stream().map(others -> Stream.concat(terms.stream(), others.stream()).toList())
        ).toList();
    }

    private static Optional<Parsed> term(List<String> tokens, int start) {
        return OPEN.equals(at(tokens, start)) ? group(tokens, start + 1) : license(tokens, start);
    }

    private static Optional<Parsed> group(List<String> tokens, int start) {
        return disjunction(tokens, start).filter(inner -> CLOSE.equals(at(tokens, inner.next())))
            .map(inner -> new Parsed(inner.alternatives(), inner.next() + 1));
    }

    private static Optional<Parsed> license(List<String> tokens, int start) {
        return Optional.of(at(tokens, start)).filter(LicenseExpression::identifier)
            .flatMap(named -> excepted(tokens, start + 1, named));
    }

    // A license may name an exception after WITH, which then belongs to it.
    private static Optional<Parsed> excepted(List<String> tokens, int next, String license) {
        if (!WITH.equals(at(tokens, next))) {
            return Optional.of(new Parsed(List.of(List.of(license)), next));
        }
        return Optional.of(at(tokens, next + 1)).filter(LicenseExpression::identifier)
            .map(exception -> new Parsed(List.of(List.of(license + ' ' + WITH + ' ' + exception)), next + 2));
    }

    // A license written with WITH keeps the exception unless the exception is known, which leaves the license.
    private static String excepted(String license, UnaryOperator<String> spelling, Predicate<String> exception) {
        String marker = ' ' + WITH + ' ';
        int at = license.indexOf(marker);
        if (at < 0) {
            return spelling.apply(license);
        }
        String named = license.substring(at + marker.length());
        return exception.test(named) ? spelling.apply(license.substring(0, at)) : license;
    }

    private static boolean identifier(String token) {
        return IDENTIFIER.matcher(token).matches() && !List.of(OR, AND, WITH).contains(token);
    }

    private static String at(List<String> tokens, int index) {
        return index < tokens.size() ? tokens.get(index) : "";
    }

    /**
     * One parsed subexpression.
     *
     * @param alternatives its alternatives, each a list of licenses joined by {@code AND}
     * @param next         the index of the token after it
     */
    private record Parsed(List<List<String>> alternatives, int next) {
    }
}
