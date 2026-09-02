package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringFactoriesRulesTest {

    private static final Path FILE = Path.of("src/main/resources/META-INF/spring.factories");
    private static final String KEY
        = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";

    @Test
    void reportsTheAutoConfigurationKeyBootNoLongerReads() {
        List<String> offences = SpringFactoriesRules.unsupported(
            FILE, KEY + "=com.example.First,com.example.Second\n"
        );

        assertEquals(1, offences.size(), "the removed key is present");
        assertTrue(offences.getFirst().contains("AutoConfiguration.imports"), "the replacement is named");
    }

    @Test
    void readsAContinuedPropertiesValue() {
        String content = KEY + "=com.example.First,\\\n  com.example.Second\n";

        assertEquals(1, SpringFactoriesRules.unsupported(FILE, content).size(), "Properties joins the value");
    }

    @Test
    void acceptsAnotherFactoriesKey() {
        String content = "org.springframework.context.ApplicationListener=com.example.Listener\n";

        assertEquals(List.of(), SpringFactoriesRules.unsupported(FILE, content), "the key still binds");
    }

    @Test
    void refusesMalformedPropertiesRatherThanPassingThemOver() {
        String malformed = KEY + "=\\u12\n";

        assertThrows(
            IllegalArgumentException.class,
            () -> SpringFactoriesRules.unsupported(FILE, malformed),
            "an unreadable resource cannot produce a clean verdict"
        );
    }

    @Test
    void exposesTheFactoriesRuleThroughTheExistingModelCheck() {
        GitFixture fixture = new GitFixture("model-factories")
            .write("pom.xml", "<project/>\n")
            .write(FILE.toString(), KEY + "=com.example.Own\n");
        SpringModelCheck check = new SpringModelCheck(
            fixture.root().resolve("pom.xml"), List.of(Path.of("src/main/resources")),
            List.of(), false
        );

        List<String> offences = Verdicts.offences(
            check.findings(), "registrations Boot no longer reads"
        );

        assertEquals(1, offences.size(), "the existing model goal exposes the resource rule");
        assertTrue(offences.getFirst().startsWith(FILE.toString()), "the finding names the resource");
    }
}
