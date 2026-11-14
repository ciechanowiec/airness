package eu.ciechanowiec.airness.governance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads what a compose file fixes for every checkout that starts it: a host port and a container name.
 *
 * <p>Two checkouts of one project, or two projects, started on one machine both ask for each of them, and the
 * second start fails. A host port given as a variable without a default, such as {@code ${APP_PORT}}, is chosen
 * by whoever starts the project, and an empty one, as in {@code 127.0.0.1::8080}, by Docker. A literal port, or a
 * variable whose default is a port, is the same port on every checkout.
 *
 * <p>The reader follows nesting by indentation, as {@link ComposeFile} does, and also reads the items of a block
 * list and the flow list a {@code ports} key may hold on its own line. Anchors, {@code extends} and included files
 * are not followed.
 */
@UtilityClass
final class ComposeIsolation {

    private static final Pattern COMMENTED = Pattern.compile("(?:^|\\s)#");
    private static final Pattern QUOTED = Pattern.compile("^([\"'])(?<inner>.*?)\\1(?:\\s+#.*)?$");
    private static final Pattern KEYED = Pattern.compile("^(?<key>[^\\s'\"#\\[{-][^:#]*?)\\s*:(?:\\s+(?<value>.*))?$");
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern DEFAULTED = Pattern.compile("^\\$\\{[A-Za-z_]\\w*:?-[^}]*}$");
    private static final Pattern PROTOCOL = Pattern.compile("/[A-Za-z]+$");
    private static final Pattern BRACKETED = Pattern.compile("^\\[[^]]*]:");
    private static final String ITEM = "-";
    private static final String COMMENT = "#";
    private static final String DOCUMENT = "---";
    private static final String FLOW_OPEN = "[";
    private static final String FLOW_CLOSE = "]";
    private static final String PORTS = "ports";
    private static final String PUBLISHED = "published";
    private static final String CONTAINER_NAME = "container_name";
    // Marks where a variable was set aside, with a character no port is written with.
    private static final String HOLE = "|";
    private static final int SERVICE_DEPTH = 2;
    private static final int PORT_DEPTH = 3;
    private static final int PAIR = 2;

    /**
     * Every port a service publishes on a host port the file fixes.
     *
     * @param yaml the text of the file
     * @return the service and the port, with the line, in file order
     */
    static List<Located<String>> fixedPorts(CharSequence yaml) {
        return nodes(yaml).stream().flatMap(ComposeIsolation::fixedPortsOf).toList();
    }

    /**
     * Every container name a service fixes.
     *
     * @param yaml the text of the file
     * @return the service and the name, with the line, in file order
     */
    static List<Located<String>> containerNames(CharSequence yaml) {
        return nodes(yaml).stream()
            .filter(node -> node.under(ComposeFile.SERVICES) && node.path().size() == SERVICE_DEPTH)
            .filter(node -> CONTAINER_NAME.equals(node.key()) && !node.value().isEmpty())
            .map(node -> new Located<>(node.line(), node.service() + " is named " + node.value()))
            .toList();
    }

    /**
     * The host port a short-syntax port publishes on, as written, or nothing when Docker chooses it.
     *
     * @param port the port, such as {@code 127.0.0.1:8080:80/tcp}
     * @return the host port, empty when it is left out
     */
    static String hostPort(String port) {
        List<String> variables = new ArrayList<>();
        String holed = holed(PROTOCOL.matcher(port.strip()).replaceFirst(""), variables);
        Matcher bracketed = BRACKETED.matcher(holed);
        String unaddressed = bracketed.find() ? holed.substring(bracketed.end()) : holed;
        // The host port is the part before the container port, whether or not an address precedes both.
        List<String> parts = List.of(unaddressed.split(":", -1));
        String host = parts.size() >= PAIR ? parts.get(parts.size() - PAIR) : "";
        return filled(host, variables);
    }

