package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringFileRulesTest {

    @Test
    void reportsAClientBuiltWithNoTimeout() {
        String source = """
            package com.example;

            class Wiring {

                RestClient client(RestClient.Builder builder) {
                    return builder.baseUrl("https://example.invalid").build();
                }
            }
            """;

        List<String> offences = SpringFileRules.untimedClients(source);

        assertEquals(1, offences.size(), "nothing bounds how long this client waits");
        assertTrue(offences.getFirst().contains("request thread"), "the offence names the consequence");
    }

    @Test
    void acceptsAClientThatBoundsItsWait() {
        String source = """
            package com.example;

            class Wiring {

                RestClient client(RestClient.Builder builder) {
                    return builder.requestFactory(factory).connectTimeout(TEN).build();
                }
            }
            """;

        assertEquals(List.of(), SpringFileRules.untimedClients(source), "a timeout is declared");
    }

    @Test
    void passesOverASourceThatBuildsNoClient() {
        assertEquals(List.of(), SpringFileRules.untimedClients("class Plain {}"), "there is no client to bound");
    }

    @Test
    void reportsAFilterChainWithNoTerminalMatcher() {
        String source = """
            package com.example;

            class Security {

                SecurityFilterChain chain(HttpSecurity http) {
                    return http.authorizeHttpRequests(requests -> requests.requestMatchers("/a").permitAll())
                        .build();
                }
            }
            """;

        assertEquals(1, SpringFileRules.openFilterChains(source).size(), "no rule closes this chain");
    }

    @Test
    void acceptsAFilterChainThatClosesItself() {
        String source = """
            package com.example;

            class Security {

                SecurityFilterChain chain(HttpSecurity http) {
                    return http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                        .build();
                }
            }
            """;

        assertEquals(List.of(), SpringFileRules.openFilterChains(source), "anyRequest closes it");
    }

    @Test
    void passesOverASourceThatOpensNoChain() {
        assertEquals(List.of(), SpringFileRules.openFilterChains("class Plain {}"), "no chain is opened");
    }

    @Test
    void reportsAPersistenceTestRunAgainstAnotherDatabase() {
        String source = """
            package com.example;

            @DataJpaTest
            class RowRepositoryTest {
            }
            """;

        assertEquals(1, SpringFileRules.replacedTestDatabases(source).size(), "Boot swaps the database here");
    }

    @Test
    void acceptsAPersistenceTestThatKeepsItsDatabase() {
        String source = """
            package com.example;

            @DataJpaTest
            @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
            class RowRepositoryTest {
            }
            """;

        assertEquals(List.of(), SpringFileRules.replacedTestDatabases(source), "the replacement is turned off");
    }

    @Test
    void reportsCredentialsAcceptedFromAnyOrigin() {
        String source = """
            package com.example;

            class Cors {

                void configure(CorsConfiguration configuration) {
                    configuration.setAllowedOriginPatterns(List.of("*"));
                    configuration.allowCredentials(true);
                }
            }
            """;

        assertEquals(1, SpringFileRules.unscopedCorsCredentials(source).size(), "the browser refuses this pairing");
    }

    @Test
    void acceptsCredentialsScopedToNamedOrigins() {
        String source = """
            package com.example;

            class Cors {

                void configure(CorsConfiguration configuration) {
                    configuration.setAllowedOrigins(List.of("https://example.invalid"));
                    configuration.allowCredentials(true);
                }
            }
            """;

        assertEquals(List.of(), SpringFileRules.unscopedCorsCredentials(source), "the origins are named");
    }

    @Test
    void passesOverAWildcardOriginThatCarriesNoCredentials() {
        String source = """
            package com.example;

            class Cors {

                void configure(CorsConfiguration configuration) {
                    configuration.setAllowedOrigins(List.of("*"));
                }
            }
            """;

        assertEquals(List.of(), SpringFileRules.unscopedCorsCredentials(source), "nothing credentialed is accepted");
    }
}
