package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.OwnedCoordinate;
import eu.ciechanowiec.airness.governance.VersionUpdate;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One freshness bound holds for npm packages and the pinned toolchain, read from the registry pnpm installs
 * from, and a listing that cannot be read fails the bound rather than passing it.
 */
class NpmFreshnessTest {

    private static final String WEB = "web";
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final String PRIVATE = "private";
    private static final String CREDENTIAL = String.join(":", "reader", "secret");

    @Test
    void reportsEveryNewerStableReleaseAndFailsOnlyTwoMajorsBehind() {
        OwnedCoordinate behind = NpmFreshness.npm(WEB, "vitest", "3.2.0");
        OwnedCoordinate close = NpmFreshness.npm(WEB, "typescript", "5.9.0");
        OwnedCoordinate canary = NpmFreshness.npm(WEB, "next", "16.0.0-canary.1");
        Map<String, List<String>> listings = Map.of(
            "vitest", List.of("3.2.0", "5.0.5", "6.0.0-beta.1"), "typescript", List.of("5.9.0", "6.0.1")
        );
        NpmFreshness check = NpmFreshness.of(
            List.of(behind, close, canary), entry -> listings.get(entry.coordinate().artifactId())
        );
        assertEquals(2, check.scanned(), "a pre-release declaration is not measured");
        assertEquals(
            List.of("[web] npm:vitest 3.2.0 -> 5.0.5", "[web] npm:typescript 5.9.0 -> 6.0.1"),
            check.updates().stream().map(VersionUpdate::report).toList()
        );
        List<Findings> findings = check.findings();
        assertEquals(
            List.of("[web] npm:vitest is at major 3 but the latest stable major is 5"), findings.getFirst().offences()
        );
        assertTrue(findings.get(1).clean());
    }

    @Test
    void measuresOnlyAPlainReleaseAndReportsOnlyANewerOne() {
        NpmFreshness check = NpmFreshness.of(
            List.of(
                NpmFreshness.npm(WEB, "current", "2.0.0"), NpmFreshness.npm(WEB, "ranged", "1.x"),
                NpmFreshness.npm(WEB, "empty", ""), NpmFreshness.npm(WEB, "gap", "1..0")
            ),
            _ -> List.of("2.0.0", "1.9.0", "", "2.x")
        );
        assertEquals(1, check.scanned(), "a range, an empty or a malformed version is not measured");
        assertEquals(List.of(), check.updates(), "the latest stable release is the declared one");
    }

    @Test
    void failsTheBoundForAListingThatCannotBeRead() {
        NpmFreshness check = NpmFreshness.of(
            List.of(NpmFreshness.npm(WEB, PRIVATE, "1.0.0"), NpmFreshness.npm(WEB, "only-canaries", "1.0.0")),
            entry -> {
                if (PRIVATE.equals(entry.coordinate().artifactId())) {
                    throw new IllegalStateException("https://registry.example/private answered 401");
                }
                return List.of("2.0.0-rc.1");
            }
        );
        assertEquals(
            List.of(
                "[web] private: https://registry.example/private answered 401",
                "[web] only-canaries: no stable release of only-canaries"
            ),
            check.findings().get(1).offences()
        );
    }