    // Each variable set aside, so the colon of a default or a message inside it is not read as a separator.
    private static String holed(String port, List<String> variables) {
        Matcher variable = VARIABLE.matcher(port);
        StringBuilder holed = new StringBuilder();
        while (variable.find()) {
            variables.add(variable.group());
            variable.appendReplacement(holed, HOLE + (variables.size() - 1) + HOLE);
        }
        variable.appendTail(holed);
        return holed.toString();
    }

    /**
     * Whether a host port is the same on every checkout: a literal, or a variable whose default is one.
     *
     * @param host the host port, as {@link #hostPort} reads it
     * @return whether the file fixes it
     */
    static boolean fixed(String host) {
        return !host.isEmpty() && (!VARIABLE.matcher(host).matches() || DEFAULTED.matcher(host).matches());
    }

    private static String filled(String host, List<String> variables) {
        String filled = host;
        for (int index = 0; index < variables.size(); index++) {
            filled = filled.replace(HOLE + index + HOLE, variables.get(index));
        }
        return filled;
    }

    private static Stream<Located<String>> fixedPortsOf(ComposeLine node) {
        return hosts(node).filter(ComposeIsolation::fixed)
            .map(host -> new Located<>(node.line(), node.service() + " publishes host port " + host));
    }

    // The host port of each port a line publishes: a short-syntax item, the published key of a long-syntax one, or
    // every item of a flow list on the ports key itself.
    private static Stream<String> hosts(ComposeLine node) {
        if (node.listsPorts()) {
            return node.key().isEmpty() ? Stream.of(hostPort(node.value())) : published(node);
        }
        return node.holdsPorts() ? flow(node.value()).map(ComposeIsolation::hostPort) : Stream.empty();
    }

    private static Stream<String> published(ComposeLine node) {
        return PUBLISHED.equals(node.key()) ? Stream.of(node.value()) : Stream.empty();
    }

    // The items of a flow list, such as ["8080:80", "9000:9000"], each without its quotes.
    private static Stream<String> flow(String list) {
        if (!list.startsWith(FLOW_OPEN)) {
            return Stream.empty();
        }
        String inner = list.substring(1, list.endsWith(FLOW_CLOSE) ? list.length() - 1 : list.length());
        return Stream.of(inner.split(",")).map(String::strip).filter(item -> !item.isEmpty()).map(Quotes::stripped);
    }

    // Every line with content, with the keys of the mappings it is nested in. A key stays open for the lines
    // deeper than it, and a list item counts as deep as the text after its dash.
    private static List<ComposeLine> nodes(CharSequence yaml) {
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
            return new ComposeLine(line, indent, List.of(), "", scalar(content));
        }
        String key = Optional.ofNullable(keyed.group("key")).orElse("").strip();
        String value = Optional.ofNullable(keyed.group("value")).orElse("");
        return new ComposeLine(line, indent, List.of(), key, scalar(value));
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

    /**
     * One line with content.
     *
     * @param line   the one-based line
     * @param indent how deep its text starts, after the dash of a list item
     * @param path   the keys of the mappings it is nested in, outermost first
     * @param key    its key, empty for a list item that holds a scalar
     * @param value  its scalar, empty when it opens a mapping
     */
    private record ComposeLine(int line, int indent, List<String> path, String key, String value) {

        ComposeLine within(List<String> ancestors) {
            return new ComposeLine(this.line, this.indent, List.copyOf(ancestors), this.key, this.value);
        }

        boolean under(String section) {
            return !this.path.isEmpty() && section.equals(this.path.getFirst());
        }

        boolean listsPorts() {
            return this.under(ComposeFile.SERVICES) && this.path.size() == PORT_DEPTH
                && PORTS.equals(this.path.getLast());
        }

        boolean holdsPorts() {
            return this.under(ComposeFile.SERVICES) && this.path.size() == SERVICE_DEPTH && PORTS.equals(this.key);
        }

        String service() {
            return this.path.size() > 1 ? this.path.get(1) : this.key;
        }
    }
}
