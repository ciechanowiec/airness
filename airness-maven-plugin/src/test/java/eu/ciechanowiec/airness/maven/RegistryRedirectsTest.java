package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A redirect is followed only where it points by a status that redirects, HTTPS is never left, and one origin is
 * one scheme, host and port, the port a scheme implies counted as written.
 */
class RegistryRedirectsTest {

    private static final URI SECURE = URI.create("https://npm.example/npm/kit");
    private static final URI PLAIN = URI.create("http://127.0.0.1:4873/kit");
    private static final int FOUND = 302;
    private static final int PERMANENT = 308;
    private static final int OK = 200;

    @Test
    void resolvesTheLocationOfARedirectAndNothingElse() {
        assertEquals(
            Optional.of(URI.create("https://npm.example/v2/kit")),
            RegistryRedirects.target(SECURE, FOUND, Optional.of("/v2/kit"))
        );
        assertEquals(
            Optional.of(URI.create("https://cdn.example/kit")),
            RegistryRedirects.target(SECURE, PERMANENT, Optional.of("https://cdn.example/kit"))
        );
        assertEquals(Optional.empty(), RegistryRedirects.target(SECURE, FOUND, Optional.empty()));
        assertEquals(Optional.empty(), RegistryRedirects.target(SECURE, OK, Optional.of("/v2/kit")));
        IllegalStateException malformed = assertThrows(
            IllegalStateException.class, () -> RegistryRedirects.target(SECURE, FOUND, Optional.of("https://[bad"))
        );
        assertTrue(Objects.requireNonNull(malformed.getMessage()).startsWith("https://npm.example/npm/kit"));
    }

    @Test
    void neverLeavesHttps() {
        assertTrue(RegistryRedirects.permitted(SECURE, URI.create("https://cdn.example/kit")));
        assertTrue(RegistryRedirects.permitted(SECURE, URI.create("HTTPS://cdn.example/kit")), "a scheme has no case");
        assertTrue(RegistryRedirects.permitted(PLAIN, SECURE));
        assertTrue(RegistryRedirects.permitted(PLAIN, URI.create("http://127.0.0.1:4874/kit")));
        assertFalse(RegistryRedirects.permitted(SECURE, PLAIN));
        assertFalse(RegistryRedirects.permitted(PLAIN, URI.create("ftp://127.0.0.1/kit")));
    }

    @Test
    void readsOneOriginAsOneSchemeHostAndPort() {
        assertTrue(RegistryRedirects.sameOrigin(SECURE, URI.create("https://NPM.example:443/other")));
        assertTrue(RegistryRedirects.sameOrigin(PLAIN, URI.create("http://127.0.0.1:4873/other")));
        assertTrue(
            RegistryRedirects.sameOrigin(URI.create("http://npm.example/a"), URI.create("http://npm.example:80/b"))
        );
        assertFalse(RegistryRedirects.sameOrigin(SECURE, URI.create("http://npm.example/npm/kit")));
        assertFalse(RegistryRedirects.sameOrigin(SECURE, URI.create("https://npm.example:8443/npm/kit")));
        assertFalse(RegistryRedirects.sameOrigin(SECURE, URI.create("https://cdn.example/npm/kit")));
        assertFalse(RegistryRedirects.sameOrigin(SECURE, URI.create("https:/no-host")));
    }
}
