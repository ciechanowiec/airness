package eu.ciechanowiec.airness.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LocalNamesTest {

    private static final String MACHINE = "build-agent-7";
    private static final String DOCKER_HOST = "DOCKER_HOST";

    @Test
    void allowsTheLoopbackNamesAndTheMachineItself() {
        LocalNames local = LocalNames.of(Map.of(), MACHINE);

        assertEquals(
            List.of(),
            Stream.of("localhost", "LocalHost", "api.localhost", MACHINE, "Build-Agent-7", "host.docker.internal")
                .filter(host -> !local.allows(host)).toList(),
            "every name that stays on the machine resolves, whatever its case"
        );
    }

    @Test
    void refusesEveryNameBeyondTheMachine() {
        LocalNames local = LocalNames.of(Map.of(), MACHINE);

        assertEquals(
            List.of(),
            Stream.of("example.com", "api.sandbox.example.com", "localhost.example.com", "build-agent-8")
                .filter(local::allows).toList(),
            "a name that only ends in a local one is no local name"
        );
    }

    @Test
    void allowsTheDockerHostReachedOverTcp() {
        LocalNames local = LocalNames.of(Map.of(DOCKER_HOST, " tcp://docker:2376 "), MACHINE);

        assertTrue(local.allows("docker"), "a remote daemon publishes the ports of the containers a test starts");
    }

    @Test
    void addsNothingForADockerHostReachedThroughASocketFile() {
        LocalNames socket = LocalNames.of(Map.of(DOCKER_HOST, "unix:///var/run/docker.sock"), MACHINE);
        LocalNames malformed = LocalNames.of(Map.of(DOCKER_HOST, "tcp://dock er:2376"), MACHINE);

        assertEquals(socket, LocalNames.of(Map.of(), MACHINE), "a socket file is on this machine already");
        assertEquals(malformed, LocalNames.of(Map.of(), MACHINE), "an address that does not parse names nothing");
    }

    @Test
    void allowsTheHostTestcontainersIsToldToUse() {
        LocalNames local = LocalNames.of(Map.of("TESTCONTAINERS_HOST_OVERRIDE", "Containers.Internal"), MACHINE);
        LocalNames blank = LocalNames.of(Map.of("TESTCONTAINERS_HOST_OVERRIDE", " "), MACHINE);

        assertTrue(local.allows("containers.internal"));
        assertFalse(blank.allows(" "), "a blank override names no host");
    }
}
