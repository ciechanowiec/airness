package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An assertion library that asserts through a name without an assertion prefix proves a test only in a source
 * that names the library, and every spelling that library allows proves it there.
 */
class AssertionLibraryTest {

    private static final String TEMPLATE = """
        package sample;

        %s
        import org.junit.jupiter.api.Test;

        class SubjectTest {

            @Test
            void judgesWhatTheSubjectProduced() {
                %s
            }
        }
        """;
    private static final String BDD_ASSERTIONS = "org.assertj.core.api.BDDAssertions";
    private static final String BDD_MOCKITO = "org.mockito.BDDMockito";
    private static final String REST_ASSURED = "import static io.restassured.RestAssured.given;\n";
    private static final String ORDERS = "given().when().get(\"/orders\")";

    @Test
    void provesATestThroughAnAssertStatement() {
        assertProven("", "assert new Subject().sum(2, 2) == 4;");
    }

    @Test
    void provesATestThroughAStaticallyImportedBddThen() {
        assertProven(
            "import static " + BDD_ASSERTIONS + ".then;\n", "then(new Subject().sum(2, 2)).isEqualTo(4);"
        );
    }

    @Test
    void provesATestThroughABddEntryPointOfAWildcardImport() {
        assertProven(
            "import static " + BDD_ASSERTIONS + ".*;\n",
            "thenThrownBy(() -> new Subject().sum(-1, 2)).isInstanceOf(IllegalArgumentException.class);"
        );
    }

    @Test
    void provesATestThroughAQualifiedBddThen() {
        assertProven("import " + BDD_ASSERTIONS + ";\n", "BDDAssertions.then(new Subject().sum(2, 2)).isEqualTo(4);");
    }

    @Test
    void provesATestThroughASoftBddThen() {
        assertProven(
            "import org.assertj.core.api.BDDSoftAssertions;\n",
            "BDDSoftAssertions softly = new BDDSoftAssertions(); softly.then(new Subject().sum(2, 2)).isEqualTo(4);"
        );
    }

    @Test
    void provesATestThroughAStaticallyImportedMockitoThenShould() {
        assertProven("import static " + BDD_MOCKITO + ".then;\n", "then(listener).should().accept(4);");
    }

    @Test
    void provesATestThroughAQualifiedMockitoThenShould() {
        assertProven(
            "import " + BDD_MOCKITO + ";\n", "BDDMockito.then(listener).shouldHaveNoMoreInteractions();"
        );
    }

    @Test
    void provesATestThroughAnAwaitilityCondition() {
        assertProven(
            "import static org.awaitility.Awaitility.await;\n",
            "await().atMost(Duration.ofSeconds(5)).until(() -> new Subject().done());"
        );
    }

    @Test
    void provesATestThroughAQualifiedAwaitilityCondition() {
        assertProven(
            "import org.awaitility.Awaitility;\n",
            "Awaitility.waitAtMost(Duration.ofSeconds(5)).untilTrue(new Subject().done());"
        );
    }

    @Test
    void provesATestThroughARestAssuredStatusCode() {
        assertProven(REST_ASSURED, ORDERS + ".then().statusCode(200);");
    }

    @Test
    void provesATestThroughARestAssuredBodyAfterFurtherCalls() {
        assertProven(REST_ASSURED, ORDERS + ".then().log().all().assertThat().body(\"size()\", is(2));");
    }

    @Test
    void readsAReactiveThenInASourceNamingNoLibraryAsNoAssertion() {
        assertUnproven("import reactor.core.publisher.Mono;\n", "Mono.just(4).then().block();");
    }

    @Test
    void readsAReactiveThenBesideBddAssertionsAsNoAssertion() {
        assertUnproven("import static " + BDD_ASSERTIONS + ".then;\n", "Mono.just(4).then().block();");
    }

    @Test
    void readsAMockitoThenWithoutShouldAsNoAssertion() {
        assertUnproven("import static " + BDD_MOCKITO + ".then;\n", "then(listener);");
    }

    @Test
    void readsARestAssuredThenWithoutAValidationAsNoAssertion() {
        assertUnproven(REST_ASSURED, ORDERS + ".then().log().all();");
    }

    @Test
    void readsAHelperStartingWithThenAsAHelperRatherThanAnAssertion() {
        String source = """
            package sample;

            import static org.assertj.core.api.BDDAssertions.*;
            import org.junit.jupiter.api.Test;

            class SubjectTest {

                @Test
                void addsTheTwoNumbersGiven() {
                    thenTheSumIsLogged(new Subject().sum(2, 2));
                }

                private void thenTheSumIsLogged(int sum) {
                    System.out.println(sum);
                }
            }
            """;
        assertEquals(
            1, AssertionRules.unproven(source).size(), "a helper of the source's own is followed, not trusted"
        );
    }

    private static void assertProven(String imports, String statement) {
        List<String> unproven = AssertionRules.unproven(TEMPLATE.formatted(imports, statement));
        assertTrue(unproven.isEmpty(), () -> "the call proves the test: " + statement + " " + unproven);
    }

    private static void assertUnproven(String imports, String statement) {
        assertEquals(
            1, AssertionRules.unproven(TEMPLATE.formatted(imports, statement)).size(),
            () -> "the call asserts nothing: " + statement
        );
    }
}
