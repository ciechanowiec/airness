package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * The user properties that set a parameter of a goal the Airness parents bind, and who owns each.
 *
 * <p>Maven fills a parameter that no configuration sets from the project property its descriptor names, so
 * a child property block reaches every such parameter without declaring the plugin at all. Refusing the
 * plugin's configuration while leaving its properties open refuses one spelling of a bypass and accepts the
 * other. A test of this module derives the rows from the pinned descriptors, so the table is the record of
 * a decision taken about every property rather than a list of the ones somebody thought of.
 */
@UtilityClass
final class PluginProperties {

    private static final String COMMENT = "#";
    private static final String RESOURCE = "plugin-properties.tsv";
    private static final int COLUMNS = 3;
    private static final List<Row> ROWS = read();
    private static final Set<String> RESERVED = ROWS.stream()
        .filter(row -> row.use() == Use.RESERVED)
        .map(Row::property)
        .collect(Collectors.toUnmodifiableSet());

    /**
     * Every classified property.
     *
     * @return the rows in the order the table lists them
     */
    static List<Row> rows() {
        return ROWS;
    }

    /**
     * Whether a child project file may not declare a property, because some bound plugin reserves it.
     *
     * @param property the property name
     * @return whether a row reserves it
     */
    static boolean reserves(String property) {
        return RESERVED.contains(property);
    }

    private static List<Row> read() {
        URL resource = Optional.ofNullable(PluginProperties.class.getResource(RESOURCE))
            .orElseThrow(() -> new IllegalStateException("Missing resource " + RESOURCE));
        try (InputStream stream = resource.openStream()) {
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + RESOURCE, exception);
        }
    }

    /**
     * Reads the table text. A line that is not a comment, not blank and not three tab-separated columns
     * naming a known owner is refused with its number, since a row the reader skipped would be a property
     * nobody decided about.
     *
     * @param content the table text
     * @return its rows
     */
    static List<Row> parse(String content) {
        List<String> lines = content.lines().toList();
        return IntStream.range(0, lines.size())
            .mapToObj(index -> row(index + 1, lines.get(index)))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<Row> row(int number, String line) {
        if (line.isBlank() || line.startsWith(COMMENT)) {
            return Optional.empty();
        }
        String[] columns = line.split("\t", -1);
        if (columns.length != COLUMNS) {
            throw new IllegalStateException(RESOURCE + " line " + number + " does not hold three columns");
        }
        return Optional.of(new Row(columns[0], columns[1], use(number, columns[2])));
    }

    private static Use use(int number, String written) {
        return Arrays.stream(Use.values())
            .filter(use -> use.name().equals(written))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(RESOURCE + " line " + number + " names no owner"));
    }

    /**
     * Who owns a property.
     */
    enum Use {

        /**
         * The harness: a child project file may not declare the property.
         */
        RESERVED,

        /**
         * The project: the property tunes how a goal runs and decides no verdict.
         */
        PROJECT
    }

    /**
     * One classified property of one plugin.
     *
     * @param plugin   the plugin's artifact identifier
     * @param property the user property its descriptor names
     * @param use      who owns the property
     */
    record Row(String plugin, String property, Use use) {
    }
}
