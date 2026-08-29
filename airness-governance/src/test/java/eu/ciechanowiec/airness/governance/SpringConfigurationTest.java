package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringConfigurationTest {

    private static SpringConfiguration yaml(String content) {
        return new SpringConfiguration("application.yml", content);
    }

    @Test
    void flattensANestedMapIntoDottedKeys() {
        String content = """
            spring:
              jpa:
                open-in-view: false
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            "spring.jpa.openinview", read.settings().getFirst().key(),
            "the nesting becomes one dotted canonical key"
        );
    }

    @Test
    void readsEverySpellingOfOneKeyAsTheSameSetting() {
        String content = """
            spring:
              jpa:
                openInView: false
            """;

        SpringConfiguration read = yaml(content);

        assertTrue(read.declared("spring.jpa.open-in-view").isPresent(), "Spring binds both spellings");
    }

    @Test
    void keepsTheKeyAsTheProjectWroteIt() {
        String content = """
            spring:
              jpa:
                openInView: false
            """;

        SpringConfiguration read = yaml(content);

        assertEquals("openInView", read.settings().getFirst().raw(), "the written form is kept");
    }

    @Test
    void passesOverCommentsBlankLinesAndDocumentSeparators() {
        String content = """
            # a comment
            ---

            server:
              port: 8080
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(1, read.settings().size(), "only the setting is a setting");
        assertEquals(List.of(), read.unreadable(), "nothing here is unaccounted for");
    }

    @Test
    void reportsALineItCannotAccountFor() {
        String content = """
            server
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(1, read.unreadable().size(), "a line with no separator is reported, not skipped");
    }

    @Test
    void readsAPropertiesFile() {
        SpringConfiguration read = new SpringConfiguration(
            "application.properties", "spring.jpa.open-in-view=false\n"
        );

        assertEquals("false", read.declared("spring.jpa.open-in-view").orElseThrow().value(), "read");
    }

    @Test
    void readsAPropertiesFileWrittenWithColons() {
        SpringConfiguration read = new SpringConfiguration(
            "application.properties", "server.shutdown: graceful\n"
        );

        assertEquals("graceful", read.declared("server.shutdown").orElseThrow().value(), "either works");
    }

    @Test
    void reportsAPropertiesLineThatAssignsNothing() {
        SpringConfiguration read = new SpringConfiguration("application.properties", "server\n");

        assertEquals(1, read.unreadable().size(), "the line assigns nothing");
    }

    @Test
    void answersWhetherASubsystemIsConfiguredAtAll() {
        String content = """
            spring:
              jpa:
                show-sql: false
            """;

        SpringConfiguration read = yaml(content);

        assertTrue(read.configures("spring.jpa"), "the prefix is configured");
        assertFalse(read.configures("management"), "this one is not");
    }

    @Test
    void leavesAParentKeyOutOfTheSettingsItReports() {
        String content = """
            spring:
              jpa:
                show-sql: false
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(1, read.settings().size(), "only the leaf carries a value");
    }
}
