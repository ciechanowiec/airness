package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A matcher states a pattern by writing it out or by naming a string constant the module declares, bare
 * from the same source or qualified by the type declaring it. A security configuration collects its
 * paths into constants as soon as it holds more than a few, and a rule reading literals alone would tell
 * its author to name the patterns that call already names.
 *
 * <p>What resolves to no written string states no pattern, which leaves the mapping reported. That is
 * the direction this has to fail in, so the readings that resolve to nothing are pinned here beside the
 * ones that resolve.
 */
class SpringEndpointConstantTest {

    private static final List<Path> ROOTS = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    private static final String CHAIN = "src/main/java/sample/Security.java";

    private static final String PATHS = "src/main/java/sample/OrderPaths.java";

    private static final String PATHS_UNDER_TEST = "src/test/java/sample/OrderPaths.java";

    private static final List<String> ORDERS = List.of("open GET /api/orders/{id}");

    private static final String HELD = "static final String ORDER = \"/api/orders/{id}\";";

    private static final String ELSEWHERE = """
        package sample;

        final class OrderPaths {

            static final String ORDER = "/api/orders/{id}";
        }
        """;

    @Test
    void acceptsAMatcherNamingThePatternWithAConstantOfItsOwnSource() {
        assertEquals(
            List.of(), permitting(HELD, ".requestMatchers(ORDER).permitAll()"),
            "a constant of the same source states the pattern it holds"
        );
    }

    @Test
    void acceptsAMatcherNamingThePatternWithAConstantOfAnotherType() {
        assertEquals(
            List.of(), permitting("", ".requestMatchers(OrderPaths.ORDER).permitAll()", PATHS),
            "a constant another type of the module declares states it too"
        );
    }

    @Test
    void acceptsAConstantNamingThePatternBesideTheMethodItRestrictsTo() {
        assertEquals(
            List.of(), permitting(HELD, ".requestMatchers(HttpMethod.GET, ORDER).permitAll()"),
            "the method a matcher restricts to leaves the constant beside it read"
        );
    }

    @Test
    void readsNoPatternOutOfTheMethodAMatcherRestrictsTo() {
        assertEquals(
            1, permitting("", ".requestMatchers(HttpMethod.GET).permitAll()").size(),
            "a qualified name no source declares as a string states nothing"
        );
    }

    @Test
    void reportsAMappingAConstantOnlyPrefixes() {
        assertEquals(
            1, permitting("static final String API = \"/api/**\";", ".requestMatchers(API).permitAll()").size(),
            "a constant holding a prefix declares no more than the prefix did"
        );
    }

    @Test
    void readsNoConstantOutOfATestSource() {
        assertEquals(
            1, permitting("", ".requestMatchers(OrderPaths.ORDER).permitAll()", PATHS_UNDER_TEST).size(),
            "a constant only a test declares is not what the application ships"
        );
    }

    @Test
    void reportsAMappingAMatcherNamesByACallItCannotRead() {
        assertEquals(
            1, permitting("", ".requestMatchers(orderPattern()).permitAll()").size(),
            "an argument resolving to no written string names no pattern"
        );
    }

    private static List<String> permitting(String held, String rules, String... paths) {
        GitFixture fixture = new GitFixture("endpoint-constant-" + rules.length() + '-' + paths.length);
        fixture.write(
            CHAIN,
            """
                package sample;

                class Security {

                    %s

                    SecurityFilterChain chain(HttpSecurity http) throws Exception {
                        return http
                            .authorizeHttpRequests(registry -> registry %s .anyRequest().authenticated())
                            .build();
                    }
                }
                """.formatted(held, rules)
        );
        for (String path : paths) {
            fixture.write(path, ELSEWHERE);
        }
        Path root = fixture.root();
        return SpringEndpointRules.undeclared(ORDERS, SpringTypes.over(root, JavaSources.under(root, ROOTS)));
    }
}
