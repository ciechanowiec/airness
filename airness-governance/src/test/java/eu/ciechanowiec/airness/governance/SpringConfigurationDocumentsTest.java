package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every document of a file is read. A value written in a later document is bound whenever that document
 * applies, so a profile document turning a setting back on is the setting turned on, and a properties file
 * separates its documents with a line Spring Boot reads as a separator rather than as a comment.
 */
class SpringConfigurationDocumentsTest {

    private static final String YAML = "application.yml";
    private static final String PROPERTIES = "application.properties";
    private static final String DUPLICATE = "more than once";

    private static List<String> offences(String file, String content) {
        return SpringConfigurationRules.offences(file, new SpringConfiguration(file, content));
    }

    private static List<Integer> documents(String content) {
        return new SpringConfiguration(PROPERTIES, content).settings().stream()
            .map(SpringConfiguration.Setting::document)
            .toList();
    }

    @Test
    void reportsAWrongValueInALaterProfileDocument() {
        String configuration = """
            spring:
              h2:
                console:
                  enabled: false
            ---
            spring:
              config:
                activate:
                  on-profile: development
              h2:
                console:
                  enabled: true
            """;

        List<String> reported = offences(YAML, configuration);

        assertEquals(1, reported.size(), "the profile publishes the console wherever it is active");
        assertTrue(reported.getFirst().contains("line 12"), "the offence names the line in the later document");
    }

    @Test
    void reportsAWrongValueInEveryDocumentThatWritesIt() {
        String configuration = """
            spring:
              jpa:
                show-sql: true
                open-in-view: false
                hibernate:
                  ddl-auto: validate
            ---
            spring:
              jpa:
                show-sql: true
            """;

        assertEquals(2, offences(YAML, configuration).size(), "each document binds its own value");
    }

    @Test
    void reportsARequiredValueOverriddenInALaterDocument() {
        String configuration = """
            server:
              shutdown: graceful
            ---
            spring:
              config:
                activate:
                  on-profile: production
            server:
              shutdown: immediate
            """;

        List<String> reported = offences(YAML, configuration);

        assertEquals(1, reported.size(), "the production profile cuts requests at the socket");
        assertTrue(reported.getFirst().contains("line 9"), "the offence names the overriding line");
    }

    @Test
    void acceptsAProfileDocumentThatInheritsARequiredKey() {
        String configuration = """
            server:
              shutdown: graceful
            ---
            spring:
              config:
                activate:
                  on-profile: production
            server:
              port: 9090
            """;

        assertEquals(List.of(), offences(YAML, configuration), "the profile inherits the graceful shutdown");
    }

    @Test
    void reportsAProfileDocumentThatOnlyAnotherProfileAnswersFor() {
        String configuration = """
            spring:
              config:
                activate:
                  on-profile: production
            server:
              port: 9090
            ---
            spring:
              config:
                activate:
                  on-profile: development
            server:
              shutdown: graceful
            """;

        List<String> reported = offences(YAML, configuration);

        assertEquals(1, reported.size(), "the production profile never reads the development document");
        assertTrue(reported.getFirst().contains("line 6"), "the offence names where production configures it");
    }

    @Test
    void acceptsARequiredKeyDeclaredInALaterUnconditionalDocument() {
        String configuration = """
            server:
              port: 9090
            ---
            server:
              shutdown: graceful
            """;

        assertEquals(List.of(), offences(YAML, configuration), "an unconditional document always applies");
    }

    @Test
    void readsAPoundSeparatorAsANewPropertiesDocument() {
        String configuration = """
            spring.application.name=default
            #---
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 1), documents(configuration), "the separator opens the second document");
    }

    @Test
    void readsAnExclamationSeparatorAsANewPropertiesDocument() {
        String configuration = """
            spring.application.name=default
            !---
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 1), documents(configuration), "both comment characters write the separator");
    }

    @Test
    void readsASeparatorFollowedByWhitespaceAsANewPropertiesDocument() {
        String configuration = "spring.application.name=default\n#---  \nspring.application.name=overridden\n";

        assertEquals(List.of(0, 1), documents(configuration), "trailing whitespace is allowed after it");
    }

    @Test
    void readsAnIndentedSeparatorAsAComment() {
        String configuration = """
            spring.application.name=default
              #---
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 0), documents(configuration), "a separator starts in the first column");
    }

    @Test
    void readsASeparatorBeneathACommentOfTheSameCharacterAsThatComment() {
        String configuration = """
            spring.application.name=default
            # the overriding document
            #---
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 0), documents(configuration), "Spring Boot continues the comment");
    }

    @Test
    void readsASeparatorBeneathACommentOfTheOtherCharacterAsASeparator() {
        String configuration = """
            spring.application.name=default
            ! the overriding document
            #---
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 1), documents(configuration), "only the same character continues a comment");
    }

    @Test
    void readsASeparatorAfterACommentAndABlankLineAsASeparator() {
        String configuration = """
            spring.application.name=default
            # the overriding document

            #---
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 1), documents(configuration), "the blank line ends the comment");
    }

    @Test
    void readsOnInThePreviousDocumentWhenACommentFollowsTheSeparator() {
        String configuration = """
            spring.application.name=default
            #---
            # the overriding document
            spring.application.name=overridden
            """;

        assertEquals(List.of(0, 0), documents(configuration), "Spring Boot reopens the document before");
    }

    @Test
    void readsAnExclamationLineAsAPropertiesComment() {
        String configuration = """
            ! a remark about the name
            spring.application.name=default
            """;

        SpringConfiguration read = new SpringConfiguration(PROPERTIES, configuration);

        assertEquals(List.of(), read.unreadable(), "the remark is a comment rather than a line to report");
    }

    @Test
    void acceptsAKeyRedeclaredInALaterPropertiesDocument() {
        String configuration = """
            spring.application.name=default
            #---
            spring.config.activate.on-profile=production
            spring.application.name=overridden
            """;

        assertEquals(
            List.of(),
            offences(PROPERTIES, configuration).stream().filter(offence -> offence.contains(DUPLICATE)).toList(),
            "a later document overriding the first is how a profile is written"
        );
    }

    @Test
    void reportsAWrongValueInALaterPropertiesDocument() {
        String configuration = """
            spring.jpa.show-sql=false
            spring.jpa.open-in-view=false
            spring.jpa.hibernate.ddl-auto=validate
            #---
            spring.config.activate.on-profile=development
            spring.jpa.show-sql=true
            """;

        List<String> reported = offences(PROPERTIES, configuration);

        assertEquals(1, reported.size(), "the profile writes every statement to standard output");
        assertTrue(reported.getFirst().contains("line 6"), "the offence names the line in the later document");
    }
}
