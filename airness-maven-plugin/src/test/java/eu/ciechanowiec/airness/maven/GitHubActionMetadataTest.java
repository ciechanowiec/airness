package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.ActionReference;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * An action's metadata is read at its ref, the {@code action.yml} before the {@code action.yaml}, kept for good when
 * the ref is a commit and read again when it is not, and anything but an answer or a 404 fails rather than passing.
 * The credential reaches only an HTTPS address and no message. Proven against local servers.
 */
@Timeout(120)
class GitHubActionMetadataTest {

    private static final String COMMIT = "11bd71901bbe5b1630ceea73d27597364c9af683";
    private static final ActionReference PINNED = new ActionReference("actions", "checkout", "", COMMIT);
    private static final ActionReference TAGGED = new ActionReference("actions", "checkout", "", "v4");
    private static final String METADATA = "runs:\n  using: node20\n";
    private static final String CREDENTIAL = "loopback-credential";
    private static final byte[] SERVED = METADATA.getBytes(StandardCharsets.UTF_8);
    private static final int SERVER_ERROR = 500;
    // A lead byte of a two-byte sequence followed by a byte that cannot continue it.
    private static final byte[] INVALID_UTF8 = {(byte) 0xC3, (byte) 0x28};
    private static final String YML = "/actions/checkout/" + COMMIT + "/action.yml";
    private static final String YAML = "/actions/checkout/" + COMMIT + "/action.yaml";
    private static final MetadataExchange PLAIN = (client, request) -> client.send(
        request, HttpResponse.BodyHandlers.ofString()
    );

    @TempDir
    private Path cache;

    private GitHubActionMetadata reader(ArchiveServer server, Optional<String> credential) {
        return new GitHubActionMetadata(
            server.uri("/").toString(), HttpClient.newHttpClient(), PLAIN, credential, this.cache
        );
    }

    // The address of a server that has stopped, which no request reaches.
    private static String closedAddress() {
        try (ArchiveServer server = new ArchiveServer()) {
            return server.uri("/").toString();
        }
    }

