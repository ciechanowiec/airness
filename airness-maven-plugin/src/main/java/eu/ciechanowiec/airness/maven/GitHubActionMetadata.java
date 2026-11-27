package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ActionMetadataSource;
import eu.ciechanowiec.airness.governance.ActionReference;
import eu.ciechanowiec.airness.governance.TransientRetry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads an action's metadata from GitHub at the ref a workflow pins it to, keeping what a commit holds for good.
 *
 * <p>The metadata is read from {@code raw.githubusercontent.com}, which serves a public repository's files with no
 * credential and outside the rate limit of GitHub's API: the {@code action.yml} at the ref, else the
 * {@code action.yaml}. A ref that holds neither answers 404 for both, whether the repository, the ref or the path
 * is missing or private, and the action is then reported as one whose runtime cannot be read. Any other answer,
 * once {@link TransientRetry#REGISTRIES} has tried again after a rate limit, a server error or a failed
 * connection, fails the build naming the address, so an outage neither passes the rule nor reads as a finding.
 *
 * <p>A commit never changes, so the metadata a commit holds is kept under the local Maven repository, beside the
 * pinned toolchain, and read from there by every later build: a project whose actions are pinned by commit reads
 * GitHub once per action and never again. A tag or a branch can be moved to another commit at any time, so its
 * metadata is read again by every build and never kept. The file is written beside its final name and moved into
 * place in one step, so a build that stops halfway leaves nothing, and two builds writing the same commit both
 * leave its one content.
 *
 * <p>{@code GITHUB_TOKEN}, when the environment sets it, is sent as a bearer credential so that an action in a
 * private repository can be read and the rate limit of an authenticated reader applies. It is sent only over HTTPS
 * and only to the address the metadata is read from: redirects are never followed, so it reaches no other origin.
 * No message names it.
 *
 * @param base     the address files are read from, ending in a slash
 * @param client   the HTTP client every request of one build shares
 * @param exchange how one request is sent and answered
 * @param token    the credential, sent only when the base is HTTPS
 * @param cache    the directory the metadata of a commit is kept in
 */
record GitHubActionMetadata(
    String base, HttpClient client, MetadataExchange exchange, Optional<String> token, Path cache
) implements ActionMetadataSource {

    /**
     * Where GitHub serves a repository's files at a ref.
     */
    static final String PUBLIC = "https://raw.githubusercontent.com/";

    /**
     * The environment variable whose credential is sent.
     */
    static final String TOKEN_VARIABLE = "GITHUB_TOKEN";

    private static final List<String> NAMES = List.of("action.yml", "action.yaml");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    // Unauthorized, forbidden and too many requests: a credential, or a pause, may let a later build read it.
    private static final Set<Integer> REFUSALS = Set.of(401, 403, 429);
    private static final String HTTPS = "https";
    private static final String SLASH = "/";
    private static final HttpClient SHARED = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    /**
     * Reads from GitHub, with the environment's credential, keeping the metadata of a commit under the local Maven
     * repository.
     *
     * @param localRepository the local Maven repository
     * @param environment     the environment, which may name a credential
     * @return the reader
     */
    static GitHubActionMetadata of(Path localRepository, Map<String, String> environment) {
        Optional<String> credential = Optional.ofNullable(environment.get(TOKEN_VARIABLE))
            .map(String::strip)
            .filter(value -> !value.isEmpty());
        return new GitHubActionMetadata(
            PUBLIC, SHARED, TransientRetry.REGISTRIES::send, credential, cache(localRepository)
        );
    }

    /**
     * Where the metadata of a commit is kept, beneath the local Maven repository.
     *
     * @param localRepository the local Maven repository
     * @return the cache directory
     */
    static Path cache(Path localRepository) {
        return localRepository.resolve(".airness").resolve("action-metadata");
    }

    @Override
    public Optional<String> metadata(ActionReference action) {
        if (!action.immutable()) {
            return this.fetched(action).map(Answer::text);
        }
        return this.kept(action).or(() -> this.fetched(action).map(answer -> this.keep(action, answer)));
    }

    private Optional<String> kept(ActionReference action) {
        return NAMES.stream()
            .map(name -> this.copy(action, name))
            .filter(Files::isRegularFile)
            .findFirst()
            .map(GitHubActionMetadata::read);
    }

    // The stream asks for the next name only after the one before it was missing.
    private Optional<Answer> fetched(ActionReference action) {
        return NAMES.stream()
            .flatMap(name -> this.fetch(action, name).map(text -> new Answer(name, text)).stream())
            .findFirst();
    }

    private String keep(ActionReference action, Answer answer) {
        store(this.copy(action, answer.name()), answer.text());
        return answer.text();
    }

    // Every part has passed the reader's alphabet, with no "." or ".." segment, so it stays beneath the cache.
    private Path copy(ActionReference action, String name) {
        Path directory = this.cache.resolve(action.owner()).resolve(action.repository());
        Path within = Stream.of(action.path().split(SLASH))
            .filter(segment -> !segment.isEmpty())
            .reduce(directory, Path::resolve, (first, _) -> first);
        return within.resolve(action.revision()).resolve(name);
    }

    private Optional<String> fetch(ActionReference action, String name) {
        String directory = action.path().isEmpty() ? "" : action.path() + SLASH;
        URI address = URI.create(
            this.base + action.owner() + SLASH + action.repository() + SLASH + action.revision() + SLASH + directory
                + name
        );
        HttpResponse<String> response = this.send(address, this.request(address));
        int status = response.statusCode();
        if (status == OK) {
            return Optional.of(response.body());
        }
        if (status == NOT_FOUND) {
            return Optional.empty();
        }
        throw unanswered(address, status, action);
    }

    private HttpRequest request(URI address) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(address).timeout(TIMEOUT);
        if (HTTPS.equalsIgnoreCase(address.getScheme())) {
            this.token.ifPresent(value -> builder.header("Authorization", "Bearer " + value));
        }
        return builder.GET().build();
    }

    private static IllegalStateException unanswered(URI address, int status, ActionReference action) {
        String hint = REFUSALS.contains(status)
            ? ": set " + TOKEN_VARIABLE + " to a token that can read the repository, or wait for the rate limit to pass"
            : "";
        return new IllegalStateException(
            address + " answered " + status + hint + ", so the action runtime check fails rather than passing without"
                + " the metadata of " + action
        );
    }

    private HttpResponse<String> send(URI address, HttpRequest request) {
        try {
            return this.exchange.send(this.client, request);
        } catch (IOException exception) {
            throw new IllegalStateException(
                address + " could not be read, so the action runtime check fails rather than passing without it: "
                    + exception.getMessage(),
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading " + address, exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the kept action metadata " + file, exception);
        }
    }

    private static void store(Path target, String metadata) {
        try {
            Files.createDirectories(target.getParent());
            stage(Files.createTempFile(target.getParent(), ".staging-", ".tmp"), target, metadata);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not keep the action metadata at " + target, exception);
        }
    }

    private static void stage(Path staging, Path target, String metadata) throws IOException {
        try {
            Files.writeString(staging, metadata);
            moveInto(staging, target);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    // Another build may have kept the same commit meanwhile. A commit holds one content, so its copy is as good as
    // this one, and only a failure with no file at the target is reported.
    private static void moveInto(Path staging, Path target) throws IOException {
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            if (!Files.isRegularFile(target)) {
                throw exception;
            }
        }
    }

    /**
     * One file an address answered with.
     *
     * @param name the name of the file
     * @param text its content
     */
    private record Answer(String name, String text) {
    }
}
