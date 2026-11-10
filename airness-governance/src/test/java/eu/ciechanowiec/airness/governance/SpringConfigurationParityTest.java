package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

/**
 * Reads each configuration fixture twice, once with {@link SpringConfiguration} and once with the loaders
 * Spring Boot itself reads application configuration with, and holds the two to the same documents, keys
 * and values. Every rule of the configuration check is only as right as the reader under it, and a reader
 * that splits documents or flattens keys otherwise than the container does judges settings nobody binds.
 *
 * <p>Two differences are notation rather than reading and are settled before comparing. A value keeps its
 * quotation marks in {@link SpringConfiguration}, where Spring Boot hands over the text inside them. A YAML
 * flow sequence is one setting there, which the rules split into its items, where Spring Boot binds one key
 * per item, so the sequence is spread into the indexed keys Spring Boot binds.
 */
class SpringConfigurationParityTest {

    private static final String DIRECTORY = "/spring-configuration/";
    private static final String YAML = "documents.yml";
    private static final String PROPERTIES = "documents.properties";
    private static final Pattern QUOTED = Pattern.compile("^([\"'])(.*)\\1$");
    private static final Pattern FLOW = Pattern.compile("^\\[(.*)]$");
    private static final String ITEMS = ",";

    private static String fixture(String name) {
        URL resource = Optional.ofNullable(SpringConfigurationParityTest.class.getResource(DIRECTORY + name))
            .orElseThrow(() -> new IllegalStateException("the fixture " + name + " is not on the classpath"));
        try (InputStream stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("the fixture " + name + " cannot be read", exception);
        }
    }

    @SneakyThrows
    private static List<Map<String, String>> loaded(PropertySourceLoader loader, String name, String text) {
        ByteArrayResource resource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
        return loader.load(name, resource).stream().map(SpringConfigurationParityTest::flattened).toList();
    }

    private static Map<String, String> flattened(PropertySource<?> source) {
        if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
            throw new IllegalStateException(source.getName() + " does not list the keys it holds");
        }
        return Arrays.stream(enumerable.getPropertyNames())
            .collect(
                Collectors.toUnmodifiableMap(
                    SpringConfiguration::canonical, key -> String.valueOf(enumerable.getProperty(key))
                )
            );
    }

    private static List<Map<String, String>> read(String name, String text, boolean flow) {
        Map<Integer, List<SpringConfiguration.Setting>> documents = new SpringConfiguration(name, text)
            .settings().stream()
            .collect(Collectors.groupingBy(SpringConfiguration.Setting::document, TreeMap::new, Collectors.toList()));
        return documents.values().stream()
            .map(
                settings -> settings.stream()
                    .flatMap(setting -> bound(setting, flow))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
            ).toList();
    }

    private static Stream<Map.Entry<String, String>> bound(SpringConfiguration.Setting setting, boolean flow) {
        Matcher sequence = FLOW.matcher(setting.value());
        if (flow && sequence.matches()) {
            List<String> items = Arrays.stream(sequence.group(1).split(ITEMS))
                .map(SpringConfigurationParityTest::unquoted)
                .toList();
            return IntStream.range(0, items.size())
                .mapToObj(index -> Map.entry(setting.key() + "." + index, items.get(index)));
        }
        return Stream.of(Map.entry(setting.key(), unquoted(setting.value())));
    }

    private static String unquoted(String value) {
        return QUOTED.matcher(value.strip()).replaceFirst("$2");
    }

    @Test
    void readsAYamlFileIntoTheDocumentsAndSettingsSpringBootLoads() {
        assertEquals(
            loaded(new YamlPropertySourceLoader(), YAML, fixture(YAML)), read(YAML, fixture(YAML), true),
            "each document holds the keys and values the container binds from it"
        );
    }

    @Test
    void readsAPropertiesFileIntoTheDocumentsAndSettingsSpringBootLoads() {
        assertEquals(
            loaded(new PropertiesPropertySourceLoader(), PROPERTIES, fixture(PROPERTIES)),
            read(PROPERTIES, fixture(PROPERTIES), false),
            "each document holds the keys and values the container binds from it"
        );
    }

    // Written here rather than in the fixture, whose own properties inspection asks for one delimiter.
    @Test
    void readsAColonDelimitedPropertyAsSpringBootLoadsIt() {
        String text = "server.shutdown: graceful\nspring.application.name:example\n";
        assertEquals(
            loaded(new PropertiesPropertySourceLoader(), PROPERTIES, text), read(PROPERTIES, text, false),
            "a colon divides a key from its value as an equals sign does"
        );
    }

    @Test
    void accountsForEveryLineOfTheFixtures() {
        List<String> unreadable = Stream.of(YAML, PROPERTIES)
            .flatMap(name -> new SpringConfiguration(name, fixture(name)).unreadable().stream())
            .toList();

        assertEquals(List.of(), unreadable, "a line the reader reports is a line it did not compare");
    }
}
