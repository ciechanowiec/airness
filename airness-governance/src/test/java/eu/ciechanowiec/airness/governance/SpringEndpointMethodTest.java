package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A matcher names a method as readily as it names a pattern, and one that names none opens every
 * method mapped under that path. That is a declaration of the mapping it opens for as long as the
 * pattern opens one, and it is a line that opened more than its author read as soon as the pattern
 * opens two, which is how a private read comes to sit behind a public write.
 */
class SpringEndpointMethodTest {

    private static final List<Path> ROOTS = List.of(Path.of("src/main/java"), Path.of("src/test/java"));
    private static final String SECURITY = "src/main/java/sample/Security.java";
    private static final String PATHS = "src/main/java/sample/GamePaths.java";
    private static final List<String> READ = List.of("open GET /games");
    private static final List<String> BOTH = List.of("open GET /games", "open POST /games");
    private static final String OPENS_GET = ".requestMatchers(HttpMethod.GET, \"/games\").permitAll()";
    private static final String OPENS_POST = ".requestMatchers(HttpMethod.POST, \"/games\").permitAll()";

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
        GitFixture fixture = new GitFixture("endpoint-method-" + files.length + '-' + open.size());
        for (int index = 0; index < files.length; index += 2) {
            fixture.write(files[index], files[index + 1]);
        }
        Path root = fixture.root();
        return SpringEndpointRules.undeclared(open, SpringTypes.over(root, JavaSources.under(root, ROOTS)));
    }

    @Test
    void acceptsAMatcherNamingNoMethodWhereThePatternOpensOne() {
        List<String> offences = offences(READ, SECURITY, chain(".requestMatchers(\"/games\").permitAll()"));

        assertEquals(List.of(), offences, "a pattern open under one method is named by naming the pattern");
    }

    @Test
    void reportsAMatcherNamingNoMethodWhereThePatternOpensTwo() {
        List<String> offences = offences(BOTH, SECURITY, chain(".requestMatchers(\"/games\").permitAll()"));

        assertEquals(2, offences.size(), "one line opened two mappings and was read against one of them");
    }

    @Test
    void namesTheMethodTheMatcherWouldHaveToState() {
        List<String> offences = offences(BOTH, SECURITY, chain(".requestMatchers(\"/games\").permitAll()"));

        assertTrue(
            offences.getLast().contains("requestMatchers(HttpMethod.POST, \"/games\").permitAll()"),
            "the offence names the matcher that would declare this method and no other"
        );
    }

    @Test
    void acceptsAMatcherPerMethodWhereThePatternOpensTwo() {
        List<String> offences = offences(BOTH, SECURITY, chain(OPENS_GET + '\n' + OPENS_POST));

        assertEquals(List.of(), offences, "a method named is a method declared");
    }

    @Test
    void readsAMatcherNamingOneMethodAsNamingNoOther() {
        List<String> offences = offences(BOTH, SECURITY, chain(OPENS_GET));

        assertEquals(1, offences.size(), "naming the get says nothing about the post the chain also admits");
    }

    @Test
    void namesTheMappingTheMatcherLeftOut() {
        List<String> offences = offences(BOTH, SECURITY, chain(OPENS_GET));

        assertTrue(
            offences.getFirst().startsWith("POST /games: the security chain let an unauthenticated"),
            "the offence is the mapping no matcher of the module names"
        );
    }

    @Test
    void readsAStaticallyImportedMethodAsTheMethod() {
        List<String> offences = offences(BOTH, SECURITY, chain(".requestMatchers(GET, \"/games\").permitAll()"));

        assertEquals(1, offences.size(), "a method written bare is the method the matcher names");
    }

    @Test
    void readsAConstantHoldingAPathAsThePatternRatherThanAsAMethod() {
        List<String> offences = offences(
            READ,
            SECURITY,
            chain(".requestMatchers(GamePaths.GET).permitAll()"),
            PATHS,
            """
                package sample;

                final class GamePaths {

                    static final String GET = "/games";
                }
                """
        );

        assertEquals(List.of(), offences, "an argument the module declares as a string is a path however it reads");
    }
}
