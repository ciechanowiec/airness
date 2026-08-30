package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringConfigurationCheckTest {

    private static final String YAML = "src/main/resources/application.yml";
    private static final String PROFILE = "src/main/resources/application-production.yml";
    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));

    private static final String CLEAN = """
        spring:
          application:
            name: example
        """;

    private static final String EXPOSED = """
        management:
          endpoints:
            web:
              exposure:
                include: "*"
        """;

    private static final String UNREADABLE = """
        management
        """;

    private static final List<ConfigurationProperty> PUBLISHED = List.of(
        declared("spring.application.name"),
        declared("management.endpoints.web.exposure.include")
    );

    private static SpringConfigurationCheck check(Path root) {
        return new SpringConfigurationCheck(root, RESOURCES, PUBLISHED);
    }

    private static ConfigurationProperty declared(String name) {
        return new ConfigurationProperty(
            name, "java.lang.String", false, new ConfigurationProperty.Deprecation("", "", "", "")
        );
    }

    @Test
    void reportsNothingAboutAConfigurationThatDecidesNothingRisky() {
        Path root = new GitFixture("config-clean").write(YAML, CLEAN).root();

        assertTrue(
            Verdicts.clean(check(root).findings()),
            "nothing here takes on an obligation it then fails"
        );
    }

    @Test
    void reportsASettingThatDecidesTheWrongThing() {
        Path root = new GitFixture("config-exposed").write(YAML, EXPOSED).root();

        List<Findings> findings = check(root).findings();

        assertEquals(1, Verdicts.offences(findings, "Runtime settings").size(), "the wildcard is reported");
    }

    @Test
    void namesTheFileEveryOffenceCameFrom() {
        Path root = new GitFixture("config-named").write(YAML, EXPOSED).root();

        List<String> offences = Verdicts.offences(
            check(root).findings(), "Runtime settings"
        );

        assertTrue(offences.getFirst().startsWith(YAML), "the offence opens with the path");
    }

    @Test
    void reportsALineTheReaderCouldNotAccountFor() {
        Path root = new GitFixture("config-unreadable").write(YAML, UNREADABLE).root();

        List<Findings> findings = check(root).findings();

        assertEquals(1, Verdicts.offences(findings, "could not account").size(), "the line is reported");
    }

    @Test
    void readsEveryProfileFileBesideTheDefaultOne() {
        Path root = new GitFixture("config-counted")
            .write(YAML, CLEAN)
            .write(PROFILE, CLEAN)
            .root();

        assertEquals(2, check(root).scanned(), "both are in scope");
    }

    @Test
    void readsNothingWhenTheModuleCarriesNoConfiguration() {
        Path root = new GitFixture("config-absent").write("src/main/java/A.java", "class A {}").root();

        assertEquals(0, check(root).scanned(), "there is none to read");
    }

    @Test
    void readsNothingOutsideTheResourceRootsItWasGiven() {
        Path root = new GitFixture("config-elsewhere").write("config/application.yml", EXPOSED).root();

        assertEquals(0, check(root).scanned(), "that root was not named");
    }

    @Test
    void readsNothingFromAResourceThatIsNotApplicationConfiguration() {
        Path root = new GitFixture("config-other")
            .write("src/main/resources/logback-spring.xml", "<configuration/>")
            .root();

        assertEquals(0, check(root).scanned(), "only application files");
    }

    @Test
    void readsNothingFromAFileNamedLikeConfigurationButFormatNothingReads() {
        Path root = new GitFixture("config-shape")
            .write("src/main/resources/application-notes.txt", "not configuration")
            .root();

        assertEquals(0, check(root).scanned(), "the format is not one");
    }

    @Test
    void reportsASettingTheContainerHasStoppedBinding() {
        Path root = new GitFixture("config-unbound").write(YAML, CLEAN).root();
        List<ConfigurationProperty> withdrawn = List.of(
            new ConfigurationProperty(
                "spring.application.name", "java.lang.String", false,
                new ConfigurationProperty.Deprecation("error", "spring.application.title", "", "4.0.0")
            )
        );

        List<Findings> findings = new SpringConfigurationCheck(root, RESOURCES, withdrawn).findings();

        assertEquals(1, Verdicts.offences(findings, "stopped binding").size(), "the key is not bound");
    }

    @Test
    void reportsAConfigurationNothingOnTheClasspathCouldAccountFor() {
        Path root = new GitFixture("config-unread").write(YAML, CLEAN).root();

        List<Findings> findings = new SpringConfigurationCheck(root, RESOURCES, List.of()).findings();

        assertEquals(
            1, Verdicts.offences(findings, "could account").size(),
            "a file judged against nothing is not a file known to be right"
        );
    }
}
