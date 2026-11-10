package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpringMetadataTest {

    private static final ConfigurationProperty.Deprecation SOUND
        = new ConfigurationProperty.Deprecation("", "", "", "");

    private static ConfigurationProperty key(String name, String type) {
        return new ConfigurationProperty(name, type, false, SOUND);
    }

    private static ConfigurationProperty group(String name) {
        return new ConfigurationProperty(name, "com.example.Settings", true, SOUND);
    }

    @Test
    void readsNothingAsNothingRatherThanAsAClasspathThatDeclaresNoKeys() {
        SpringMetadata metadata = new SpringMetadata(List.of());

        assertTrue(metadata.empty(), "an unread classpath answers no question about any key");
    }

    @Test
    void knowsAKeyItWasGivenWhateverSpellingTheProjectUses() {
        SpringMetadata metadata = new SpringMetadata(List.of(key("spring.jpa.open-in-view", "boolean")));

        assertTrue(metadata.known("spring.jpa.openInView"), "Spring binds every spelling as one key");
    }

    @Test
    void knowsEveryKeyBeneathAMap() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("logging.level", "java.util.Map<java.lang.String,java.lang.String>"))
        );

        assertTrue(metadata.known("logging.level.org.hibernate"), "a map holds whatever is put under it");
    }

    @Test
    void knowsEveryKeyBeneathProperties() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("spring.datasource.hikari.data-source-properties", "java.util.Properties"))
        );

        assertTrue(
            metadata.known("spring.datasource.hikari.data-source-properties.cachePrepStmts"),
            "properties are as open as a map, and reading only maps refuses a pool that tunes its driver"
        );
    }

    @Test
    void refusesToJudgeAKeyNoDeclaredGroupClaims() {
        SpringMetadata metadata = new SpringMetadata(List.of(group("spring.jpa")));

        assertEquals(
            Optional.empty(), metadata.anchor("acme.retry.attempts"),
            "a project binds its own settings, and nothing here has standing to call them wrong"
        );
    }

    @Test
    void claimsAKeyBeneathTheMostSpecificGroupAboveIt() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(group("spring"), group("spring.jpa"), group("spring.jpa.hibernate"))
        );

        assertEquals(
            Optional.of("spring.jpa.hibernate"), metadata.anchor("spring.jpa.hibernate.ddl_auto"),
            "the nearest group is the one that knows the key is not one of its own"
        );
    }

    @Test
    void readsWhatASupplierSaysAboutWithdrawingAKey() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(
                new ConfigurationProperty(
                    "server.error.include-message", "java.lang.String", false,
                    new ConfigurationProperty.Deprecation("error", "spring.web.error.include-message", "", "4.0.0")
                )
            )
        );

        assertTrue(
            metadata.deprecation("server.error.include-message").orElseThrow().unbound(),
            "the container has stopped reading it rather than merely advised against it"
        );
    }

    @Test
    void holdsNoDeprecationForAKeyInGoodStanding() {
        SpringMetadata metadata = new SpringMetadata(List.of(key("server.shutdown", "java.lang.String")));

        assertFalse(metadata.deprecation("server.shutdown").isPresent(), "nothing was said about it");
    }

    @Test
    void keepsOneEntryWhenTwoJarsDeclareTheSameKey() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("server.port", "java.lang.Integer"), key("server.port", "java.lang.Integer"))
        );

        assertTrue(metadata.known("server.port"), "two suppliers naming one key is not a conflict to refuse");
    }

    @Test
    void knowsAnIndexedItemOfASet() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("management.endpoints.web.exposure.include", "java.util.Set<java.lang.String>"))
        );

        assertTrue(
            metadata.known("management.endpoints.web.exposure.include[0]"),
            "a set is bound item by item from its indexed keys"
        );
    }

    @Test
    void knowsTheKeysOfAnObjectHeldInAList() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("spring.cloud.gateway.routes", "java.util.List<com.example.RouteDefinition>"))
        );

        assertTrue(
            metadata.known("spring.cloud.gateway.routes[0].id"),
            "the keys after the index belong to the item, which metadata does not describe"
        );
    }

    @Test
    void knowsAnIndexedItemOfACollection() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("acme.hosts", "java.util.Collection<java.lang.String>"))
        );

        assertTrue(metadata.known("acme.hosts[2]"), "a collection is bound item by item as well");
    }

    @Test
    void knowsAnIndexedItemOfAnArray() {
        SpringMetadata metadata = new SpringMetadata(List.of(key("acme.hosts", "java.lang.String[]")));

        assertTrue(metadata.known("acme.hosts[1]"), "an array is bound item by item as well");
    }

    @Test
    void doesNotKnowAnIndexBeneathASingleValue() {
        SpringMetadata metadata = new SpringMetadata(List.of(key("server.shutdown", "java.lang.String")));

        assertFalse(metadata.known("server.shutdown[0]"), "a single value has no items to index");
    }

    @Test
    void doesNotKnowAnIndexedItemOfAListItDoesNotDeclare() {
        SpringMetadata metadata = new SpringMetadata(
            List.of(key("acme.hosts", "java.util.List<java.lang.String>"))
        );

        assertFalse(metadata.known("acme.ports[0]"), "the index makes no key known on its own");
    }
}
