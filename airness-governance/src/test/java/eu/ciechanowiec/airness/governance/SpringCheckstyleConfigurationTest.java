package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringCheckstyleConfigurationTest {

    private static final List<Fixture> SPRING_FIXTURES = List.of(
        new Fixture(
            "Premises.java",
            """
                package example;
                final class Premises {
                    static final String DERIVED = "ROLE_" + Role.ADMIN.name();
                    static final String SPELLED = "ROLE_ADMIN";
                }
                """,
            "AirnessSpringRoleIsNotSpelledOut",
            4
        ),
        new Fixture(
            "Beans.java",
            """
                    package example;
                    @Configuration(proxyBeanMethods = false)
                    final class Beans {
                        @Bean
                        public String exposed() {
                            return "";
                        }
                    }
                """,
            "AirnessSpringBeanMethodIsNotPublic",
            4
        ),
        new Fixture(
            "src/test/java/example/ContextTest.java",
            """
                    package example;
                    @SpringBootTest
                    @ExtendWith(SpringExtension.class)
                    final class ContextTest {
                    }
                """,
            "AirnessSpringBootTestDoesNotRepeatSpringExtension",
            2
        ),
        new Fixture(
            "Operations.java",
            """
                    package example;
                    @Service
                    final class Operations {
                        void save(@NotBlank String name) {
                        }
                    }
                """,
            "AirnessSpringComponentMethodConstraintsAreEvaluated",
            2
        ),
        new Fixture(
            "WebConfiguration.java",
            """
                package example;
                final class WebConfiguration implements WebMvcConfigurer {
                }
                """,
            "AirnessSpringWebConfigurerIsConfiguration",
            2
        ),
        new Fixture(
            "Mapping.java",
            """
                package example;
                final class Mapping {
                    @RequestMapping(path = "/one", method = RequestMethod.GET)
                    public void one() {
                    }
                }
                """,
            "AirnessSpringWebMappingUsesPreciseAnnotation",
            3
        ),
        new Fixture(
            "Listeners.java",
            """
                package example;
                final class Listeners {
                    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
                    @Transactional(readOnly = false, timeout = 5)
                    public void deliver(Posted posted) {
                    }
                }
                """,
            "AirnessSpringAfterCommitListenerRunsItsOwnTransaction",
            3
        ),
        new Fixture(
            "Bootstrap.java",
            """
                package example;
                final class Bootstrap implements ApplicationRunner {
                    @Override
                    public void run(ApplicationArguments arguments) {
                    }
                }
                """,
            "AirnessSpringRunnerStatesItsOrder",
            2
        )
    );
    private static final Fixture CONTROLS = new Fixture(
        "src/test/java/example/SpringControls.java",
        """
            package example;
            interface BeanContract {
                String inherited();
            }
            @Configuration(proxyBeanMethods = false)
            final class Controls implements BeanContract, WebMvcConfigurer {
                @Override
                @Bean
                public String inherited() {
                    return "";
                }
                @RequestMapping(
                    path = "/many",
                    method = {RequestMethod.GET, RequestMethod.POST}
                )
                public void many() {
                }
                @RequestMapping(path = "/head", method = RequestMethod.HEAD)
                public void head() {
                }
                @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
                @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false, timeout = 5)
                public void deliver(Posted posted) {
                }
            }
            @Service
            @Validated
            final class Operations {
                void save(@NotBlank String name) {
                }
            }
            @SpringBootTest
            final class ContextTest {
            }
            abstract class WebBase implements WebMvcConfigurer {
            }
            @Order(0)
            final class Seeding implements ApplicationRunner {
                @Override
                public void run(ApplicationArguments arguments) {
                }
            }
            final class Admitting implements CommandLineRunner, Ordered {
                @Override
                public void run(String... arguments) {
                }
                @Override
                public int getOrder() {
                    return 1;
                }
            }
            """,
        "controls",
        1
    );
    private static final List<String> NEW_SPRING_RULES = SPRING_FIXTURES.stream()
        .map(Fixture::rule)
        .toList();

    @Test
    @SneakyThrows
    void reportsEveryNewSpringRuleAtItsExactOwner(@TempDir Path directory) {
        for (Fixture fixture : SPRING_FIXTURES) {
            List<Finding> findings = CheckstyleConfigurationTest.inspect(directory, fixture, true);
            assertTrue(
                findings.stream().anyMatch(finding -> finding.matches(fixture)),
                () -> "%s:%d must report %s, but reported %s"
                    .formatted(fixture.file(), fixture.line(), fixture.rule(), findings)
            );
        }
    }

    @Test
    @SneakyThrows
    void acceptsTheExplicitControlsForEveryNewSpringRule(@TempDir Path directory) {
        List<Finding> findings = CheckstyleConfigurationTest.inspect(directory, CONTROLS, true);
        assertTrue(
            findings.stream().noneMatch(finding -> NEW_SPRING_RULES.contains(finding.rule())),
            () -> "explicit controls must carry no new Spring finding, but reported " + findings
        );
    }

    @Test
    @SneakyThrows
    void keepsTheMissingAndSingleVerbMappingOwnersDisjoint(@TempDir Path directory) {
        Fixture mapping = new Fixture(
            "MappingOwner.java",
            """
                package example;
                final class MappingOwner {
                    @RequestMapping("/one")
                    public void one() {
                    }
                }
                """,
            "AirnessSpringWebMappingNamesItsMethod",
            3
        );
        List<Finding> findings = CheckstyleConfigurationTest.inspect(directory, mapping, true);
        long missing = findings.stream()
            .filter(finding -> finding.is("AirnessSpringWebMappingNamesItsMethod"))
            .count();
        long precise = findings.stream()
            .filter(finding -> finding.is("AirnessSpringWebMappingUsesPreciseAnnotation"))
            .count();
        assertEquals(1, missing, "a mapping with no verb keeps its existing owner");
        assertEquals(0, precise, "the single-verb delta does not duplicate the missing-verb finding");
    }
}
