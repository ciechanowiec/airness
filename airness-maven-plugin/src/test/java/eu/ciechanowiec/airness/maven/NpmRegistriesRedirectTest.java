package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * A registry that redirects never receives the token of another: the first request carries the token the
 * configuration names for the registry, a redirect to another scheme, host or port carries only the one it
 * names for the new address, a request that carried none never gains one, and HTTPS is never left for plain
 * HTTP. Proven against local HTTPS servers, which record the credential each request carried.
 */
@Timeout(120)
class NpmRegistriesRedirectTest {

    private static final String LISTING = "{\"versions\": {\"1.0.0\": {}}}";
    private static final String FIRST = "first-secret";
    private static final String SECOND = "second-secret";
    private static final String BEARER = "Bearer ";
    private static final String TOKEN_KEY = "/:_authToken";
    private static final String PACKAGE = "/npm/kit";

    @TempDir
    private Path directory;

    private NpmRegistries registries(TlsServers tls, Map<String, String> config) {
        HttpClient client = NpmRegistries.clientBuilder().sslContext(tls.context()).build();
        return new NpmRegistries(config, client);
    }

    @Test
    @SneakyThrows
    void dropsTheTokenOnARedirectToAnotherPort() {
        try (TlsServers tls = TlsServers.of(this.directory)) {
            RecordingServer first = tls.server();
            RecordingServer second = tls.server();
            first.redirect(PACKAGE, second.uri(PACKAGE));
            second.answer(PACKAGE, LISTING);
            NpmRegistries registries = this.registries(
                tls, Map.of("registry", first.uri("/npm/"), "//" + first.authority() + TOKEN_KEY, FIRST)
            );
            assertEquals(List.of("1.0.0"), registries.versions("kit"));
            assertEquals(List.of(Optional.of(BEARER + FIRST)), first.credentials(), "the registry gets its token");
            assertEquals(List.of(Optional.empty()), second.credentials(), "the other origin gets none");
        }
    }

    @Test
    @SneakyThrows
    void sendsAnotherOriginOnlyTheTokenTheConfigurationNamesForIt() {
        try (TlsServers tls = TlsServers.of(this.directory)) {
            RecordingServer first = tls.server();
            RecordingServer second = tls.server();
            first.redirect(PACKAGE, second.uri("/npm/moved/kit"));
            second.answer("/npm/moved/kit", LISTING);
            NpmRegistries registries = this.registries(
                tls, Map.of(
                    "registry", first.uri("/npm/"), "//" + first.authority() + TOKEN_KEY, FIRST,
                    "//" + second.authority() + "/npm/moved/:_authToken", SECOND
                )
            );
            assertEquals(List.of("1.0.0"), registries.versions("kit"));
            assertEquals(List.of(Optional.of(BEARER + SECOND)), second.credentials());
        }
    }

    @Test
    @SneakyThrows
    void keepsTheTokenWithinOneOriginAndNeverAddsOneToARequestThatCarriedNone() {
        try (TlsServers tls = TlsServers.of(this.directory)) {
            RecordingServer first = tls.server();
            RecordingServer second = tls.server();
            first.redirect(PACKAGE, first.uri("/npm/v2/kit"));
            first.answer("/npm/v2/kit", LISTING);
            first.redirect("/index.json", second.uri("/dist/index.json"));
            second.answer("/dist/index.json", "[{\"version\": \"v26.11.1\", \"lts\": \"X\"}]");
            NpmRegistries registries = this.registries(
                tls, Map.of(
                    "registry", first.uri("/npm/"), "//" + first.authority() + TOKEN_KEY, FIRST,
                    "//" + second.authority() + TOKEN_KEY, SECOND
                )
            );
            assertEquals(List.of("1.0.0"), registries.versions("kit"));
            assertEquals(List.of(Optional.of(BEARER + FIRST), Optional.of(BEARER + FIRST)), first.credentials());
            assertEquals(List.of("26.11.1"), registries.nodeLongTermReleases(first.uri("/index.json")));
            assertEquals(List.of(Optional.empty()), second.credentials(), "an anonymous request stays anonymous");
        }
    }

    @Test
    @SneakyThrows
    void refusesARedirectToPlainHttpAndAnEndlessChain() {
        try (TlsServers tls = TlsServers.of(this.directory); ArchiveServer plain = new ArchiveServer()) {
            RecordingServer first = tls.server();
            first.redirect(PACKAGE, plain.uri(PACKAGE).toString());
            first.redirect("/npm/loop", first.uri("/npm/loop"));
            NpmRegistries registries = this.registries(
                tls, Map.of("registry", first.uri("/npm/"), "//" + first.authority() + TOKEN_KEY, FIRST)
            );
            IllegalStateException downgrade = assertThrows(
                IllegalStateException.class, () -> registries.versions("kit")
            );
            assertTrue(Objects.requireNonNull(downgrade.getMessage()).contains("which is not HTTPS"));
            assertEquals(0, plain.requests(), "the plain address is never asked");
            IllegalStateException loop = assertThrows(IllegalStateException.class, () -> registries.versions("loop"));
            assertTrue(Objects.requireNonNull(loop.getMessage()).contains("redirected more than"));
        }
    }
}
