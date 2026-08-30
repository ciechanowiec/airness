package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringMetadataRulesTest {

    private static final String FILE = "application.yml";

    private static final String NAMED = """
        spring:
          application:
            name: example
        """;

    private static SpringConfiguration read(String content) {
        return new SpringConfiguration(FILE, content);
    }

    private static SpringMetadata metadata(ConfigurationProperty... published) {
        return new SpringMetadata(List.of(published));
    }

    private static ConfigurationProperty withdrawn(String level, String replacement, String reason, String since) {
        return new ConfigurationProperty(
            "spring.application.name", "java.lang.String", false,
            new ConfigurationProperty.Deprecation(level, replacement, reason, since)
        );
    }

    private static ConfigurationProperty group() {
        return new ConfigurationProperty("spring.application", "com.example.Settings", true, sound());
    }

    private static ConfigurationProperty.Deprecation sound() {
        return new ConfigurationProperty.Deprecation("", "", "", "");
    }

    @Test
    void reportsAKeyTheContainerHasStoppedBinding() {
        List<String> reported = SpringMetadataRules.unbound(
            FILE, read(NAMED), metadata(withdrawn("error", "spring.application.title", "", "4.0.0"))
        );

        assertEquals(1, reported.size(), "the value written here is read by nothing");
        assertTrue(reported.getFirst().contains("spring.application.title"), "the replacement is named");
        assertTrue(reported.getFirst().contains("4.0.0"), "and the release that withdrew it");
    }

    @Test
    void leavesAnUnboundKeyOutOfTheDeprecatedRuleAndTheOtherWayAround() {
        SpringMetadata gone = metadata(withdrawn("error", "", "", ""));
        SpringMetadata advised = metadata(withdrawn("warning", "", "", ""));

        assertEquals(
            List.of(), SpringMetadataRules.deprecated(FILE, read(NAMED), gone),
            "a key that no longer binds is not a key still binding"
        );
        assertEquals(
            List.of(), SpringMetadataRules.unbound(FILE, read(NAMED), advised),
            "and a key still binding has not stopped"
        );
    }

    @Test
    void reportsAKeyItsSupplierHasDeprecated() {
        List<String> reported = SpringMetadataRules.deprecated(
            FILE, read(NAMED), metadata(withdrawn("warning", "", "Jackson 3 is preferred.", "3.5.0"))
        );

        assertEquals(1, reported.size(), "the release that withdraws it will stop reading the line");
        assertTrue(reported.getFirst().contains("Jackson 3 is preferred."), "the stated reason is carried");
    }

    @Test
    void tellsTheReaderToRemoveAKeyWithdrawnWithNothingPutInItsPlace() {
        List<String> reported = SpringMetadataRules.unbound(
            FILE, read(NAMED), metadata(withdrawn("error", "", "", ""))
        );

        assertTrue(reported.getFirst().endsWith("remove the line"), "there is nothing else to be done");
    }

    @Test
    void reportsAKeyBeneathADeclaredGroupThatDeclaresNoSuchKey() {
        List<String> reported = SpringMetadataRules.unknown(
            FILE, read(NAMED), metadata(group())
        );

        assertEquals(1, reported.size(), "the group is declared and knows no such key");
    }

    @Test
    void leavesAKeyNoDeclaredGroupClaimsAlone() {
        String own = """
            acme:
              retry:
                attempts: 3
            """;

        List<String> reported = SpringMetadataRules.unknown(
            FILE, read(own), metadata(group())
        );

        assertEquals(List.of(), reported, "a project binds its own settings and answers to nothing here");
    }

    @Test
    void leavesEveryKeyAloneWhenTheClasspathDeclaredNothing() {
        List<String> reported = SpringMetadataRules.unknown(FILE, read(NAMED), metadata());

        assertEquals(List.of(), reported, "an unread classpath is a separate finding rather than every key");
    }

    @Test
    void reportsAClasspathThatDeclaredNothingToJudgeTheFileAgainst() {
        List<String> reported = SpringMetadataRules.unread(FILE, read(NAMED), metadata());

        assertEquals(1, reported.size(), "a file judged against nothing is not a file known to be right");
    }

    @Test
    void acceptsAnEmptyClasspathForAFileThatDeclaresNothing() {
        List<String> reported = SpringMetadataRules.unread(FILE, read(""), metadata());

        assertEquals(List.of(), reported, "there was nothing to judge, so nothing went unjudged");
    }

    @Test
    void acceptsAKeyThatIsDeclaredAndInGoodStanding() {
        SpringMetadata metadata = metadata(
            new ConfigurationProperty("spring.application.name", "java.lang.String", false, sound()),
            group()
        );

        assertEquals(List.of(), SpringMetadataRules.unknown(FILE, read(NAMED), metadata), "it is declared");
        assertEquals(List.of(), SpringMetadataRules.unbound(FILE, read(NAMED), metadata), "and it binds");
    }
}
