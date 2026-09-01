package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringSourceCheckTest {

    private static final String ROOT = "com.example";
    private static final String APPLICATION = "src/main/java/sample/Application.java";
    private static final String WIRING = "src/main/java/sample/Wiring.java";
    private static final String ROW = "src/main/java/sample/Row.java";
    private static final String ROWS = "src/main/java/sample/Rows.java";
    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));

    private static final String CLEAN = """
        package com.example;

        @SpringBootApplication(proxyBeanMethods = false)
        final class Application {

            static void main(String[] args) {
                SpringApplication.run(Application.class, args);
            }
        }
        """;

    private static final String MISPLACED = """
        package com.example.boot;

        @SpringBootApplication(proxyBeanMethods = false)
        final class Application {
        }
        """;

    private static final String CALLING = """
        package com.example;

        @Configuration(proxyBeanMethods = false)
        final class Wiring {

            @Bean
            Neighbour neighbour() {
                return new Neighbour();
            }

            @Bean
            Holder holder() {
                return new Holder(neighbour());
            }
        }
        """;

    private static final String RECORD = """
        package com.example;

        record Row(Booking booking, String room, String client) {
        }
        """;

    private static final String QUERY = """
        package com.example;

        interface Rows {

            @Query("SELECT new com.example.Row(booking, room.name) FROM Booking booking")
            List<Row> rows();
        }
        """;

    @Test
    void passesOverASourceTreeThatBreaksNeitherRule() {
        Path root = new GitFixture("spring-clean").write(APPLICATION, CLEAN).root();

        assertTrue(Verdicts.clean(new SpringSourceCheck(root, MAIN, ROOT).findings()), "nothing is reported");
    }

    @Test
    void reportsTheMisplacedApplicationAndTheBeanCallSeparately() {
        Path root = new GitFixture("spring-broken")
            .write(APPLICATION, MISPLACED)
            .write(WIRING, CALLING)
            .root();

        List<Findings> findings = new SpringSourceCheck(root, MAIN, ROOT).findings();

        assertEquals(1, Verdicts.offences(findings, "package root").size(), "the application is one offence");
        assertEquals(1, Verdicts.offences(findings, "Bean methods").size(), "the call is the other");
    }

    @Test
    void namesTheSourceThatCarriesAnOffence() {
        Path root = new GitFixture("spring-named").write(WIRING, CALLING).root();

        List<String> offences = Verdicts.offences(new SpringSourceCheck(root, MAIN, ROOT).findings(), "Bean methods");

        assertTrue(offences.getFirst().startsWith(WIRING), "the offence opens with the path it came from");
    }

    @Test
    void readsWhatAQueryConstructsAgainstTheRecordDeclaredBesideIt() {
        Path root = new GitFixture("spring-constructed")
            .write(ROW, RECORD)
            .write(ROWS, QUERY)
            .root();

        List<Findings> findings = new SpringSourceCheck(root, MAIN, ROOT).findings();

        assertEquals(1, Verdicts.offences(findings, "wrong number of arguments").size(), "one is missing");
    }

    @Test
    void leavesAQueryConstructingTheRecordItCanBuild() {
        Path root = new GitFixture("spring-buildable")
            .write(ROW, RECORD)
            .write(ROWS, QUERY.replace("room.name)", "room.name, client.name)"))
            .root();

        assertTrue(Verdicts.clean(new SpringSourceCheck(root, MAIN, ROOT).findings()), "the arguments answer");
    }

    @Test
    void reportsARepositoryQueryWhoseParameterNameDisagrees() {
        String query = """
            package com.example;

            interface Rows {

                @Query("select row from Row row where row.owner = :owner")
                Row row(@Param("holder") UUID owner);
            }
            """;
        Path root = new GitFixture("spring-query-parameters").write(ROWS, query).root();

        List<String> offences = Verdicts.offences(
            new SpringSourceCheck(root, MAIN, ROOT).findings(), "parameter names"
        );

        assertEquals(1, offences.size(), "the source check exposes the query rule");
    }

    @Test
    void countsTheSourcesItRead() {
        Path root = new GitFixture("spring-counted")
            .write(APPLICATION, CLEAN)
            .write(WIRING, CALLING)
            .root();

        assertEquals(2, new SpringSourceCheck(root, MAIN, ROOT).scanned(), "both sources are in scope");
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanTree() {
        Path root = new GitFixture("spring-empty").root();

        assertEquals(0, new SpringSourceCheck(root, MAIN, ROOT).scanned(), "the goal refuses this count");
    }
}
