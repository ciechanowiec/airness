package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.ciechanowiec.airness.governance.ConfigurationProperty;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * The configuration keys the classpath of one module says it binds.
 *
 * <p>Spring Boot writes {@code META-INF/spring-configuration-metadata.json} into each jar that
 * contributes settings. Reading it is the only way to hold a project's {@code application.yml} to what
 * its own dependencies actually accept, and it has to happen here rather than in the governance module:
 * a resolved artifact is a thing only Maven knows about, and the governance module reads text out of the
 * working tree and nothing else.
 *
 * <p>So this reads and transcribes, and decides nothing. What counts as a withdrawn key or an
 * unaccounted one is a rule, and the rules stay where the other rules are.
 *
 * <p>An archive that cannot be opened is thrown rather than skipped. It sits on a compile classpath that
 * has already been resolved, so failing to read it is a broken build and not an absent contribution, and
 * treating it as the latter would quietly shrink what every key is judged against.
 *
 * @param classpath the resolved compile classpath, plus the output directory of the module itself
 */
record ConfigurationMetadata(List<Path> classpath) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENTRY = "META-INF/spring-configuration-metadata.json";
    private static final String PROPERTIES = "properties";
    private static final String GROUPS = "groups";
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String DEPRECATION = "deprecation";
    private static final String DEPRECATED = "deprecated";
    private static final String LEVEL = "level";

    /**
     * The specification's default when a key is marked deprecated without a level being named.
     */
    private static final String ADVISED = "warning";

    private static final String NONE = "";

    ConfigurationMetadata {
        classpath = List.copyOf(classpath);
    }

    /**
     * Every metadata entry the classpath publishes, in no particular order.
     *
     * @return the entries, which may hold one name more than once when two jars declare it
     */
    List<ConfigurationProperty> published() {
        return this.classpath.stream().flatMap(ConfigurationMetadata::read).toList();
    }

    private static Stream<ConfigurationProperty> read(Path element) {
        return Files.isDirectory(element)
            ? loose(element.resolve(ENTRY))
            : archived(element);
    }

    private static Stream<ConfigurationProperty> loose(Path file) {
        if (!Files.isReadable(file)) {
            return Stream.empty();
        }
        try {
            return parsed(MAPPER.readTree(file.toFile()));
        } catch (IOException exception) {
            throw new UncheckedIOException("%s could not be read".formatted(file), exception);
        }
    }

    private static Stream<ConfigurationProperty> archived(Path archive) {
        try (JarFile jar = new JarFile(archive.toFile())) {
            return entries(jar);
        } catch (IOException exception) {
            throw new UncheckedIOException("%s could not be read".formatted(archive), exception);
        }
    }

    /*
     * The tree is read while the archive is open and walked after it is closed, which is safe because
     * readTree materialises the whole document rather than leaving a reader over the entry.
     */
    private static Stream<ConfigurationProperty> entries(JarFile jar) throws IOException {
        Optional<ZipEntry> entry = Optional.ofNullable(jar.getEntry(ENTRY));
        if (entry.isEmpty()) {
            return Stream.empty();
        }
        try (InputStream source = jar.getInputStream(entry.orElseThrow())) {
            return parsed(MAPPER.readTree(source));
        }
    }

    private static Stream<ConfigurationProperty> parsed(JsonNode root) {
        JsonNode document = root;
        return Stream.concat(
            listed(document.path(PROPERTIES), false),
            listed(document.path(GROUPS), true)
        );
    }

    private static Stream<ConfigurationProperty> listed(JsonNode declared, boolean group) {
        return declared.valueStream().map(entry -> property(entry, group)).toList().stream();
    }

    private static ConfigurationProperty property(JsonNode entry, boolean group) {
        JsonNode declared = entry;
        return new ConfigurationProperty(
            declared.path(NAME).asText(NONE),
            declared.path(TYPE).asText(NONE),
            group,
            deprecation(declared)
        );
    }

    /*
     * A missing deprecation and an empty one mean different things. The specification lets a key be
     * marked deprecated with no level named, which defaults to a warning, and ten keys of Spring Boot
     * itself are written that way. The older boolean form says the same with no object at all. Both are
     * given the default here so that an empty level below means the key is in good standing and nothing
     * else.
     */
    private static ConfigurationProperty.Deprecation deprecation(JsonNode entry) {
        JsonNode stated = entry.path(DEPRECATION);
        boolean withdrawn = !stated.isMissingNode() || entry.path(DEPRECATED).asBoolean(false);
        return new ConfigurationProperty.Deprecation(
            withdrawn ? stated.path(LEVEL).asText(ADVISED) : NONE,
            stated.path("replacement").asText(NONE),
            stated.path("reason").asText(NONE),
            stated.path("since").asText(NONE)
        );
    }
}
