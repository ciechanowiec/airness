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
    private static final String ENTITY = "src/main/java/sample/Order.java";
    private static final String CONTROLLER = "src/main/java/sample/Orders.java";
    private static final String CARRIED = "carried by a web request or response";

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

    @Test
    void reportsAnEntityReturnedFromAController() {
        Path root = new GitFixture("module-returns")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, RETURNS_THE_ENTITY)
            .root();

        List<String> offences = Verdicts.offences(new SpringModuleCheck(root, MAIN).findings(), CARRIED);

        assertEquals(1, offences.size(), "one handler returns the entity");
        assertTrue(offences.getFirst().contains("republishes the schema"), "the offence says what it costs");
    }

    @Test
    void reportsAnEntityAcceptedAsARequestBody() {
        Path root = new GitFixture("module-accepts")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, ACCEPTS_THE_ENTITY)
            .root();

        List<String> offences = Verdicts.offences(new SpringModuleCheck(root, MAIN).findings(), CARRIED);

        assertEquals(1, offences.size(), "one handler binds the entity");
        assertTrue(offences.getFirst().contains("every column"), "the offence says what a caller may set");
    }

    @Test
    void leavesATypeThatMerelyBeginsWithAnEntityName() {
        Path root = new GitFixture("module-summary")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, CARRIES_ITS_OWN_TYPE)
            .root();

        assertTrue(
            Verdicts.clean(new SpringModuleCheck(root, MAIN).findings()),
            "OrderSummary is a type of its own rather than the entity it is named after"
        );
    }

    @Test
    void leavesAControllerAloneWhenTheModuleDeclaresNoEntity() {
        Path root = new GitFixture("module-no-entity").write(CONTROLLER, RETURNS_THE_ENTITY).root();

        SpringModuleCheck check = new SpringModuleCheck(root, MAIN);

        assertEquals(1, check.types(), "the controller is the only type declared");
        assertTrue(Verdicts.clean(check.findings()), "nothing here is an entity, so nothing here is exposed");
    }

    @Test
    void reportsAnEmptyScopeRatherThanACleanModule() {
        Path root = new GitFixture("module-empty").write(ENTITY, ORDER).root();

        SpringModuleCheck check = new SpringModuleCheck(root, List.of(Path.of("src/main/kotlin")));

        assertEquals(0, check.scanned(), "a root that names nothing read nothing");
        assertTrue(Verdicts.clean(check.findings()), "which is why the caller refuses a zero scope");
    }

    @Test
    void readsPastASourceThatDeclaresNoType() {
        Path root = new GitFixture("module-package-info")
            .write(ENTITY, ORDER)
            .write("src/main/java/sample/package-info.java", NO_TYPE)
            .root();

        SpringModuleCheck check = new SpringModuleCheck(root, MAIN);

        assertEquals(2, check.scanned(), "both files are Java sources");
        assertEquals(1, check.types(), "and only one of them declares a type");
    }

    @Test
    void leavesAMappingThatIntroducesNoSignature() {
        Path root = new GitFixture("module-odd-mapping")
            .write(ENTITY, ORDER)
            .write("src/main/java/sample/Odd.java", MAPPING_ON_A_FIELD)
            .root();

        assertTrue(
            Verdicts.clean(new SpringModuleCheck(root, MAIN).findings()),
            "a mapping that introduces no parameter list names no return type to read"
        );
    }

    @Test
    void countsTheSourcesAndTheTypesItRead() {
        Path root = new GitFixture("module-scope")
            .write(ENTITY, ORDER)
            .write(CONTROLLER, RETURNS_THE_ENTITY)
            .root();

        SpringModuleCheck check = new SpringModuleCheck(root, MAIN);

        assertEquals(2, check.scanned(), "both sources lie under the root given");
        assertEquals(2, check.types(), "and each of them declares a type");
    }
}
