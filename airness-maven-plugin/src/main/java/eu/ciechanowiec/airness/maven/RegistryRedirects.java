package eu.ciechanowiec.airness.maven;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The decisions a registry read makes about a redirect: where it points, whether it may be followed, and
 * whether it stays within the origin the token was configured for.
 */
@UtilityClass
final class RegistryRedirects {

    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final int HTTPS_PORT = 443;
    private static final int HTTP_PORT = 80;
    private static final int MOVED_PERMANENTLY = 301;
    private static final int FOUND = 302;
    private static final int SEE_OTHER = 303;
    private static final int TEMPORARY_REDIRECT = 307;
    private static final int PERMANENT_REDIRECT = 308;
    private static final Set<Integer> REDIRECTS = Set.of(
        MOVED_PERMANENTLY, FOUND, SEE_OTHER, TEMPORARY_REDIRECT, PERMANENT_REDIRECT
    );

    /**
     * Where a redirect points, resolved against the address that answered it.
     *
     * @param address  the address that answered
     * @param status   the status of its answer
     * @param location the answer's {@code Location} header, or nothing
     * @return the target, or nothing when the answer is no redirect or names no location
     */
    static Optional<URI> target(URI address, int status, Optional<String> location) {
        try {
            return REDIRECTS.contains(status) ? location.map(address::resolve) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                NpmRegistries.redacted(address.toString()) + " redirected to an address that does not parse", exception
            );
        }
    }

    /**
     * Whether a redirect may be followed: to HTTPS from anywhere, and to plain HTTP only from plain HTTP, as a
     * local registry is reached. HTTPS is never left for plain HTTP, nor for any other scheme.
     *
     * @param from the address that answered
     * @param to   the target
     * @return whether the redirect may be followed
     */
    static boolean permitted(URI from, URI to) {
        boolean secure = HTTPS.equalsIgnoreCase(to.getScheme());
        boolean plainToPlain = HTTP.equalsIgnoreCase(to.getScheme()) && HTTP.equalsIgnoreCase(from.getScheme());
        return secure || plainToPlain;
    }

    /**
     * Whether two addresses share one origin: the same scheme, host and port, the port a scheme implies counted
     * as written.
     *
     * @param first  one address
     * @param second the other
     * @return whether they share the origin
     */
    static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
            && Optional.ofNullable(first.getHost()).orElse("")
                .equalsIgnoreCase(Optional.ofNullable(second.getHost()).orElse(""))
            && port(first) == port(second);
    }

    private static int port(URI address) {
        if (address.getPort() >= 0) {
            return address.getPort();
        }
        return HTTPS.equalsIgnoreCase(address.getScheme()) ? HTTPS_PORT : HTTP_PORT;
    }
}