    @Test
    void readsTheActionYmlAtTheRevision() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve(YML, SERVED);
            assertEquals(Optional.of(METADATA), this.reader(server, Optional.empty()).metadata(PINNED));
            assertEquals(1, server.requests());
        }
    }

    @Test
    void readsAnActionInADirectoryOfItsRepository() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/github/codeql-action/v3/upload-sarif/nested/action.yml", SERVED);
            ActionReference action = new ActionReference("github", "codeql-action", "upload-sarif/nested", "v3");
            assertEquals(Optional.of(METADATA), this.reader(server, Optional.empty()).metadata(action));
        }
    }

    @Test
    void readsTheActionYamlWhenTheRevisionHoldsNoActionYml() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve(YAML, SERVED);
            assertEquals(Optional.of(METADATA), this.reader(server, Optional.empty()).metadata(PINNED));
            assertEquals(2, server.requests());
            assertTrue(Files.isRegularFile(this.cache.resolve("actions/checkout/" + COMMIT + "/action.yaml")));
        }
    }

    @Test
    void answersNothingWhenTheRevisionHoldsNeitherFile() {
        try (ArchiveServer server = new ArchiveServer()) {
            assertEquals(Optional.empty(), this.reader(server, Optional.empty()).metadata(PINNED));
            assertEquals(2, server.requests());
            assertFalse(Files.exists(this.cache.resolve("actions")), "nothing is kept for a missing file");
        }
    }

    @Test
    void keepsTheMetadataOfACommitAndReadsItFromThereAfterwards() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve(YML, SERVED);
            GitHubActionMetadata reader = this.reader(server, Optional.empty());
            reader.metadata(PINNED);
            assertEquals(Optional.of(METADATA), reader.metadata(PINNED));
            assertEquals(1, server.requests(), "the second read comes from the kept copy");
            assertEquals(Optional.of(METADATA), this.reader(server, Optional.empty()).metadata(PINNED));
            assertEquals(1, server.requests(), "so does the read of a later build");
        }
    }

    @Test
    @SneakyThrows
    void readsAKeptActionYamlWithoutAskingForAnActionYml() {
        Path kept = this.cache.resolve("actions/checkout/" + COMMIT + "/action.yaml");
        Files.createDirectories(kept.getParent());
        Files.writeString(kept, METADATA);
        try (ArchiveServer server = new ArchiveServer()) {
            assertEquals(Optional.of(METADATA), this.reader(server, Optional.empty()).metadata(PINNED));
            assertEquals(0, server.requests());
        }
    }

    @Test
    @SneakyThrows
    void keepsTheMetadataOfADirectoryBeneathItsRepository() {
        String commit = "3d3c42e5aac5ba805825da76410c181273ba90b1";
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/octo/tools/" + commit + "/setup/java/action.yml", SERVED);
            this.reader(server, Optional.empty()).metadata(new ActionReference("octo", "tools", "setup/java", commit));
        }
        assertEquals(METADATA, Files.readString(this.cache.resolve("octo/tools/setup/java/" + commit + "/action.yml")));
        try (Stream<Path> staged = Files.list(this.cache.resolve("octo/tools/setup/java/" + commit))) {
            assertEquals(List.of("action.yml"), staged.map(file -> file.getFileName().toString()).toList());
        }
    }

    @Test
    void readsATagAgainOnEveryReadAndKeepsNothing() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/actions/checkout/v4/action.yml", SERVED);
            GitHubActionMetadata reader = this.reader(server, Optional.empty());
            reader.metadata(TAGGED);
            assertEquals(Optional.of(METADATA), reader.metadata(TAGGED));
            assertEquals(2, server.requests());
            assertFalse(Files.exists(this.cache.resolve("actions")));
        }
    }

    @Test
    @SneakyThrows
    void failsWhenTheMetadataCannotBeKept() {
        Path blocked = this.cache.resolve("actions/checkout/" + COMMIT + "/action.yml");
        Files.createDirectories(blocked.resolve("occupied"));
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve(YML, SERVED);
            GitHubActionMetadata reader = this.reader(server, Optional.empty());
            UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> reader.metadata(PINNED));
            assertTrue(String.valueOf(failure.getMessage()).contains(blocked.toString()), failure::getMessage);
        }
    }

    @Test
    void failsOnAServerErrorNamingTheAddress() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.refuse(YML, SERVER_ERROR);
            GitHubActionMetadata reader = this.reader(server, Optional.empty());
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> reader.metadata(PINNED));
            assertEquals(
                server.uri(YML) + " answered 500, so the action runtime check fails rather than passing without the"
                    + " metadata of actions/checkout@" + COMMIT,
                failure.getMessage()
            );
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429})
    void namesTheCredentialWhenTheAnswerIsARefusalOrARateLimit(int status) {
        try (ArchiveServer server = new ArchiveServer()) {
            server.refuse("/actions/checkout/v4/action.yml", status);
            GitHubActionMetadata reader = this.reader(server, Optional.empty());
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> reader.metadata(TAGGED));
            assertTrue(
                String.valueOf(failure.getMessage()).contains(
                    " answered " + status + ": set GITHUB_TOKEN to a token that can read the repository"
                ),
                failure::getMessage
            );
        }
    }

    @Test
    void failsWhenTheAddressCannotBeReached() {
        String base = closedAddress();
        String address = base + YML.substring(1);
        GitHubActionMetadata reader = new GitHubActionMetadata(
            base, HttpClient.newHttpClient(), PLAIN, Optional.empty(), this.cache
        );
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> reader.metadata(PINNED));
        assertTrue(
            String.valueOf(failure.getMessage()).startsWith(
                address + " could not be read, so the action runtime check fails rather than passing without it: "
            ),
            failure::getMessage
        );
    }

    @Test
    void stopsWhenInterruptedAndKeepsTheInterruption() {
        GitHubActionMetadata reader = new GitHubActionMetadata(
            "http://127.0.0.1:9/", HttpClient.newHttpClient(), (_, _) -> {
                throw new InterruptedException("stopped");
            }, Optional.empty(), this.cache
        );
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> reader.metadata(TAGGED));
            assertEquals(
                "Interrupted while reading http://127.0.0.1:9/actions/checkout/v4/action.yml", failure.getMessage()
            );
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted(), "the interruption is cleared for the next test");
        }
    }

    @Test
    void sendsNoCredentialOverPlainHttp() {
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve(YML, SERVED);
            this.reader(server, Optional.of(CREDENTIAL)).metadata(PINNED);
            assertEquals(List.of(Optional.empty()), server.credentials());
        }
    }

    @Test
    @SneakyThrows
    void sendsTheCredentialOverHttpsAndFollowsNoRedirect(@TempDir Path keys) {
        try (
            TlsServers tls = TlsServers.of(keys);
            HttpClient client = HttpClient.newBuilder()
                .sslContext(tls.context())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        ) {
            RecordingServer github = tls.server();
            RecordingServer elsewhere = tls.server();
            github.answer(YML, METADATA);
            github.redirect("/actions/checkout/v4/action.yml", elsewhere.uri("/actions/checkout/v4/action.yml"));
            GitHubActionMetadata reader = new GitHubActionMetadata(
                github.uri("/"), client, PLAIN, Optional.of(CREDENTIAL), this.cache
            );
            assertEquals(Optional.of(METADATA), reader.metadata(PINNED));
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> reader.metadata(TAGGED));
            assertTrue(String.valueOf(failure.getMessage()).contains(" answered 302,"), failure::getMessage);
            assertFalse(String.valueOf(failure.getMessage()).contains(CREDENTIAL), "no message names the credential");
            assertEquals(
                List.of(Optional.of("Bearer " + CREDENTIAL), Optional.of("Bearer " + CREDENTIAL)), github.credentials()
            );
            assertEquals(List.of(), elsewhere.credentials(), "the redirect is not followed");
        }
    }

    @Test
    void readsGithubWithTheCredentialTheEnvironmentNames(@TempDir Path repository) {
        GitHubActionMetadata reader = GitHubActionMetadata.of(
            repository, Map.of("GITHUB_TOKEN", " " + CREDENTIAL + "\n")
        );
        assertEquals("https://raw.githubusercontent.com/", reader.base());
        assertEquals(Optional.of(CREDENTIAL), reader.token());
        assertEquals(repository.resolve(".airness").resolve("action-metadata"), reader.cache());
        assertEquals(Optional.empty(), GitHubActionMetadata.of(repository, Map.of("GITHUB_TOKEN", "  ")).token());
        assertEquals(Optional.empty(), GitHubActionMetadata.of(repository, Map.of()).token());
    }

    @Test
    @SneakyThrows
    void failsWhenAKeptCopyCannotBeRead() {
        Path kept = this.cache.resolve("actions/checkout/" + COMMIT + "/action.yml");
        Files.createDirectories(kept.getParent());
        Files.write(kept, INVALID_UTF8);
        try (ArchiveServer server = new ArchiveServer()) {
            GitHubActionMetadata reader = this.reader(server, Optional.empty());
            UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> reader.metadata(PINNED));
            assertTrue(String.valueOf(failure.getMessage()).contains(kept.toString()), failure::getMessage);
            assertInstanceOf(IOException.class, failure.getCause());
        }
    }
}
