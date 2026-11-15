package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads what a compose file gives up of a container's isolation: a privilege, a namespace of the host, a port on
 * every address of the host, or a credential written into the file.
 *
 * <p>The file is read as {@link ComposeLines} reads it, so anchors, {@code extends} and included files are not
 * followed.
 */
@UtilityClass
final class ComposeHardening {

    private static final Set<String> HOST_NAMESPACES = Set.of(
        "network_mode", "pid", "ipc", "userns_mode", "uts", "cgroup"
    );
    private static final String HOST = "host";
    private static final String PRIVILEGED = "privileged";
    private static final String CAPABILITIES = "cap_add";
    private static final String SECURITY_OPTIONS = "security_opt";
    private static final String VOLUMES = "volumes";
    private static final String SOURCE = "source";
    private static final String PORTS = "ports";
    private static final String HOST_IP = "host_ip";
    private static final String TARGET = "target";
    private static final String ENVIRONMENT = "environment";
    private static final String DOCKER_SOCKET = "docker.sock";
    // A security option that turns a confinement of the container off.
    private static final Pattern UNCONFINED = Pattern.compile("unconfined|label[:=]disable", Pattern.CASE_INSENSITIVE);
    // An address of the host's loopback interface, IPv4 or IPv6.
    private static final Pattern LOOPBACK = Pattern.compile("^(?:127(?:\\.\\d{1,3}){3}|::1)$");
    // A reference to a variable that supplies no default: ${NAME}, ${NAME:?message}, ${NAME?message} or $NAME.
    private static final Pattern REFERENCE = Pattern.compile(
        "^\\$(?:\\{[A-Za-z_]\\w*(?::?\\?[^}]*)?}|[A-Za-z_]\\w*)$"
    );
    private static final String ASSIGNMENT = "=";

    /**
     * Every privilege a service is given beyond a default container's: privileged mode, an added capability, a
     * confinement turned off, a namespace shared with the host, or the Docker socket mounted.
     *
     * @param yaml the text of the file
     * @return the service and the privilege, with the line, in file order
     */
    static List<Located<String>> isolationLifts(CharSequence yaml) {
        return ComposeLines.of(yaml).stream().flatMap(ComposeHardening::liftsOf).toList();
    }

    /**
     * Every port a service publishes on an address other than the host's loopback interface.
     *
     * @param yaml the text of the file
     * @return the service and the port, with the line, in file order
     */
    static List<Located<String>> exposedPorts(CharSequence yaml) {
        List<ComposeLine> lines = ComposeLines.of(yaml);
        Stream<Located<String>> flowed = lines.stream()
            .filter(line -> line.atService() && PORTS.equals(line.key()))
            .flatMap(
                line -> ComposeLines.flow(line.value()).map(port -> shortPort(line, port)).flatMap(Optional::stream)
            );
        List<ComposeLine> listed = lines.stream().filter(line -> line.under(PORTS)).toList();
        Stream<Located<String>> blocked = entries(listed).flatMap(entry -> exposedEntry(entry).stream());
        return Stream.concat(flowed, blocked)
            .sorted(Comparator.comparingInt(Located::line))
            .toList();
    }

    /**
     * Every credential a service's environment gives a value the file writes: a literal, or a variable's default.
     * A credential the file leaves to the environment that starts it, as {@code ${NAME}} does, is not one.
     *
     * @param yaml the text of the file
     * @return the service and the name of the credential, with the line, in file order
     */
    static List<Located<String>> valuedCredentials(CharSequence yaml) {
        return ComposeLines.of(yaml).stream().flatMap(ComposeHardening::credentialsOf)
            .filter(ComposeHardening::valued).map(ComposeHardening::described).toList();
    }

    private static boolean valued(Assignment assignment) {
        return EnvironmentRules.privateCredential(assignment.name()) && !supplied(assignment.value());
    }

    private static Located<String> described(Assignment assignment) {
        return new Located<>(assignment.line(), assignment.service() + " gives " + assignment.name() + " a value");
    }

    private static Stream<Located<String>> liftsOf(ComposeLine line) {
        Stream<String> lifts = Stream.of(
            privileged(line), namespace(line),
            listed(line, CAPABILITIES).map(capability -> "adds the capability " + capability),
            listed(line, SECURITY_OPTIONS).filter(option -> UNCONFINED.matcher(option).find())
                .map(option -> "sets security_opt " + option),
            socket(line)
        ).flatMap(lift -> lift);
        return lifts.map(lift -> new Located<>(line.line(), line.service() + ' ' + lift));
    }

    private static Stream<String> privileged(ComposeLine line) {
        boolean privileged = line.atService() && PRIVILEGED.equals(line.key()) && Boolean.parseBoolean(line.value());
        return privileged ? Stream.of("runs privileged") : Stream.empty();
    }

