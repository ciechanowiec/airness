package eu.ciechanowiec.airness.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.util.stream.Stream;

/**
 * Resolves the names that stay on the machine through the runtime's own resolver, and refuses every other.
 *
 * <p>A refused name fails as a name that does not exist fails, with an exception the caller already handles, and
 * the message says why, so a test that reached for a remote service names the rule rather than a timeout. A
 * reverse lookup is answered for a loopback address alone, because any other one would ask a name server.
 *
 * @param builtin the runtime's own resolver
 * @param local   the names that stay on the machine
 */
record LoopbackResolver(InetAddressResolver builtin, LocalNames local) implements InetAddressResolver {

    private static final String REFUSAL = "%s is not resolved, because an Airness test reaches no network beyond "
        + "the machine it runs on. Run the service it names on this machine, for example in a container the test "
        + "starts, and reach it through localhost.";

    @Override
    public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
        if (!this.local.allows(host)) {
            throw new UnknownHostException(REFUSAL.formatted(host));
        }
        return this.builtin.lookupByName(host, lookupPolicy);
    }

    @Override
    public String lookupByAddress(byte[] address) throws UnknownHostException {
        if (!InetAddress.getByAddress(address).isLoopbackAddress()) {
            throw new UnknownHostException(REFUSAL.formatted("The name of " + InetAddress.getByAddress(address)));
        }
        return this.builtin.lookupByAddress(address);
    }
}
