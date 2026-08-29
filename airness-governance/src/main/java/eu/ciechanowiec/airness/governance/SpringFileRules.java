package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reports the four settings whose absence is the defect.
 *
 * <p>Each of these reads as one question about a whole file: something is declared, and the thing that
 * makes it safe is not declared beside it. An HTTP client is built and no timeout bounds it. A filter
 * chain is opened and no terminal rule closes it. A persistence test is written and nothing stops Boot
 * swapping the database underneath it.
 *
 * <p>They live here rather than in the analyzer configuration because each needs two answers about one
 * file at once, and an XPath match reports on the node it matched rather than on what was missing
 * elsewhere. A rule that has to say that this is here and that is not needs to have read both.
 */
@UtilityClass
final class SpringFileRules {

    private static final Pattern CLIENT_BUILDER = Pattern.compile(
        "\\b(?:RestClient|WebClient)\\s*\\.\\s*[Bb]uilder\\b|\\bRestTemplateBuilder\\b"
    );
    private static final Pattern TIMEOUT = Pattern.compile(
        "\\b(?:connectTimeout|readTimeout|responseTimeout|setConnectTimeout|setReadTimeout)\\b"
    );
    private static final Pattern AUTHORIZE = Pattern.compile("\\bauthorizeHttpRequests\\s*\\(");
    private static final Pattern TERMINAL = Pattern.compile("\\banyRequest\\s*\\(");
    private static final Pattern CREDENTIALS = Pattern.compile("\\ballowCredentials\\s*\\(\\s*true\\s*\\)");
    /*
     * The setter form spells the name inside a longer identifier, where a word boundary before it does not
     * exist: setAllowedOriginPatterns has no break between the t and the A.
     */
    private static final Pattern WILDCARD_ORIGIN = Pattern.compile(
        "(?i)allowedorigin(?:pattern)?s?\\s*\\([^)]*\"\\*\""
    );
    private static final Pattern DATA_JPA_TEST = Pattern.compile("@DataJpaTest\\b");
    private static final Pattern REPLACE_NONE = Pattern.compile(
        "\\breplace\\s*=\\s*(?:AutoConfigureTestDatabase\\s*\\.\\s*)?Replace\\s*\\.\\s*NONE\\b"
    );

    /**
     * Whether an HTTP client is built here with nothing bounding how long it waits.
     *
     * @param source the Java source to read
     * @return the offence, when a builder appears and no timeout does
     */
    static List<String> untimedClients(CharSequence source) {
        return unless(
            source, CLIENT_BUILDER, TIMEOUT,
            "an HTTP client is built here with no connect or read timeout, so one unresponsive dependency"
                + " can consume every request thread"
        );
    }

    /**
     * Whether a filter chain is opened here without a rule that closes it.
     *
     * @param source the Java source to read
     * @return the offence, when the chain names no terminal matcher
     */
    static List<String> openFilterChains(CharSequence source) {
        return unless(
            source, AUTHORIZE, TERMINAL,
            "this filter chain names no anyRequest, so every path no earlier matcher named is"
                + " unauthenticated, and adding a controller adds a public endpoint"
        );
    }

    /**
     * Whether a persistence test lets Boot swap the configured database for an embedded one.
     *
     * @param source the Java source to read
     * @return the offence, when the replacement is not turned off
     */
    static List<String> replacedTestDatabases(CharSequence source) {
        return unless(
            source, DATA_JPA_TEST, REPLACE_NONE,
            "this test runs against an embedded database rather than the configured one, so its dialect,"
                + " its indexes and its constraints go unexercised"
        );
    }

    /**
     * Whether credentialed requests are accepted here from any origin at all.
     *
     * @param source the Java source to read
     * @return the offence, when credentials are allowed beside a wildcard origin
     */
    static List<String> unscopedCorsCredentials(CharSequence source) {
        // The wildcard this rule looks for is a literal, so the literals stay. Blanking them would leave
        // the rule reading an origin list it can no longer see the contents of.
        String code = JavaCode.withoutComments(source);
        return found(code, CREDENTIALS)
            .filter(_ -> found(code, WILDCARD_ORIGIN).isPresent())
            .map(
                at -> offence(
                    source, at,
                    "credentials are allowed beside a wildcard origin: written as allowedOrigins the"
                        + " browser refuses it outright, and written as allowedOriginPatterns Spring echoes"
                        + " the caller's own origin back instead of a wildcard, so that form is accepted"
                        + " and admits every site there is"
                )
            )
            .stream()
            .toList();
    }

    private static List<String> unless(
        CharSequence source, Pattern trigger, Pattern required, String consequence
    ) {
        String code = JavaCode.blanked(source);
        return found(code, required).isPresent()
            ? List.of()
            : found(code, trigger).map(at -> offence(source, at, consequence)).stream().toList();
    }

    private static Optional<Integer> found(String code, Pattern pattern) {
        Matcher match = pattern.matcher(code);
        return match.find() ? Optional.of(match.start()) : Optional.empty();
    }

    private static String offence(CharSequence source, int at, String consequence) {
        return "line " + JavaCode.lineOf(source, at) + ": " + consequence;
    }
}
