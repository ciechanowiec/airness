package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A mapping the running chain left open is read against the patterns the module says it meant to open.
 * A matcher naming the pattern is that declaration. A matcher naming a prefix admits whatever is mapped
 * under it later and so declares nothing about this endpoint.
 */
class SpringEndpointRulesTest {

    private static final List<Path> ROOTS = List.of(Path.of("src/main/java"), Path.of("src/test/java"));
    private static final String SECURITY = "src/main/java/sample/Security.java";
    private static final String SECURITY_TEST = "src/test/java/sample/SecurityTest.java";
    private static final List<String> ORDERS = List.of("open GET /api/orders/{id}");

    private static String chain(String rules) {
        return """
            package sample;

            class Security {

                SecurityFilterChain chain(HttpSecurity http) throws Exception {
                    return http
                        .authorizeHttpRequests(
                            registry -> registry
                                %s
                                .anyRequest().authenticated()
                        )
                        .build();
                }
            }
            """.formatted(rules);
    }

    private static List<String> offences(List<String> open, String... files) {
        GitFixture fixture = new GitFixture("endpoints-" + files.length + '-' + open.size());
        for (int index = 0; index < files.length; index += 2) {
            fixture = fixture.write(files[index], files[index + 1]);
        }
        Path root = fixture.root();
        return SpringEndpointRules.undeclared(open, SpringTypes.over(root, JavaSources.under(root, ROOTS)));
    }

    @Test
    void acceptsAMatcherNamingThePatternTheMappingDeclares() {
        List<String> offences = offences(
            ORDERS, SECURITY, chain(".requestMatchers(\"/api/orders/{id}\").permitAll()")
        );

        assertEquals(List.of(), offences, "the module named the pattern it left open");
    }

    @Test
    void acceptsAMatcherNamingThePatternAmongSeveral() {
        List<String> offences = offences(
            ORDERS, SECURITY, chain(".requestMatchers(\"/login\", \"/api/orders/{id}\", \"/health\").permitAll()")
        );

        assertEquals(List.of(), offences, "a matcher admitting several patterns names each of them");
    }

    @Test
    void acceptsAMatcherNamingThePatternAfterAMethod() {
        List<String> offences = offences(
            ORDERS, SECURITY, chain(".requestMatchers(HttpMethod.GET, \"/api/orders/{id}\").permitAll()")
        );

        assertEquals(List.of(), offences, "the method the matcher restricts to leaves the pattern named");
    }

    @Test
    void reportsAMappingOnlyAPrefixAdmits() {
        List<String> offences = offences(ORDERS, SECURITY, chain(".requestMatchers(\"/api/**\").permitAll()"));

        assertEquals(1, offences.size(), "a prefix admits what is mapped under it, which is not a declaration");
        assertTrue(
            offences.getFirst().startsWith("GET /api/orders/{id}: the security chain let an unauthenticated"),
            "the offence names the method and the pattern the container mapped"
        );
        assertTrue(
            offences.getFirst().contains("requestMatchers(\"/api/orders/{id}\").permitAll()"),
            "the remedy names the pattern the project would have to write"
        );
    }

    @Test
    void reportsAMappingTheModuleNamesNowhere() {
        List<String> offences = offences(ORDERS, SECURITY, chain(".requestMatchers(\"/login\").permitAll()"));

        assertEquals(1, offences.size(), "naming another pattern says nothing about this one");
    }

    @Test
    void reportsAMappingWhereTheModuleWritesNoMatcherAtAll() {
        List<String> offences = offences(ORDERS);

        assertEquals(1, offences.size(), "a module holding no permitAll has declared nothing public");
    }

    @Test
    void refusesAMatcherThatNamesThePatternWithoutPermittingIt() {
        List<String> offences = offences(
            ORDERS, SECURITY, chain(".requestMatchers(\"/api/orders/{id}\").hasRole(\"ADMIN\")")
        );

        assertEquals(
            1, offences.size(),
            "a matcher that asks something of the caller is not the one that admits anyone"
        );
    }

    @Test
    void readsNoMatcherOutOfATestSource() {
        List<String> offences = offences(
            ORDERS, SECURITY_TEST, chain(".requestMatchers(\"/api/orders/{id}\").permitAll()")
        );

        assertEquals(1, offences.size(), "what a test admits is not what the application ships");
    }

    @Test
    void readsNoMatcherOutOfAComment() {
        List<String> offences = offences(
            ORDERS, SECURITY,
            chain(".requestMatchers(\"/login\").permitAll()\n// .requestMatchers(\"/api/orders/{id}\").permitAll()")
        );

        assertEquals(1, offences.size(), "a matcher that was commented out admits nothing");
    }

    @Test
    void reportsEachOpenMappingOnce() {
        List<String> offences = offences(
            List.of("open GET /api/orders/{id}", "open GET /api/orders/{id}", "open POST /api/orders"),
            SECURITY, chain(".requestMatchers(\"/login\").permitAll()")
        );

        assertEquals(2, offences.size(), "two runs recording one mapping are one offence");
    }

    @Test
    void readsNothingOutOfALineThatIsNotEvidence() {
        List<String> offences = offences(List.of("sample.Application", "open", "open GET"), SECURITY, chain(""));

        assertEquals(List.of(), offences, "a line that is not an open mapping states no mapping");
    }
}
