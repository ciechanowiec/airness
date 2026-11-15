package eu.ciechanowiec.airness.governance;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The lines with content of a compose file, each with the keys of the mappings it is nested in, which are what
 * the compose rules read.
 *
 * <p>The reader follows nesting by indentation, as {@link ComposeFile} does, and also reads the items of a block
 * list and of a flow list or flow mapping written on one line. Anchors, {@code extends} and included files are not
 * followed.
 */
@UtilityClass
final class ComposeLines {

    private static final Pattern COMMENTED = Pattern.compile("(?:^|\\s)#");
    private static final Pattern QUOTED = Pattern.compile("^([\"'])(?<inner>.*?)\\1(?:\\s+#.*)?$");
    private static final Pattern KEYED = Pattern.compile("^(?<key>[^\\s'\"#\\[{-][^:#]*?)\\s*:(?:\\s+(?<value>.*))?$");
    // A key of a flow mapping and its value, which a colon and blank space separate.
    private static final Pattern FLOW_PAIR = Pattern.compile("^(?<key>[^:]+?)\\s*:\\s+(?<value>.*)$");
    private static final String ITEM = "-";
    private static final String COMMENT = "#";
    private static final String DOCUMENT = "---";
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String MAPPING_OPEN = "{";
    private static final String MAPPING_CLOSE = "}";

    /**
     * Every line with content, with the keys of the mappings it is nested in. A key stays open for the lines
     * deeper than it, and a list item counts as deep as the text after its dash.
     *
     * @param yaml the text of the file
     * @return the lines, in file order
     */
    static List<ComposeLine> of(CharSequence yaml) {
        String[] lines = yaml.toString().split("\n", -1);
        List<ComposeLine> read = IntStream.range(0, lines.length)
            .mapToObj(index -> read(index + 1, lines[index]))
            .flatMap(Optional::stream)
            .toList();
        List<ComposeLine> nodes = new ArrayList<>();
        Deque<ComposeLine> open = new ArrayDeque<>();
        for (ComposeLine line : read) {
            closeDeeper(open, line.indent());
            ComposeLine node = line.within(open.reversed().stream().map(ComposeLine::key).toList());
            nodes.add(node);
            if (!node.key().isEmpty()) {
                open.push(node);
            }
        }
        return nodes;
    }

    /**
     * The items of a flow list, such as {@code ["8080:80", "9000:9000"]}, each without its quotes.
     *
     * @param list the value as written
     * @return the items, none when the value is no flow list
     */
    static Stream<String> flow(String list) {
        return inner(list, LIST_OPEN, LIST_CLOSE).stream().flatMap(ComposeLines::items);
    }

    /**
     * The pairs of a flow mapping, such as {@code {target: 80, host_ip: 127.0.0.1}}, each value without its quotes.
     *
     * @param mapping the value as written
     * @return the pairs, none when the value is no flow mapping
     */
    static List<Map.Entry<String, String>> flowMapping(String mapping) {
        return inner(mapping, MAPPING_OPEN, MAPPING_CLOSE).stream()
            .flatMap(ComposeLines::items)
            .map(FLOW_PAIR::matcher)
            .filter(Matcher::matches)
            .map(pair -> pairOf(pair.group("key"), pair.group("value")))
            .toList();
    }

    private static Map.Entry<String, String> pairOf(String key, String value) {
        return new SimpleImmutableEntry<>(Quotes.stripped(key), Quotes.stripped(value));
    }

    private static Optional<String> inner(String value, String open, String close) {
        if (!value.startsWith(open)) {
            return Optional.empty();
        }
        return Optional.of(value.substring(1, value.endsWith(close) ? value.length() - 1 : value.length()));
    }

    private static Stream<String> items(String inner) {
        return Stream.of(inner.split(",")).map(String::strip).filter(item -> !item.isEmpty()).map(Quotes::stripped);
    }

    // The keys a line at this depth is no longer nested in.
    private static void closeDeeper(Deque<ComposeLine> open, int indent) {
        while (!open.isEmpty() && open.peek().indent() >= indent) {
            open.pop();
        }
    }

    private static Optional<ComposeLine> read(int line, String raw) {
        String text = raw.strip();
        boolean ignored = text.isEmpty() || text.startsWith(COMMENT) || text.startsWith(DOCUMENT);
        return ignored ? Optional.empty() : Optional.of(parsed(line, raw, text));
    }

    private static ComposeLine parsed(int line, String raw, String text) {
        boolean item = ITEM.equals(text) || text.startsWith(ITEM + " ");
        String content = item ? text.substring(1).strip() : text;
        int indent = raw.length() - raw.stripLeading().length() + (item ? text.length() - content.length() : 0);
        Matcher keyed = KEYED.matcher(content);
        if (!keyed.matches()) {
            return new ComposeLine(line, indent, List.of(), "", scalar(content), item);
        }
        String key = Optional.ofNullable(keyed.group("key")).orElse("").strip();
        String value = Optional.ofNullable(keyed.group("value")).orElse("");
        return new ComposeLine(line, indent, List.of(), key, scalar(value), item);
    }

    // A quoted scalar is taken whole, up to its closing quote, and anything after that is a comment. In an
    // unquoted one a '#' opens a comment only where blank space precedes it, which is what YAML says.
    private static String scalar(String value) {
        String text = value.strip();
        Matcher quoted = QUOTED.matcher(text);
        if (quoted.matches()) {
            return Optional.ofNullable(quoted.group("inner")).orElse("");
        }
        Matcher comment = COMMENTED.matcher(text);
        return (comment.find() ? text.substring(0, comment.start()) : text).strip();
    }
}