    private static Stream<String> namespace(ComposeLine line) {
        boolean shared = line.atService() && HOST_NAMESPACES.contains(line.key()) && HOST.equals(line.value());
        return shared ? Stream.of("shares the host's namespace through " + line.key() + ": " + HOST) : Stream.empty();
    }

    private static Stream<String> socket(ComposeLine line) {
        boolean longSource = line.under(VOLUMES) && SOURCE.equals(line.key()) && line.value().contains(DOCKER_SOCKET);
        boolean mounted = longSource || listed(line, VOLUMES).anyMatch(volume -> volume.contains(DOCKER_SOCKET));
        return mounted ? Stream.of("mounts the Docker socket") : Stream.empty();
    }

    // The scalars a key of a service lists: an item of its block list, or each item of a flow list on the key.
    private static Stream<String> listed(ComposeLine line, String serviceKey) {
        if (line.atService() && serviceKey.equals(line.key())) {
            return ComposeLines.flow(line.value());
        }
        return line.under(serviceKey) && line.key().isEmpty() ? Stream.of(line.value()) : Stream.empty();
    }

    // The items of the block lists of ports, each with the lines of its mapping. An item opens a new entry.
    private static Stream<List<ComposeLine>> entries(List<ComposeLine> listed) {
        List<Integer> starts = IntStream.range(0, listed.size()).filter(index -> listed.get(index).item())
            .boxed().toList();
        return IntStream.range(0, starts.size()).mapToObj(
            index -> listed.subList(
                starts.get(index), index + 1 < starts.size() ? starts.get(index + 1) : listed.size()
            )
        );
    }

    // An entry is a short-syntax port, a flow mapping, or the keys of a long-syntax mapping on the lines it spans.
    private static Optional<Located<String>> exposedEntry(List<ComposeLine> entry) {
        ComposeLine first = entry.getFirst();
        if (first.key().isEmpty() && !first.value().isEmpty()) {
            List<Map.Entry<String, String>> mapping = ComposeLines.flowMapping(first.value());
            return mapping.isEmpty() ? shortPort(first, first.value()) : longPort(first, keyed(mapping.stream()));
        }
        return longPort(
            first, keyed(
                entry.stream().filter(line -> !line.key().isEmpty())
                    .map(line -> Map.entry(line.key(), line.value()))
            )
        );
    }

    // A key a mapping repeats keeps its first value. Compose itself refuses the repetition.
    private static Map<String, String> keyed(Stream<Map.Entry<String, String>> pairs) {
        return pairs.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (kept, _) -> kept));
    }

    private static Optional<Located<String>> shortPort(ComposeLine line, String port) {
        return loopback(ComposeIsolation.hostAddress(port)) ? Optional.empty()
            : Optional.of(new Located<>(line.line(), line.service() + " publishes " + port + " beyond loopback"));
    }

    private static Optional<Located<String>> longPort(ComposeLine line, Map<String, String> port) {
        return loopback(port.getOrDefault(HOST_IP, "")) ? Optional.empty() : Optional.of(
            new Located<>(
                line.line(), line.service() + " publishes target " + port.getOrDefault(TARGET, "?") + " beyond loopback"
            )
        );
    }

    private static boolean loopback(String address) {
        return LOOPBACK.matcher(address).matches();
    }

    // An environment written on the key's own line, as a flow mapping or a flow list, or one entry of a block below.
    private static Stream<Assignment> credentialsOf(ComposeLine line) {
        if (line.atService() && ENVIRONMENT.equals(line.key())) {
            Stream<Assignment> mapped = ComposeLines.flowMapping(line.value()).stream()
                .map(pair -> new Assignment(line.line(), line.service(), pair.getKey(), pair.getValue()));
            return Stream.concat(mapped, ComposeLines.flow(line.value()).map(item -> listItem(line, item)));
        }
        return line.under(ENVIRONMENT) ? Stream.of(blockEntry(line)) : Stream.empty();
    }

    private static Assignment blockEntry(ComposeLine line) {
        return line.key().isEmpty() ? listItem(line, line.value())
            : new Assignment(line.line(), line.service(), line.key(), line.value());
    }

    private static Assignment listItem(ComposeLine line, String item) {
        int separator = item.indexOf(ASSIGNMENT);
        return separator < 0 ? new Assignment(line.line(), line.service(), item, "")
            : new Assignment(line.line(), line.service(), item.substring(0, separator), item.substring(separator + 1));
    }

    private static boolean supplied(String value) {
        String text = Quotes.stripped(value);
        return text.isEmpty() || REFERENCE.matcher(text).matches();
    }

    private record Assignment(int line, String service, String name, String value) {
    }
}
