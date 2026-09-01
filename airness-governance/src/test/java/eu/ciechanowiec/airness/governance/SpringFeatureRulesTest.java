package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringFeatureRulesTest {

    private static final String USE = "src/main/java/sample/Use.java";
    private static final String CONFIGURATION = "src/main/java/sample/Configuration.java";
    private static final List<Path> SOURCES = List.of(Path.of("src"));

    @Test
    void reportsAsyncThatProductionNeverEnables() {
        SpringTypes types = types(new GitFixture("feature-async").write(USE, source("@Async(\"pool\")")));

        assertEquals(1, SpringFeatureRules.unenabledAsync(types).size(), "the method has no enabler");
    }

    @Test
    void acceptsAnAsyncEnablerFromAnotherProductionSource() {
        SpringTypes types = types(
            new GitFixture("feature-async-enabled")
                .write(USE, source("@Async(\"pool\")"))
                .write(CONFIGURATION, configuration("@EnableAsync"))
        );

        assertEquals(List.of(), SpringFeatureRules.unenabledAsync(types), "the application activates it");
    }

    @Test
    void doesNotTakeProductionActivationFromATest() {
        SpringTypes types = types(
            new GitFixture("feature-test-enabler")
                .write(USE, source("@Scheduled(cron = \"${cron}\")"))
                .write("src/test/java/sample/TestConfiguration.java", configuration("@EnableScheduling"))
        );

        assertEquals(1, SpringFeatureRules.unenabledScheduling(types).size(), "test configuration does not ship");
    }

    @Test
    void correlatesCachingAcrossTheReactor() {
        SpringTypes types = types(
            new GitFixture("feature-cache")
                .write(USE, source("@Cacheable(\"rows\")"))
                .write(CONFIGURATION, configuration("@EnableCaching"))
        );

        assertEquals(List.of(), SpringFeatureRules.unenabledCaching(types), "the cache interceptor is enabled");
    }

    @Test
    void correlatesRetryAcrossTheReactor() {
        SpringTypes types = types(
            new GitFixture("feature-retry")
                .write(USE, source("@Retryable"))
                .write(CONFIGURATION, configuration("@EnableRetry"))
        );

        assertEquals(List.of(), SpringFeatureRules.unenabledRetry(types), "retry advice is enabled");
    }

    @Test
    void requiresAuditingOnlyOnAPersistentTypeThatUsesIt() {
        String source = """
            package sample;

            @Entity
            class Use {

                @CreatedDate
                private Instant created;
            }
            """;
        SpringTypes types = types(new GitFixture("feature-audit").write(USE, source));

        assertEquals(1, SpringFeatureRules.unenabledAuditing(types).size(), "the field needs JPA auditing");
    }

    @Test
    void ignoresAnAuditingSpellingOutsidePersistence() {
        SpringTypes types = types(new GitFixture("feature-audit-plain").write(USE, source("@CreatedDate")));

        assertEquals(List.of(), SpringFeatureRules.unenabledAuditing(types), "the type is not persisted");
    }

    @Test
    void requiresThePrePostMethodSecurityFlagExplicitly() {
        SpringTypes types = types(
            new GitFixture("feature-prepost")
                .write(USE, source("@PreAuthorize(\"hasRole('ADMIN')\")"))
                .write(CONFIGURATION, configuration("@EnableMethodSecurity"))
        );

        List<String> offences = SpringFeatureRules.disabledMethodSecurity(types);

        assertEquals(1, offences.size(), "the default is not an explicit declaration");
        assertTrue(offences.getFirst().contains("prePostEnabled"), "the offence names the family flag");
    }

    @Test
    void requiresTheSecuredMethodSecurityFlag() {
        SpringTypes types = types(
            new GitFixture("feature-secured")
                .write(USE, source("@Secured(\"ROLE_ADMIN\")"))
                .write(CONFIGURATION, configuration("@EnableMethodSecurity(prePostEnabled = true)"))
        );

        assertTrue(
            SpringFeatureRules.disabledMethodSecurity(types).getFirst().contains("securedEnabled"),
            "a different family flag answers nothing"
        );
    }

    @Test
    void requiresTheJsr250MethodSecurityFlag() {
        SpringTypes types = types(
            new GitFixture("feature-jsr")
                .write(USE, source("@RolesAllowed(\"ADMIN\")"))
                .write(CONFIGURATION, configuration("@EnableMethodSecurity(securedEnabled = true)"))
        );

        assertTrue(
            SpringFeatureRules.disabledMethodSecurity(types).getFirst().contains("jsr250Enabled"),
            "roles allowed belongs to JSR-250"
        );
    }

    @Test
    void acceptsEveryMethodSecurityFamilyItsEnablerNames() {
        String uses = source(
            "@PreAuthorize(\"hasRole('ADMIN')\")\n    @Secured(\"ROLE_ADMIN\")\n    @RolesAllowed(\"ADMIN\")"
        );
        String enabled = configuration(
            "@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)"
        );
        SpringTypes types = types(
            new GitFixture("feature-all-security").write(USE, uses).write(CONFIGURATION, enabled)
        );

        assertEquals(List.of(), SpringFeatureRules.disabledMethodSecurity(types), "all three families are active");
    }

    private static SpringTypes types(GitFixture fixture) {
        Path root = fixture.root();
        return SpringTypes.over(root, JavaSources.under(root, SOURCES));
    }

    private static String source(String annotation) {
        return """
            package sample;

            class Use {

                %s
                void act() {
                }
            }
            """.formatted(annotation);
    }

    private static String configuration(String annotation) {
        return """
            package sample;

            %s
            class Configuration {
            }
            """.formatted(annotation);
    }
}
