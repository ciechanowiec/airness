package eu.ciechanowiec.airness.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * The resolver as the runtime installs it: this module's own tests run with the service registration on their
 * classpath, as a consumer's do, so every lookup here goes through the guard.
 */
class LoopbackResolutionTest {

    private static final byte[] LOOPBACK = {127, 0, 0, 1};
    private static final byte[] DOCUMENTATION = {(byte) 192, 0, 2, 1};
    private static final String REMOTE = "airness-guard.example.com";

    @Test
    void isTheResolverTheRuntimeInstalled() {
        assertEquals("airness-loopback", new LoopbackResolution().name());
    }

    @Test
    @SneakyThrows
    void resolvesLocalhostToALoopbackAddress() {
        assertTrue(Arrays.stream(InetAddress.getAllByName("localhost")).allMatch(InetAddress::isLoopbackAddress));
    }

    @Test
    void refusesARemoteNameWithTheRule() {
        UnknownHostException refusal = assertThrows(UnknownHostException.class, () -> InetAddress.getAllByName(REMOTE));

        String message = String.valueOf(refusal.getMessage());
        assertTrue(message.contains(REMOTE), message);
        assertTrue(message.contains("reaches no network beyond the machine it runs on"), message);
    }

    @Test
    @SneakyThrows
    void namesALoopbackAddress() {
        String name = InetAddress.getByAddress(LOOPBACK).getCanonicalHostName();

        assertTrue(name.startsWith("localhost"), () -> "the machine names its loopback address, but named " + name);
    }

    @Test
    @SneakyThrows
    void asksNoNameServerForTheNameOfARemoteAddress() {
        InetAddress remote = InetAddress.getByAddress(DOCUMENTATION);

        assertEquals(
            remote.getHostAddress(), remote.getCanonicalHostName(),
            "a refused reverse lookup leaves the address as its own name"
        );
    }
}
