package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The wiring rules read a declaration in one file against a use in another. Each test below writes both
 * halves, because either half alone is an ordinary source that no rule has anything to say about.
 */
class SpringWiringRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src"));
    private static final String NEIGHBOUR = "src/main/java/sample/Neighbour.java";
    private static final String CALLER = "src/main/java/sample/Caller.java";
    private static final String SESSION = "src/main/java/sample/Session.java";
    private static final String HOLDER = "src/main/java/sample/Holder.java";
    private static final String SETTINGS_SOURCE = "src/main/java/sample/Settings.java";
    private static final String ENTRY_SOURCE = "src/main/java/sample/Entry.java";

    private static final String BOUND_SETTINGS = """
        package sample;

        @ConfigurationProperties(prefix = "acme")
        @Validated
        class Settings {
        }
        """;

    private static final String ENABLES_THE_SETTINGS = """
        package sample;

        @Configuration
        @EnableConfigurationProperties(Settings.class)
        class Entry {
        }
        """;

    private static final String SCANS_FOR_SETTINGS = """
        package sample;

        @Configuration
        @ConfigurationPropertiesScan
        class Entry {
        }
        """;

    private static final String SERVICE = """
        package sample;

        @Service
        class Neighbour {
        }
        """;

    private static final String PLAIN = """
        package sample;

        class Neighbour {
        }
        """;

    private static final String BUILDS_IT = """
        package sample;

        class Caller {

            Neighbour own() {
                return new Neighbour();
            }
        }
        """;

    private static final String PROTOTYPE = """
        package sample;

        @Component
        @Scope("prototype")
        class Session {
        }
        """;

    private static final String TAKES_IT = """
        package sample;

        @Service
        class Holder {

            Holder(Session session) {
            }
        }
        """;

    private static final String TAKES_A_PROVIDER = """
        package sample;

        @Service
        class Holder {

            Holder(ObjectProvider<Session> sessions) {
            }
        }
        """;

    private static final String PLAIN_SESSION = """
        package sample;

        class Session {
        }
        """;

    private static final String PROTOTYPE_TAKES_IT = """
        package sample;

        @Component
        @Scope("prototype")
        class Holder {

            Holder(Session session) {
            }
        }
        """;

    private static final String TAKES_IT_AFTER_A_VALUE = """
        package sample;

        @Service
        class Holder {

            Holder(@Value("${retries}") int retries, Session session) {
            }
        }
        """;

    private static final String GUARDS = """
        package sample;

        @Service
        class Guarded {

            @PreAuthorize("hasRole('ADMIN')")
            public void act() {
            }
        }
        """;

    private static final String ENABLES = """
        package sample;

        @Configuration
        @EnableMethodSecurity
        class Security {
        }
        """;

    private static final String FIRES = """
        package sample;

        @Service
        class Firing {

            @Async
            public void fire() {
            }
        }
        """;

    private static final String NAMES_ITS_EXECUTOR = """
        package sample;

        @Service
        class Firing {

            @Async("pool")
            public void fire() {
            }
        }
        """;

    private static final String DECLARES_A_POOL = """
        package sample;

        @Configuration
        class Pools {

            @Bean
            TaskExecutor pool() {
                return new SimpleAsyncTaskExecutor();
            }
        }
        """;

    private static final String CONFIGURES_ASYNC = """
        package sample;

        @Configuration
        class Pools implements AsyncConfigurer {
        }
        """;

    private static SpringTypes types(GitFixture fixture) {
        Path root = fixture.root();
        return SpringTypes.over(root, JavaSources.under(root, SOURCES));
    }

    @Test
    void reportsAComponentBuiltWithNew() {
        SpringTypes types = types(
            new GitFixture("wiring-new").write(NEIGHBOUR, SERVICE).write(CALLER, BUILDS_IT)
        );

        List<String> offences = SpringWiringRules.instantiatedComponents(types);

        assertEquals(1, offences.size(), "one source builds the component itself");
        assertTrue(offences.getFirst().contains("Caller.java"), "the offence names the source that built it");
        assertTrue(offences.getFirst().contains("collaborators are null"), "and says what the object lacks");
    }

    @Test
    void leavesAComponentBuiltInATest() {
        SpringTypes types = types(
            new GitFixture("wiring-new-test")
                .write(NEIGHBOUR, SERVICE)
                .write("src/test/java/sample/CallerTest.java", BUILDS_IT)
        );

        assertEquals(
            List.of(), SpringWiringRules.instantiatedComponents(types),
            "building a component directly is how a unit test is meant to be written"
        );
    }

    @Test
    void leavesTheConstructionOfATypeThatIsNoComponent() {
        SpringTypes types = types(
            new GitFixture("wiring-plain").write(NEIGHBOUR, PLAIN).write(CALLER, BUILDS_IT)
        );

        assertEquals(
            List.of(), SpringWiringRules.instantiatedComponents(types),
            "the module declares no component, so nothing here was taken from the container"
        );
    }

    @Test
    void reportsAPrototypeInjectedIntoASingleton() {
        SpringTypes types = types(
            new GitFixture("wiring-prototype").write(SESSION, PROTOTYPE).write(HOLDER, TAKES_IT)
        );

        List<String> offences = SpringWiringRules.prototypesWithoutProviders(types);

        assertEquals(1, offences.size(), "the singleton takes the prototype directly");
        assertTrue(offences.getFirst().contains("resolved once"), "the offence says when the one instance arrives");
    }

    @Test
    void readsPastAParameterAnnotationThatTakesArgumentsOfItsOwn() {
        SpringTypes types = types(
            new GitFixture("wiring-prototype-after-value")
                .write(SESSION, PROTOTYPE)
                .write(HOLDER, TAKES_IT_AFTER_A_VALUE)
        );

        assertEquals(
            1, SpringWiringRules.prototypesWithoutProviders(types).size(),
            "a @Value ahead of the prototype closes a bracket that is not the end of the parameter list"
        );
    }

    @Test
    void leavesAPrototypeTakenThroughAProvider() {
        SpringTypes types = types(
            new GitFixture("wiring-provider").write(SESSION, PROTOTYPE).write(HOLDER, TAKES_A_PROVIDER)
        );

        assertEquals(
            List.of(), SpringWiringRules.prototypesWithoutProviders(types),
            "a provider is asked for an instance per call, which is the scope honoured"
        );
    }

    @Test
    void leavesAModuleThatDeclaresNoPrototype() {
        SpringTypes types = types(
            new GitFixture("wiring-no-prototype")
                .write(SESSION, PLAIN_SESSION)
                .write(HOLDER, TAKES_IT)
        );

        assertEquals(
            List.of(), SpringWiringRules.prototypesWithoutProviders(types),
            "nothing here asks for a scope, so nothing here is denied one"
        );
    }

    @Test
    void leavesAPrototypeTakenByAnotherPrototype() {
        SpringTypes types = types(
            new GitFixture("wiring-prototype-pair")
                .write(SESSION, PROTOTYPE)
                .write(HOLDER, PROTOTYPE_TAKES_IT)
        );

        assertEquals(
            List.of(), SpringWiringRules.prototypesWithoutProviders(types),
            "a bean built per request receives a fresh instance every time it is built"
        );
    }

    @Test
    void reportsAGuardThatNothingEnables() {
        SpringTypes types = types(
            new GitFixture("wiring-guard").write("src/main/java/sample/Guarded.java", GUARDS)
        );

        List<String> offences = SpringWiringRules.unenabledMethodSecurity(types);

        assertEquals(1, offences.size(), "the module guards a method and enables nothing");
        assertTrue(offences.getFirst().contains("runs unguarded"), "the offence says what the guard does");
    }

    @Test
    void leavesAGuardTheModuleEnables() {
        SpringTypes types = types(
            new GitFixture("wiring-guard-enabled")
                .write("src/main/java/sample/Guarded.java", GUARDS)
                .write("src/main/java/sample/Security.java", ENABLES)
        );

        assertEquals(
            List.of(), SpringWiringRules.unenabledMethodSecurity(types),
            "the annotation is read once method security is enabled anywhere in the module"
        );
    }

    @Test
    void reportsAnAsyncWithNoExecutorAnywhereInTheModule() {
        SpringTypes types = types(
            new GitFixture("wiring-async").write("src/main/java/sample/Firing.java", FIRES)
        );

        List<String> offences = SpringWiringRules.unnamedAsyncExecutors(types);

        assertEquals(1, offences.size(), "one method is fired onto nothing in particular");
        assertTrue(offences.getFirst().contains("SimpleAsyncTaskExecutor"), "the offence names the fallback");
    }

    @Test
    void leavesAnAsyncThatNamesItsExecutor() {
        SpringTypes types = types(
            new GitFixture("wiring-async-named").write("src/main/java/sample/Firing.java", NAMES_ITS_EXECUTOR)
        );

        assertEquals(
            List.of(), SpringWiringRules.unnamedAsyncExecutors(types),
            "an executor named is an executor chosen"
        );
    }

    @Test
    void leavesAnAsyncWhenTheModuleDeclaresAnExecutorBean() {
        SpringTypes types = types(
            new GitFixture("wiring-async-pool")
                .write("src/main/java/sample/Firing.java", FIRES)
                .write("src/main/java/sample/Pools.java", DECLARES_A_POOL)
        );

        assertEquals(
            List.of(), SpringWiringRules.unnamedAsyncExecutors(types),
            "the single executor bean of a module is the one Spring will use"
        );
    }

    @Test
    void leavesAnAsyncWhenTheModuleConfiguresOne() {
        SpringTypes types = types(
            new GitFixture("wiring-async-configurer")
                .write("src/main/java/sample/Firing.java", FIRES)
                .write("src/main/java/sample/Pools.java", CONFIGURES_ASYNC)
        );

        assertEquals(
            List.of(), SpringWiringRules.unnamedAsyncExecutors(types),
            "an AsyncConfigurer decides the executor for every unnamed @Async"
        );
    }

    @Test
    void reportsAConfigurationPropertiesTypeNothingRegisters() {
        SpringTypes types = types(new GitFixture("props-orphan").write(SETTINGS_SOURCE, BOUND_SETTINGS));

        List<String> offences = SpringWiringRules.unregisteredProperties(types);

        assertEquals(1, offences.size(), "the annotation builds no bean on its own");
        assertTrue(
            offences.getFirst().contains("keeps the default"),
            "and the offence says what the unbound type leaves behind"
        );
    }

    @Test
    void leavesAConfigurationPropertiesTypeThatIsEnabledByName() {
        SpringTypes types = types(
            new GitFixture("props-enabled")
                .write(SETTINGS_SOURCE, BOUND_SETTINGS)
                .write(ENTRY_SOURCE, ENABLES_THE_SETTINGS)
        );

        assertEquals(
            List.of(), SpringWiringRules.unregisteredProperties(types),
            "the module names the type in @EnableConfigurationProperties"
        );
    }

    @Test
    void leavesEveryConfigurationPropertiesTypeInAModuleThatScansForThem() {
        SpringTypes types = types(
            new GitFixture("props-scanned")
                .write(SETTINGS_SOURCE, BOUND_SETTINGS)
                .write(ENTRY_SOURCE, SCANS_FOR_SETTINGS)
        );

        assertEquals(
            List.of(), SpringWiringRules.unregisteredProperties(types),
            "one scan answers for every such type at once"
        );
    }

    @Test
    void readsTheScanAnnotationAsSomethingOtherThanTheBindingOne() {
        SpringTypes types = types(new GitFixture("props-prefix").write(ENTRY_SOURCE, SCANS_FOR_SETTINGS));

        assertEquals(
            List.of(), SpringWiringRules.unregisteredProperties(types),
            "@ConfigurationPropertiesScan is not a type waiting to be registered"
        );
    }
}
