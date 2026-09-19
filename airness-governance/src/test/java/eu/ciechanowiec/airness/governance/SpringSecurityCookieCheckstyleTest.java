package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringSecurityCookieCheckstyleTest {

    private static final String HTTP_ONLY = "AirnessSpringSecurityCookieDeclaresHttpOnly";
    private static final String SECURE = "AirnessSpringSecurityCookieDeclaresSecure";
    private static final String SAME_SITE = "AirnessSpringSecurityCookieDeclaresSameSite";
    private static final String SERVLET = "AirnessSpringSecurityCookieIsNotServletTyped";
    // The call each rule looks for, which is also what tells its query from its siblings' in the
    // shipped configuration.
    private static final String READS = "httpOnly";
    private static final String TRAVELS = "secure";
    private static final String CROSSES = "sameSite";
    private static final String SERVLET_TYPE = "Cookie";

    @Test
    void reportsACookieThatDoesNotSayWhetherAPageMayReadIt(@TempDir Path directory) {
        String source = built(".secure(true).sameSite(\"Lax\")");

        assertEquals(1, findings(directory, source, HTTP_ONLY, READS), "the flag is not stated");
    }

    @Test
    void reportsACookieThatDoesNotSayWhetherItTravelsOverPlainHttp(@TempDir Path directory) {
        String source = built(".httpOnly(true).sameSite(\"Lax\")");

        assertEquals(1, findings(directory, source, SECURE, TRAVELS), "the flag is not stated");
    }

    @Test
    void reportsACookieThatDoesNotSayWhetherItRidesOnAnotherSitesRequest(@TempDir Path directory) {
        String source = built(".httpOnly(true).secure(true)");

        assertEquals(1, findings(directory, source, SAME_SITE, CROSSES), "the flag is not stated");
    }

    @Test
    void acceptsACookieStatingAllThreeFlags(@TempDir Path directory) {
        String source = built(".httpOnly(true).secure(true).sameSite(\"Lax\")");
        List<Integer> reported = List.of(
            findings(directory, source, HTTP_ONLY, READS),
            findings(directory, source, SECURE, TRAVELS),
            findings(directory, source, SAME_SITE, CROSSES)
        );

        assertEquals(List.of(0, 0, 0), reported, "every flag is stated");
    }

    @Test
    void acceptsAFlagTurnedOffAsReadilyAsOneTurnedOn(@TempDir Path directory) {
        String source = built(".httpOnly(true).secure(false).sameSite(\"Lax\")");

        assertEquals(
            0, findings(directory, source, SECURE, TRAVELS),
            "stating the flag is the requirement rather than the value it is given"
        );
    }

    @Test
    void acceptsAFlagSuppliedByAConstant(@TempDir Path directory) {
        String source = built(".httpOnly(ALWAYS).secure(OVER_TLS).sameSite(LAX)");
        List<Integer> reported = List.of(
            findings(directory, source, HTTP_ONLY, READS),
            findings(directory, source, SECURE, TRAVELS),
            findings(directory, source, SAME_SITE, CROSSES)
        );

        assertEquals(List.of(0, 0, 0), reported, "a flag named by a constant is stated as surely as a literal");
    }

    @Test
    void passesOverABuilderThatIsNotACookie(@TempDir Path directory) {
        String source = """
            class Sample {
                Object make() {
                    return Something.from("a", "b").path("/").build();
                }
            }
            """;
        List<Integer> reported = List.of(
            findings(directory, source, HTTP_ONLY, READS),
            findings(directory, source, SECURE, TRAVELS),
            findings(directory, source, SAME_SITE, CROSSES)
        );

        assertEquals(List.of(0, 0, 0), reported, "another builder answers a different question");
    }

    @Test
    void passesOverACookieNamedOnlyAsAReturnType(@TempDir Path directory) {
        String source = """
            class Sample {
                ResponseCookie make() {
                    return Something.from("a", "b").build();
                }
            }
            """;

        assertEquals(
            0, findings(directory, source, SECURE, TRAVELS),
            "the type a method answers with is not the builder the chain came from"
        );
    }

    @Test
    void passesOverABuilderCompletedInLaterStatements(@TempDir Path directory) {
        String source = """
            class Sample {
                Object make() {
                    var building = ResponseCookie.from("a", "b");
                    building.secure(true);
                    return building.build();
                }
            }
            """;

        assertEquals(
            0, findings(directory, source, SECURE, TRAVELS),
            "a value followed between statements is a miss rather than a false report"
        );
    }

    @Test
    void keepsTheThreeFlagsToTheirOwnOwners(@TempDir Path directory) {
        String source = built(".httpOnly(true)");
        List<Integer> reported = List.of(
            findings(directory, source, HTTP_ONLY, READS),
            findings(directory, source, SECURE, TRAVELS),
            findings(directory, source, SAME_SITE, CROSSES)
        );

        assertEquals(List.of(0, 1, 1), reported, "each flag is reported by the one rule that owns it");
    }

    @Test
    void reportsACookieBuiltFromTheServletClass(@TempDir Path directory) {
        String source = """
            class Sample {
                Object make() {
                    Cookie cookie = new Cookie("theme", "dark");
                    cookie.setHttpOnly(true);
                    return cookie;
                }
            }
            """;

        assertEquals(
            1, findings(directory, source, SERVLET, SERVLET_TYPE), "its flags are set away from where it is built"
        );
    }

    @Test
    void passesOverATypeMerelyNamedAfterACookie(@TempDir Path directory) {
        String source = """
            class Sample {
                Object make() {
                    return new CookieJar("a");
                }
            }
            """;

        assertEquals(
            0, findings(directory, source, SERVLET, SERVLET_TYPE), "the rule names one class rather than a prefix"
        );
    }

    @Test
    void leavesTheBuilderAloneWhereTheServletClassIsRefused(@TempDir Path directory) {
        String source = built(".httpOnly(true).secure(true).sameSite(\"Lax\")");

        assertEquals(
            0, findings(directory, source, SERVLET, SERVLET_TYPE),
            "the builder is the remedy the servlet rule names rather than a second offence"
        );
    }

    // One cookie, built with whatever flags a test gives it.
    private static String built(String flags) {
        return """
            class Sample {
                Object make() {
                    return ResponseCookie.from("a", "b").path("/")%s.maxAge(60).build();
                }
            }
            """.formatted(flags);
    }

    private static int findings(Path directory, String source, String rule, String marker) {
        return CheckstyleRule.findings(directory, source, rule, marker);
    }
}
