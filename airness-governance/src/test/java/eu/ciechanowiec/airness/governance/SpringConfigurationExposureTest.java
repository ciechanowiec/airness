package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The actuator's published endpoints are one list however it is written: separated by commas, as a YAML
 * flow sequence in either order and with or without quotation marks, or item by item through its index.
 * Each spelling publishes the same endpoints, so each is judged the same way.
 */
class SpringConfigurationExposureTest {

    private static final String YAML = "application.yml";
    private static final String PROPERTIES = "application.properties";
    private static final String INCLUDE = "management.endpoints.web.exposure.include";

    private static List<String> yaml(String include) {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include: %s
            """.formatted(include);
        return SpringConfigurationRules.offences(YAML, new SpringConfiguration(YAML, configuration));
    }

    private static List<String> properties(String content) {
        return SpringConfigurationRules.offences(PROPERTIES, new SpringConfiguration(PROPERTIES, content));
    }

    @Test
    void reportsADangerousEndpointInACommaSeparatedList() {
        assertEquals(1, yaml("health, env").size(), "the environment endpoint is published");
    }

    @Test
    void reportsADangerousEndpointLastInAFlowSequence() {
        assertEquals(1, yaml("[health, env]").size(), "the brackets are notation around the same list");
    }

    @Test
    void reportsADangerousEndpointFirstInAFlowSequence() {
        assertEquals(1, yaml("[env, health]").size(), "the endpoint is published wherever it sits");
    }

    @Test
    void reportsADangerousEndpointInAFlowSequenceOfDoubleQuotedItems() {
        assertEquals(1, yaml("[\"health\", \"env\"]").size(), "quotation marks are notation around an item");
    }

    @Test
    void reportsADangerousEndpointInAFlowSequenceOfSingleQuotedItems() {
        assertEquals(1, yaml("['env', 'health']").size(), "single quotation marks are notation as well");
    }

    @Test
    void reportsAWildcardInAFlowSequence() {
        assertEquals(1, yaml("[\"*\"]").size(), "the wildcard publishes every endpoint");
    }

    @Test
    void acceptsAFlowSequenceOfHarmlessEndpoints() {
        assertEquals(List.of(), yaml("[health, info]"), "the endpoints are named and none hands anything out");
    }

    @Test
    void reportsADangerousEndpointWrittenAsAnIndexedItem() {
        String configuration = INCLUDE + "[0]=health\n" + INCLUDE + "[1]=env\n";

        assertEquals(1, properties(configuration).size(), "the second item publishes the environment");
    }

    @Test
    void reportsAWildcardWrittenAsAnIndexedItem() {
        assertEquals(1, properties(INCLUDE + "[0]=*\n").size(), "the item publishes every endpoint");
    }

    @Test
    void reportsADangerousEndpointWrittenAsAnIndexedYamlKey() {
        String configuration = """
            management:
              endpoints:
                web:
                  exposure:
                    include[0]: env
            """;

        assertEquals(
            1, SpringConfigurationRules.offences(YAML, new SpringConfiguration(YAML, configuration)).size(),
            "the bracketed key is the same item"
        );
    }

    @Test
    void acceptsHarmlessEndpointsWrittenAsIndexedItems() {
        String configuration = INCLUDE + "[0]=health\n" + INCLUDE + "[1]=info\n";

        assertEquals(List.of(), properties(configuration), "each item names an endpoint that hands nothing out");
    }

    @Test
    void leavesAKeyThatOnlyBeginsWithTheListAlone() {
        assertEquals(
            List.of(), properties("management.endpoints.web.exposure.include-extra=env\n"),
            "another key is not an item of the list"
        );
    }
}