    @Test
    @SneakyThrows
    void readsTheRegistryAndTheTokenAsPnpmReadsTheConfiguration(@TempDir Path directory) {
        Files.writeString(
            directory.resolve(".npmrc"), "# comment\n@airness:registry=${REGISTRY}\nregistry=https://p/\n"
        );
        Path user = Files.writeString(
            directory.resolve("user.npmrc"),
            """
                registry=https://user.example/npm
                //npm.example.com/:_authToken=root
                //npm.example.com/private/:_authToken=${TOKEN}
                //npm.example.com.evil.net/:_authToken=evil
                """
        );
        NpmRegistries registries = NpmRegistries.of(
            directory, Map.of(
                "REGISTRY", "http://127.0.0.1:4873/", "npm_config_userconfig", user.toString(), "TOKEN", "deep",
                "PNPM_CONFIG_REGISTRY", "https://mirror.example/npm", "npm_config_registry", "https://ignored.example/"
            )
        );
        assertEquals("http://127.0.0.1:4873/", registries.registry("@airness/node"), "a scope's registry first");
        assertEquals(
            "https://mirror.example/npm/", registries.registry("vitest"),
            "a pnpm_config_ variable wins over the project's file, which wins over the user's, and pnpm 12 reads"
                + " no npm_config_ registry"
        );
        assertEquals(Optional.of("deep"), registries.token("https://npm.example.com/private/npm/"));
        assertEquals(Optional.of("root"), registries.token("https://npm.example.com/public/"));
        assertEquals(Optional.empty(), registries.token("https://npm.example.com.evil.net.attacker/"));
        assertEquals(Optional.empty(), registries.token("http://npm.example.com/"), "no token over plain HTTP");
        NpmRegistries none = NpmRegistries.of(directory.resolve("absent"), Map.of("NPM_CONFIG_USERCONFIG", "absent"));
        assertEquals(NpmRegistries.PUBLIC, none.registry("@scope/kit"));
        assertTrue(NpmRegistries.of(directory.resolve("absent"), Map.of()).registry("x").endsWith("/"));
        NpmRegistries ported = new NpmRegistries(
            Map.of(
                "//registry.example:8443/:_authToken", "ported", "//:_authToken", "nobody", "registry", "https://r/"
            ),
            HttpClient.newHttpClient()
        );
        assertEquals(Optional.of("ported"), ported.token("https://registry.example:8443/npm/"));
        assertEquals(Optional.empty(), ported.token("https://registry.example/npm/"), "the port is part of the host");
        assertEquals("https://r/", ported.registry("@other/kit"), "a scope with no registry of its own");
        assertEquals(
            "https://host:8443/npm/x", NpmRegistries.redacted("https://" + CREDENTIAL + "@host:8443/npm/x?token=1"),
            "a credential in a configured registry address never reaches a finding"
        );
    }

    @Test
    void readsTheEnvironmentAsPnpm12ReadsIt(@TempDir Path directory) {
        NpmRegistries environment = NpmRegistries.of(
            directory, Map.of(
                "npm_config_userconfig", directory.resolve("absent").toString(), "pnpm_config_registry",
                "https://r.example/",
                "npm_config_//npm.example.com/Team/:_authToken", "npm", "pnpm_config_//npm.example.com/:_authToken",
                "pnpm", "pnpm_config_@airness:registry", "https://ignored.example/"
            )
        );
        assertEquals(Optional.of("npm"), environment.token("https://npm.example.com/Team/"), "the path keeps its case");
        assertEquals(Optional.of("pnpm"), environment.token("https://npm.example.com/public/"));
        assertEquals("https://r.example/", environment.registry("@airness/node"), "no scope registry from variables");
    }

    @Test
    void readsTheVersionsAndTheLongTermReleasesAListingNames() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/npm/vitest", bytes("{\"versions\": {\"5.0.5\": {}, \"6.0.0\": {}}}"));
            server.serve("/npm/shapeless", bytes("{\"versions\": []}"));
            String index = server.serve(
                "/index.json", bytes(
                    "[{\"version\": \"v27.0.0\", \"lts\": false}, {\"version\": \"v26.11.1\", \"lts\": \"X\"}]"
                )
            ).toString();
            NpmRegistries registries = new NpmRegistries(
                Map.of("registry", server.uri("/npm").toString()), HttpClient.newHttpClient()
            );
            assertEquals(List.of("5.0.5", "6.0.0"), registries.versions("vitest"));
            assertEquals(List.of("26.11.1"), registries.nodeLongTermReleases(index), "only long-term-support releases");
            assertThrows(IllegalStateException.class, () -> registries.versions("shapeless"));
            assertThrows(
                IllegalStateException.class, () -> registries.nodeLongTermReleases(server.uri("/npm/vitest").toString())
            );
            IllegalStateException missing = assertThrows(
                IllegalStateException.class, () -> registries.versions("absent")
            );
            assertTrue(Objects.requireNonNull(missing.getMessage()).contains("answered 404"));
            server.refuse("/npm/forbidden", FORBIDDEN);
            assertThrows(IllegalStateException.class, () -> registries.versions("forbidden"));
            server.refuse("/npm/private", UNAUTHORIZED);
            IllegalStateException refused = assertThrows(
                IllegalStateException.class, () -> registries.versions("private")
            );
            assertTrue(
                Objects.requireNonNull(refused.getMessage()).contains(
                    "needs a credential the npm configuration does not"
                ),
                "a private registry is named, never a crash and never a pass"
            );
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
