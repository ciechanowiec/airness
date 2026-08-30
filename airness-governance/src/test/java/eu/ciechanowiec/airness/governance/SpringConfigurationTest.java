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

    @Test
    void readsAValueWithoutTheCommentWrittenBesideIt() {
        String content = """
            spring:
              jpa:
                show-sql: false # left off until the query log is wanted
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            "false", read.settings().getFirst().value(),
            "the note beside the value is not part of the value a rule compares"
        );
    }

    @Test
    void keepsAHashThatSitsInsideAQuotedValue() {
        String content = """
            spring:
              datasource:
                url: "jdbc:postgresql://host/db?options=#one"
            """;

        SpringConfiguration read = yaml(content);

        assertTrue(
            read.settings().getFirst().value().endsWith("#one\""),
            "a quoted hash is a character of the value rather than the start of a comment"
        );
    }

    @Test
    void keepsAHashThatNoWhitespacePrecedes() {
        String content = """
            spring:
              application:
                name: teron#one
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            "teron#one", read.settings().getFirst().value(),
            "YAML opens a comment only where whitespace precedes the hash"
        );
    }

    @Test
    void countsTheDocumentsASeparatorDivides() {
        String content = """
            spring:
              jpa:
                open-in-view: false
            ---
            spring:
              jpa:
                open-in-view: true
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            List.of(0, 1), read.settings().stream().map(SpringConfiguration.Setting::document).toList(),
            "the same key in two documents is one override rather than one key written twice"
        );
    }

    @Test
    void reportsASequenceEntryRatherThanReadingItAsAKey() {
        String content = """
            spring:
              cloud:
                routes:
                  - name: first
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(1, read.unreadable().size(), "the reader does not model a sequence");
        assertTrue(
            read.settings().isEmpty(),
            "and it makes no key out of the dash rather than recording one nothing could match"
        );
    }
}
