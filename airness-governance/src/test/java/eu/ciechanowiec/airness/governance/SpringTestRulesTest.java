package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The test-scope rule reads a suite against what the module around it arranged. A profile is named in
 * one file and answered in another that is not Java at all, so it is not a question about one source.
 */
class SpringTestRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src"));
    private static final List<String> NOTHING_CONFIGURED = List.of();
    private static final String SUITE = "src/test/java/sample/SuiteTest.java";
    private static final String INTEGRATION = "integration";

    private static final String ACTIVATES_A_PROFILE = """
        package sample;

        @SpringBootTest
        @ActiveProfiles("integration")
        class SuiteTest {
        }
        """;

    private static final String SELECTS_ON_THE_PROFILE = """
        package sample;

        @Component
        @Profile("integration")
        class Stub {
        }
        """;

    private static final String SELECTS_AGAINST_THE_PROFILE = """
        package sample;

        @Component
        @Profile("!integration")
        class Stub {
        }
        """;

    private static SpringTypes types(GitFixture fixture) {
        Path root = fixture.root();
        return SpringTypes.over(root, JavaSources.under(root, SOURCES));
    }

    @Test
    void reportsAProfileNoConfigurationAnswers() {
        SpringTypes types = types(new GitFixture("test-profile").write(SUITE, ACTIVATES_A_PROFILE));

        List<String> offences = SpringTestRules.missingProfiles(types, NOTHING_CONFIGURED);

        assertEquals(1, offences.size(), "the profile the suite activates is answered by nothing");
        assertTrue(offences.getFirst().contains(INTEGRATION), "the offence names the profile asked for");
        assertTrue(offences.getFirst().contains("without a word"), "and says why nothing said so");
    }

    @Test
    void leavesAProfileAConfigurationFileAnswers() {
        SpringTypes types = types(new GitFixture("test-profile-file").write(SUITE, ACTIVATES_A_PROFILE));

        assertEquals(
            List.of(), SpringTestRules.missingProfiles(types, List.of(INTEGRATION)),
            "a file named after the profile is what activating it was for"
        );
    }

    @Test
    void leavesAProfileABeanSelectsOn() {
        SpringTypes types = types(
            new GitFixture("test-profile-bean")
                .write(SUITE, ACTIVATES_A_PROFILE)
                .write("src/main/java/sample/Stub.java", SELECTS_ON_THE_PROFILE)
        );

        assertEquals(
            List.of(), SpringTestRules.missingProfiles(types, NOTHING_CONFIGURED),
            "a profile that only picks beans needs no file of its own"
        );
    }

    @Test
    void readsANegatedProfileAsTheProfileItNames() {
        SpringTypes types = types(
            new GitFixture("test-profile-negated")
                .write(SUITE, ACTIVATES_A_PROFILE)
                .write("src/main/java/sample/Stub.java", SELECTS_AGAINST_THE_PROFILE)
        );

        assertEquals(
            List.of(), SpringTestRules.missingProfiles(types, NOTHING_CONFIGURED),
            "a bean excluded by the profile is a bean the profile decides, which is the profile existing"
        );
    }
}
