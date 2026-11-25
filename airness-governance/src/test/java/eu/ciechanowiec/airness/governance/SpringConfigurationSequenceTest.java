package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The items of a YAML list are read by their index, which is how Spring binds them, so a list of objects
 * such as the routes of a Spring Cloud Gateway is read item by item rather than reported.
 */
class SpringConfigurationSequenceTest {

    private static SpringConfiguration yaml(String content) {
        return new SpringConfiguration("application.yml", content);
    }

    @Test
    void readsAListOfObjectsItemByItem() {
        List<String> spellings = List.of(
            """
                spring:
                  cloud:
                    gateway:
                      routes:
                        - id: orders
                          uri: http://orders:8080
                          predicates:
                            - Host=shop.example.test
                            - Path=/orders/**
                        - id: billing
                          uri: http://billing:8080
                """,
            """
                spring:
                  cloud:
                    gateway:
                      routes:
                      - id: orders
                        uri: http://orders:8080
                        predicates:
                        - Host=shop.example.test
                        - Path=/orders/**
                      - id: billing
                        uri: http://billing:8080
                """
        );
        List<String> expected = List.of(
            "spring.cloud.gateway.routes.0.id=orders",
            "spring.cloud.gateway.routes.0.uri=http://orders:8080",
            "spring.cloud.gateway.routes.0.predicates.0=Host=shop.example.test",
            "spring.cloud.gateway.routes.0.predicates.1=Path=/orders/**",
            "spring.cloud.gateway.routes.1.id=billing",
            "spring.cloud.gateway.routes.1.uri=http://billing:8080"
        );

        spellings.forEach(
            content -> {
                SpringConfiguration read = yaml(content);
                assertEquals(
                    expected,
                    read.settings().stream().map(setting -> setting.key() + "=" + setting.value()).toList(),
                    "each item is read by its index, whether the dash is indented under the key or not"
                );
                assertEquals(List.of(), read.unreadable(), "and no line of the list is left unread");
            }
        );
    }

    @Test
    void readsAScalarItemByItsIndexAndKeepsTheColonOfItsValue() {
        String content = """
            management:
              endpoints:
                web:
                  exposure:
                    include:
                      - health
                      - info
            app:
              origins:
                - https://shop.example.test # the public host
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            List.of("health", "info"),
            read.declarations("management.endpoints.web.exposure.include").stream()
                .map(SpringConfiguration.Setting::value)
                .toList(),
            "a rule asking about the list is answered by each item"
        );
        assertEquals(
            "app.origins.0", read.settings().getLast().key(),
            "a colon followed by no space makes no key out of the item"
        );
        assertEquals(
            "https://shop.example.test", read.settings().getLast().value(),
            "and the item keeps the whole of its value, without the comment beside it"
        );
        assertEquals("origins[0]", read.settings().getLast().raw(), "the item is named by its list and index");
    }

    @Test
    void readsAnItemWhoseMappingStartsOnTheLineBelowItsDash() {
        String content = """
            routes:
              -
                id: orders
              -
                id: billing
            """;

        assertEquals(
            List.of("routes.0.id", "routes.1.id"),
            yaml(content).settings().stream().map(SpringConfiguration.Setting::key).toList(),
            "a dash alone opens an item whose keys follow beneath it"
        );
    }

    @Test
    void reportsASequenceThatBindsToNoKey() {
        String content = """
            - first
            spring:
              profiles:
                group:
                  - - nested
            """;

        SpringConfiguration read = yaml(content);

        assertEquals(
            List.of("line 1: - first", "line 5: - - nested"), read.unreadable(),
            "a list opening the file and a list written inside an item on one line are both reported"
        );
        assertTrue(read.settings().isEmpty(), "and neither is made into a key nothing could match");
    }
}
