package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The model rules read what a module declares it is built from. Four of the six ask first whether the
 * Boot plugin repackages the module, because a library of a Spring Boot project legitimately maps a
 * schema it does not create and legitimately publishes none of the endpoints a deployment needs.
 */
class SpringModelCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));
    private static final String BOOT = "org.springframework.boot";
    private static final String POM = "pom.xml";
    private static final String MINIMAL = "<project/>\n";
    private static final String IMPORTS
        = "src/main/resources/META-INF/spring/"
            + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String SHIPPED = "Development tooling declared";
    private static final String SCHEMA = "nothing declared that would create it";
    private static final String ACTUATOR = "publishing nothing an orchestrator can read";
    private static final String WEB = "spring-boot-starter-web";
    private static final String SECURITY = "spring-boot-starter-security";
    private static final String AUTHENTICATION = "nothing that authenticates a caller";
    private static final String STACKS = "Both web stacks declared";
    private static final String OWN = "Auto-configuration declared inside";

    private static final boolean DEPLOYED = true;
    private static final boolean LIBRARY = false;

    private static SpringDependency starter(String artifact) {
        return new SpringDependency(BOOT, artifact, false);
    }

    private static List<Findings> findings(
        GitFixture fixture, Collection<SpringDependency> dependencies, boolean repackaged
    ) {
        Path root = fixture.write(POM, MINIMAL).root();
        return new SpringModelCheck(root.resolve(POM), RESOURCES, dependencies, repackaged).findings();
    }

    @Test
    void reportsDevelopmentToolingThatIsNotOptional() {
        List<String> offences = Verdicts.offences(
            findings(
                new GitFixture("model-devtools"),
                List.of(new SpringDependency(BOOT, "spring-boot-devtools", false)), LIBRARY
            ),
            SHIPPED
        );

        assertEquals(1, offences.size(), "the declaration lets the tooling travel");
        assertTrue(offences.getFirst().contains("remote debug endpoint"), "the offence says what travels");
    }

    @Test
    void leavesDevelopmentToolingDeclaredOptional() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-devtools-optional"),
                    List.of(new SpringDependency(BOOT, "spring-boot-devtools", true)), LIBRARY
                ),
                SHIPPED
            ),
            "optional is what keeps the tooling out of everything downstream"
        );
    }

    @Test
    void reportsAMappedSchemaWithNothingToCreateIt() {
        List<String> offences = Verdicts.offences(
            findings(
                new GitFixture("model-schema"), List.of(starter("spring-boot-starter-data-jpa")), DEPLOYED
            ),
            SCHEMA
        );

        assertEquals(1, offences.size(), "the module maps a schema and declares no migration tool");
        assertTrue(offences.getFirst().contains("by hand at deploy time"), "the offence says who creates it");
    }

    @Test
    void leavesAMappedSchemaAMigrationToolCreates() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-schema-flyway"),
                    List.of(
                        starter("spring-boot-starter-data-jpa"),
                        new SpringDependency("org.flywaydb", "flyway-core", false)
                    ),
                    DEPLOYED
                ),
                SCHEMA
            ),
            "a migration tool is what creates the schema the mapping was written against"
        );
    }

    @Test
    void leavesAMappedSchemaInAModuleThatIsNotDeployed() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-schema-library"),
                    List.of(starter("spring-boot-starter-data-jpa")), LIBRARY
                ),
                SCHEMA
            ),
            "a library maps the schema and the application that ships it creates it"
        );
    }

    @Test
    void reportsADeployableApplicationWithNoActuator() {
        List<String> offences = Verdicts.offences(
            findings(new GitFixture("model-actuator"), List.of(starter(WEB)), DEPLOYED),
            ACTUATOR
        );

        assertEquals(1, offences.size(), "the module is deployed and publishes no probes");
        assertTrue(offences.getFirst().contains("readiness probe"), "the offence names what is absent");
    }

    @Test
    void leavesADeployableApplicationThatDeclaresTheActuator() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-actuator-declared"),
                    List.of(starter(WEB), starter("spring-boot-starter-actuator")),
                    DEPLOYED
                ),
                ACTUATOR
            ),
            "the starter is what publishes the probes an orchestrator reads"
        );
    }

    @Test
    void leavesADeployableApplicationThatServesNoHttp() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-actuator-batch"),
                    List.of(starter("spring-boot-starter-batch")), DEPLOYED
                ),
                ACTUATOR
            ),
            "a batch job is repackaged like a service and has no port to publish a probe on"
        );
    }

    @Test
    void readsTheReactiveStarterAsAWebStackTheProbesWouldBeServedOn() {
        assertEquals(
            1,
            Verdicts.offences(
                findings(
                    new GitFixture("model-actuator-reactive"),
                    List.of(starter("spring-boot-starter-webflux")), DEPLOYED
                ),
                ACTUATOR
            ).size(),
            "either stack serves the endpoints, so either one is asked for them"
        );
    }

    @Test
    void reportsBothWebStacksDeclaredAtOnce() {
        List<String> offences = Verdicts.offences(
            findings(
                new GitFixture("model-stacks"),
                List.of(
                    starter(WEB), starter("spring-boot-starter-webflux"),
                    starter("spring-boot-starter-actuator")
                ),
                DEPLOYED
            ),
            STACKS
        );

        assertEquals(1, offences.size(), "one module declares both");
        assertTrue(offences.getFirst().contains("mapped by nothing"), "the offence says which half loses");
    }

    @Test
    void readsTheServletStarterUnderTheNameBootFourGaveIt() {
        assertEquals(
            1,
            Verdicts.offences(
                findings(
                    new GitFixture("model-stacks-renamed"),
                    List.of(
                        starter("spring-boot-starter-webmvc"), starter("spring-boot-starter-webflux"),
                        starter("spring-boot-starter-actuator")
                    ),
                    DEPLOYED
                ),
                STACKS
            ).size(),
            "the platform kept both spellings, so a rule that knew one would pass over half of them"
        );
    }

    @Test
    void leavesAModuleThatDeclaresOneWebStack() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-one-stack"), List.of(starter("spring-boot-starter-webflux")),
                    LIBRARY
                ),
                STACKS
            ),
            "one stack is a choice made rather than a conflict settled"
        );
    }

    @Test
    void reportsAutoConfigurationTheApplicationPublishesToItself() {
        GitFixture fixture = new GitFixture("model-imports").write(IMPORTS, "com.example.Own\n");

        List<String> offences = Verdicts.offences(
            findings(fixture, List.of(starter("spring-boot-starter-actuator")), DEPLOYED), OWN
        );

        assertEquals(1, offences.size(), "the registration file is the declaration");
        assertTrue(offences.getFirst().contains(".imports"), "the offence names the file rather than the pom");
    }

    @Test
    void leavesAutoConfigurationInAModuleThatIsNotDeployed() {
        GitFixture fixture = new GitFixture("model-imports-starter").write(IMPORTS, "com.example.Own\n");

        assertEquals(
            List.of(),
            Verdicts.offences(findings(fixture, List.of(), LIBRARY), OWN),
            "a module that is not repackaged is what a starter is"
        );
    }

    @Test
    void readsPastAResourceThatRegistersNothing() {
        GitFixture fixture = new GitFixture("model-other-resource")
            .write("src/main/resources/application.yml", "spring:\n");

        assertEquals(
            List.of(),
            Verdicts.offences(findings(fixture, List.of(), DEPLOYED), OWN),
            "an ordinary resource registers no auto-configuration"
        );
    }

    @Test
    void countsWhatItReadAndWhetherTheModuleIsDeployed() {
        Path root = new GitFixture("model-scope").write(POM, MINIMAL).root();

        SpringModelCheck check = new SpringModelCheck(
            root.resolve(POM), RESOURCES, List.of(starter(WEB)), LIBRARY
        );

        assertEquals(1, check.scanned(), "one dependency was declared");
        assertFalse(check.repackaged(), "and nothing repackages this module");
    }

    @Test
    void reportsADeployedServiceThatAuthenticatesNobody() {
        List<String> offences = Verdicts.offences(
            findings(
                new GitFixture("model-unauthenticated"), List.of(starter(WEB)),
                DEPLOYED
            ),
            AUTHENTICATION
        );

        assertEquals(1, offences.size(), "the module serves HTTP and declares no security");
        assertTrue(
            offences.getFirst().contains("no chain to read"),
            "the offence says why the other security rules stay silent"
        );
    }

    @Test
    void leavesEveryShapeThatIsNotADeployedServiceWithoutSecurity() {
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-authenticated"), List.of(starter(WEB), starter(SECURITY)),
                    DEPLOYED
                ),
                AUTHENTICATION
            ),
            "the starter is what gives the filter chain rules something to read"
        );
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-unauthenticated-batch"),
                    List.of(starter("spring-boot-starter-data-jpa")), DEPLOYED
                ),
                AUTHENTICATION
            ),
            "a batch job answers nobody, so there is nobody to authenticate"
        );
        assertEquals(
            List.of(),
            Verdicts.offences(
                findings(
                    new GitFixture("model-unauthenticated-library"), List.of(starter(WEB)), LIBRARY
                ),
                AUTHENTICATION
            ),
            "a library is mapped into the application that deploys it and secures it"
        );
    }
}
