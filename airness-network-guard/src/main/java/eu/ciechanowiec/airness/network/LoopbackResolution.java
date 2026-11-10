package eu.ciechanowiec.airness.network;

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;

/**
 * Installs the {@link LoopbackResolver} in place of the runtime's own resolver.
 *
 * <p>The runtime finds this through the service registration beside it, the first time anything resolves a host
 * name, so a test needs nothing beyond this artifact on its classpath. The Java parent puts it there at test
 * scope for every module, Surefire's and Failsafe's runs alike. The runtime's base type is extended because the
 * service it loads is declared as that type.
 */
public final class LoopbackResolution extends InetAddressResolverProvider {

    private static final String NAME = "airness-loopback";

    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new LoopbackResolver(
            configuration.builtinResolver(), LocalNames.of(System.getenv(), configuration.lookupLocalHostName())
        );
    }

    @Override
    public String name() {
        return NAME;
    }
}
