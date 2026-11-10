package eu.ciechanowiec.airness.network;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The host names that stay on the machine a test runs on.
 *
 * <p>They are {@code localhost} and every name below it, which RFC 6761 reserves for the loopback interface, the
 * machine's own name, the names Docker and Testcontainers give the machine as their containers see it, and the
 * Docker host the environment names when the daemon is reached over TCP, which is where a container a test starts
 * publishes its ports. A name is compared without regard to case, as the resolver compares it.
 *
 * @param names the names allowed beside {@code localhost} and the names below it, in lower case
 */
record LocalNames(Set<String> names) {

    private static final String LOOPBACK = "localhost";
    private static final String BELOW_LOOPBACK = "." + LOOPBACK;
    private static final Set<String> CONTAINER_HOSTS = Set.of(
        "host.docker.internal", "gateway.docker.internal", "host.testcontainers.internal"
    );
    private static final String DOCKER_HOST = "DOCKER_HOST";
    private static final String TESTCONTAINERS_HOST = "TESTCONTAINERS_HOST_OVERRIDE";
    private static final String TCP = "tcp";

    /**
     * Holds the names in lower case and as a set of its own, so they cannot change after they were read.
     *
     * @param names the names allowed beside {@code localhost} and the names below it
     */
    LocalNames {
        names = names.stream().map(LocalNames::folded).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Reads the names from the environment a test runs in.
     *
     * @param environment the environment variables of the test's process
     * @param localHost   the machine's own name
     * @return the names
     */
    static LocalNames of(Map<String, String> environment, String localHost) {
        Stream<String> docker = Optional.ofNullable(environment.get(DOCKER_HOST)).flatMap(LocalNames::tcpHost)
            .stream();
        Stream<String> override = Optional.ofNullable(environment.get(TESTCONTAINERS_HOST))
            .filter(host -> !host.isBlank()).stream();
        return new LocalNames(
            Stream.of(Stream.of(localHost), CONTAINER_HOSTS.stream(), docker, override).flatMap(stream -> stream)
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    /**
     * Whether a host name stays on the machine.
     *
     * @param host the name a test asked to resolve
     * @return whether it is one of these names, {@code localhost}, or a name below it
     */
    boolean allows(String host) {
        String name = folded(host);
        return LOOPBACK.equals(name) || name.endsWith(BELOW_LOOPBACK) || this.names.contains(name);
    }

    // A daemon reached over a socket file is on this machine, and its containers publish on localhost.
    private static Optional<String> tcpHost(String dockerHost) {
        try {
            URI address = new URI(dockerHost.strip());
            return TCP.equals(address.getScheme()) ? Optional.ofNullable(address.getHost()) : Optional.empty();
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
    }

    private static String folded(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
