package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The module check reads every source before judging any, and reports an entity that a controller
 * accepts or returns. A type merely named like an entity is not one, which is the false positive that
 * would otherwise make the rule unusable in a project that names its response types after its tables.
 */
class SpringModuleCheckTest {

    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));
    private static final String ENTITY = "src/main/java/sample/Order.java";
    private static final String CONTROLLER = "src/main/java/sample/Orders.java";
    private static final String CARRIED = "carried by a web request or response";
    private static final String ADVICE = "src/main/java/sample/Errors.java";
    private static final String REPOSITORY = "src/main/java/sample/OrderRepository.java";
    private static final String SUITE = "src/test/java/sample/SuiteTest.java";
    private static final List<Path> BOTH = List.of(Path.of("src"));
    private static final String ADVISED = "left to the framework's own error page";
    private static final String REACHED = "holding the repository layer directly";
    private static final String PROFILED = "Test profiles activated";
    private static final String UNACTIVATED = "Test profile files that nothing activates";
    private static final String UNREGISTERED = "Configuration property types nothing";
    private static final List<Path> TREES
        = List.of(Path.of("src/main/resources"), Path.of("src/test/resources"));

    private static final String BOUND_SETTINGS = """
        package sample;

        @ConfigurationProperties(prefix = "acme")
        class Settings {
        }
        """;

    private static final String ORDER = """
        package sample;

        @Entity
        class Order {

            @Id
            @GeneratedValue
            private Long id;
        }
        """;

    private static final String RETURNS_THE_ENTITY = """
        package sample;

        @RestController
        class Orders {

            @GetMapping("/orders")
            public List<Order> all() {
                return List.of();
            }
        }
        """;

    private static final String ACCEPTS_THE_ENTITY = """
        package sample;

        @RestController
        class Orders {

            @PostMapping("/orders")
            public void accept(@RequestBody Order order) {
            }
        }
        """;

    private static final String HANDLES_THE_ERRORS = """
        package sample;

        @RestControllerAdvice
        class Errors {
        }
        """;

    private static final String SPRING_DATA = """
        package sample;

        interface OrderRepository extends JpaRepository<Order, Long> {
        }
        """;

    private static final String TAKES_THE_REPOSITORY = """
        package sample;

        @RestController
        class Orders {

            Orders(OrderRepository orders) {
            }
        }
        """;

    private static final String ACTIVATES_TWO_PROFILES = """
        package sample;

        @ActiveProfiles({"integration", "staging"})
        class SuiteTest {
        }
        """;

    private static final String NO_TYPE = """
        /**
         * A package that declares no type of its own.
         */
        package sample;
        """;

    private static final String MAPPING_ON_A_FIELD = """
        package sample;

        @RestController
        class Odd {

            @GetMapping
            private String path;
        }
        """;

    private static final String CARRIES_ITS_OWN_TYPE = """
        package sample;

        @RestController
        class Orders {

            @GetMapping("/orders")
            public List<OrderSummary> all() {
                return List.of();
            }

            @PostMapping("/orders")
            public void accept(@RequestBody OrderSummary summary) {
            }
        }
        """;

    private static SpringModuleCheck check(Path root) {
        return new SpringModuleCheck(root, MAIN, RESOURCES);
    }

    @Test
    void reportsAnEntityReturnedFromAController() {
        Path root = new GitFixture("module-returns")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, RETURNS_THE_ENTITY)
            .root();

        List<String> offences = Verdicts.offences(check(root).findings(), CARRIED);

        assertEquals(1, offences.size(), "one handler returns the entity");
        assertTrue(offences.getFirst().contains("republishes the schema"), "the offence says what it costs");
    }

    @Test
    void reportsAnEntityAcceptedAsARequestBody() {
        Path root = new GitFixture("module-accepts")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, ACCEPTS_THE_ENTITY)
            .root();

        List<String> offences = Verdicts.offences(check(root).findings(), CARRIED);

        assertEquals(1, offences.size(), "one handler binds the entity");
        assertTrue(offences.getFirst().contains("every column"), "the offence says what a caller may set");
    }

    @Test
    void leavesATypeThatMerelyBeginsWithAnEntityName() {
        Path root = new GitFixture("module-summary")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, CARRIES_ITS_OWN_TYPE)
            .root();

        assertEquals(
            List.of(), Verdicts.offences(check(root).findings(), CARRIED),
            "OrderSummary is a type of its own rather than the entity it is named after"
        );
    }

    @Test
    void leavesAControllerAloneWhenTheModuleDeclaresNoEntity() {
        Path root = new GitFixture("module-no-entity").write(CONTROLLER, RETURNS_THE_ENTITY).root();

        SpringModuleCheck check = check(root);

        assertEquals(1, check.types(), "the controller is the only type declared");
        assertEquals(
            List.of(), Verdicts.offences(check.findings(), CARRIED),
            "nothing here is an entity, so nothing here is exposed"
        );
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanModule() {
        Path root = new GitFixture("module-empty").write(ENTITY, ORDER).root();

        SpringModuleCheck check = new SpringModuleCheck(
            root, List.of(Path.of("src/main/kotlin")), RESOURCES
        );

        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(Verdicts.clean(check.findings()), "which is why the caller refuses a zero scope");
    }

    @Test
    void readsPastASourceThatDeclaresNoType() {
        Path root = new GitFixture("module-package-info")
            .write(ENTITY, ORDER)
            .write("src/main/java/sample/package-info.java", NO_TYPE)
            .root();

        SpringModuleCheck check = check(root);

        assertEquals(2, check.scanned(), "both files are Java sources");
        assertEquals(1, check.types(), "and only one of them declares a type");
    }

    @Test
    void leavesAMappingThatIntroducesNoSignature() {
        Path root = new GitFixture("module-odd-mapping")
            .write(ENTITY, ORDER)
            .write("src/main/java/sample/Odd.java", MAPPING_ON_A_FIELD)
            .root();

        assertEquals(
            List.of(), Verdicts.offences(check(root).findings(), CARRIED),
            "a mapping that introduces no parameter list names no return type to read"
        );
    }

    @Test
    void countsTheSourcesAndTheTypesItRead() {
        Path root = new GitFixture("module-scope")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, RETURNS_THE_ENTITY)
            .root();

        SpringModuleCheck check = check(root);

        assertEquals(2, check.scanned(), "both sources lie under the root given");
        assertEquals(2, check.types(), "and each of them declares a type");
    }

    @Test
    void reportsAModuleWhoseControllersAreAdvisedByNothing() {
        Path root = new GitFixture("module-unadvised").write(CONTROLLER, RETURNS_THE_ENTITY).root();

        List<String> offences = Verdicts.offences(check(root).findings(), ADVISED);

        assertEquals(1, offences.size(), "the module is missing one declaration, so it is named once");
        assertTrue(offences.getFirst().contains("default error page"), "the offence says what answers instead");
    }

    @Test
    void leavesAModuleThatDeclaresAnAdvice() {
        Path root = new GitFixture("module-advised")
            .write(CONTROLLER, RETURNS_THE_ENTITY)
            .write(ADVICE, HANDLES_THE_ERRORS)
            .root();

        assertEquals(
            List.of(), Verdicts.offences(check(root).findings(), ADVISED),
            "one advice answers for every controller of the module"
        );
    }

    @Test
    void reportsAControllerHoldingARepository() {
        Path root = new GitFixture("module-repository")
            .write(REPOSITORY, SPRING_DATA)
            .write(CONTROLLER, TAKES_THE_REPOSITORY)
            .root();

        List<String> offences = Verdicts.offences(check(root).findings(), REACHED);

        assertEquals(1, offences.size(), "the controller declares the repository among its collaborators");
        assertTrue(offences.getFirst().contains("transaction boundary"), "the offence says what moves with it");
    }

    @Test
    void leavesAControllerThatNamesNoRepository() {
        Path root = new GitFixture("module-no-repository")
            .write(REPOSITORY, SPRING_DATA)
            .write(CONTROLLER, RETURNS_THE_ENTITY)
            .root();

        assertEquals(
            List.of(), Verdicts.offences(check(root).findings(), REACHED),
            "a module holding a repository is not a module whose controllers reach it"
        );
    }

    @Test
    void readsTheResourceTreesForTheProfilesTheTestsActivate() {
        Path root = new GitFixture("module-profiles")
            .write(SUITE, ACTIVATES_TWO_PROFILES)
            .write("src/main/resources/application-integration.yml", "spring:\n")
            .root();

        List<String> offences = Verdicts.offences(
            new SpringModuleCheck(root, BOTH, RESOURCES).findings(), PROFILED
        );

        assertEquals(1, offences.size(), "one of the two profiles has a file and the other has none");
        assertTrue(offences.getFirst().contains("staging"), "and it is the one without the file that is named");
    }

    @Test
    void reportsATestProfileFileThatNoSuiteActivates() {
        Path root = new GitFixture("module-profile-orphan")
            .write(SUITE, ACTIVATES_TWO_PROFILES)
            .write("src/test/resources/application-forgotten.yml", "spring:\n")
            .root();

        List<String> offences = Verdicts.offences(
            new SpringModuleCheck(root, BOTH, TREES).findings(), UNACTIVATED
        );

        assertEquals(1, offences.size(), "the file answers a profile nothing selects");
        assertTrue(offences.getFirst().contains("forgotten"), "and the offence names it");
    }

    @Test
    void leavesAProfileFileUnderTheMainResourcesAlone() {
        Path root = new GitFixture("module-profile-deployed")
            .write(SUITE, ACTIVATES_TWO_PROFILES)
            .write("src/main/resources/application-forgotten.yml", "spring:\n")
            .root();

        assertEquals(
            List.of(), Verdicts.offences(new SpringModuleCheck(root, BOTH, TREES).findings(), UNACTIVATED),
            "a deployment this repository does not hold may be what selects it"
        );
    }

    @Test
    void reportsAConfigurationPropertiesTypeTheModuleNeverRegisters() {
        Path root = new GitFixture("module-props")
            .write("src/main/java/sample/Settings.java", BOUND_SETTINGS)
            .root();

        List<String> offences = Verdicts.offences(check(root).findings(), UNREGISTERED);

        assertEquals(1, offences.size(), "the annotation alone builds no bean");
    }
}
