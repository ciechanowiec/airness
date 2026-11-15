package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <p>The file is read as {@link ComposeLines} reads it, so anchors, {@code extends} and included files are not
 * followed.
 */
@UtilityClass
final class ComposeIsolation {

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern DEFAULTED = Pattern.compile("^\\$\\{[A-Za-z_]\\w*:?-[^}]*}$");
    private static final Pattern PROTOCOL = Pattern.compile("/[A-Za-z]+$");
    private static final Pattern BRACKETED = Pattern.compile("^\\[[^]]*]:");
    private static final String PORTS = "ports";
    private static final String PUBLISHED = "published";
    private static final String CONTAINER_NAME = "container_name";
    // Marks where a variable was set aside, with a character no port is written with.
    private static final String HOLE = "|";
    private static final int PAIR = 2;
    private static final int TRIPLE = 3;

    /**
     * Every port a service publishes on a host port the file fixes.
     *
     * @param yaml the text of the file
     * @return the service and the port, with the line, in file order
     */
    static List<Located<String>> fixedPorts(CharSequence yaml) {
        return ComposeLines.of(yaml).stream().flatMap(ComposeIsolation::fixedPortsOf).toList();
    }

    /**
     * Every container name a service fixes.
     *
     * @param yaml the text of the file
     * @return the service and the name, with the line, in file order
     */
    static List<Located<String>> containerNames(CharSequence yaml) {
        return ComposeLines.of(yaml).stream()
            .filter(ComposeLine::atService)
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

    /**
     * The host address a short-syntax port binds, as written, or nothing when it names none and so binds every
     * address of the host.
     *
     * @param port the port, such as {@code 127.0.0.1:8080:80/tcp}
     * @return the address, such as {@code 127.0.0.1} or {@code ::1}, empty when it is left out
     */
    static String hostAddress(String port) {
        List<String> variables = new ArrayList<>();
        String holed = holed(PROTOCOL.matcher(port.strip()).replaceFirst(""), variables);
        Matcher bracketed = BRACKETED.matcher(holed);
        if (bracketed.find()) {
            return filled(holed.substring(1, bracketed.end() - PAIR), variables);
        }
        List<String> parts = List.of(holed.split(":", -1));
        return parts.size() == TRIPLE ? filled(parts.getFirst(), variables) : "";
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
        if (node.under(PORTS)) {
            return node.key().isEmpty() ? Stream.of(hostPort(node.value())) : published(node);
        }
        return holdsPorts(node) ? ComposeLines.flow(node.value()).map(ComposeIsolation::hostPort) : Stream.empty();
    }

    private static boolean holdsPorts(ComposeLine node) {
        return node.atService() && PORTS.equals(node.key());
    }

    private static Stream<String> published(ComposeLine node) {
        return PUBLISHED.equals(node.key()) ? Stream.of(node.value()) : Stream.empty();
    }
}
