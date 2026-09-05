package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The wiring rules read a declaration in one file against a use in another. Each test below writes both
 * halves, because either half alone is an ordinary source that no rule has anything to say about.
 */
class SpringWiringRulesTest {

    private static final List<Path> SOURCES = List.of(Path.of("src"));
    private static final int MANY = 800;
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

    private static String takesItAfterManyOthers() {
        return """
            package sample;

            @Service
            class Holder {

                Holder(
            %s        Session session
                ) {
                }
            }
            """.formatted("        @Value(\"${retries}\") int retries,\n".repeat(MANY));
    }

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
    void readsAConstructorDeclaredOverManyLines() {
        SpringTypes types = types(
            new GitFixture("wiring-long").write(SESSION, PROTOTYPE).write(HOLDER, takesItAfterManyOthers())
        );

        Optional<List<String>> offences = BoundedStack.read(
            () -> SpringWiringRules.prototypesWithoutProviders(types)
        );

        assertTrue(offences.isPresent(), "a parameter list is read in runs, so its length costs the scan no depth");
        assertEquals(1, offences.orElseThrow().size(), "and the prototype declared last in it is still found");
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
