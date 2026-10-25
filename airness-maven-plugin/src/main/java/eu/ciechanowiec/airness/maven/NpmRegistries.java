package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Where an npm package's releases are listed, and the releases listed there, read as pnpm reads them.
 *
 * <p>The configuration is the one {@code release-age} reads: the user's {@code .npmrc}
 * ({@code npm_config_userconfig}, else {@code ~/.npmrc}), then the project's, then the {@code npm_config_}
 * variables, a later source winning, with {@code ${NAME}} expanded from the environment. A package is asked of
 * its scope's registry, else the configured one, else the public one, with the token of the
 * {@code //host[:port]/path/:_authToken} whose host and port equal the registry's exactly and whose path is the
 * longest run of the registry's leading segments. No token is sent over plain HTTP. A registry that refuses the
 * request is named in the finding, never a crash and never a pass.
 *
 * @param config the merged configuration, every key as an {@code .npmrc} writes it
 * @param client the HTTP client every request of one build shares
 */
record NpmRegistries(Map<String, String> config, HttpClient client) {

    /**
     * The public registry, which answers when the configuration names none.
     */
    static final String PUBLIC = "https://registry.npmjs.org/";

    /**
     * The Node release index, which lists every Node release and the long-term-support line of each.
     */
    static final String NODE_RELEASES = "https://nodejs.org/dist/index.json";

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private static final String PREFIX = "npm_config_";
    private static final String REGISTRY = "registry";
    private static final String TOKEN = ":_authToken";
    private static final String SLASH = "/";
    private static final String HTTPS = "https";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient SHARED = HttpClient.newBuilder().connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL).build();

    /**
     * Copies the configuration, so the answer cannot change after it was read.
     *
     * @param config the configuration
     * @param client the HTTP client
     */
    NpmRegistries {
        config = Map.copyOf(config);
    }

    /**
     * Reads the npm configuration a node project installs with.
     *
     * @param directory   the node project directory, whose {@code .npmrc} is read
     * @param environment the environment, which names the user configuration, adds {@code npm_config_}
     *                    settings and expands {@code ${...}}
     * @return the registries
     */
    static NpmRegistries of(Path directory, Map<String, String> environment) {
        Path user = Optional.ofNullable(environment.get(PREFIX + "userconfig"))
            .or(() -> Optional.ofNullable(environment.get((PREFIX + "userconfig").toUpperCase(Locale.ROOT))))
            .map(Path::of)
            .orElseGet(() -> Path.of(System.getProperty("user.home"), ".npmrc"));
        UnaryOperator<String> expand = value -> VARIABLE.matcher(value).replaceAll(
            match -> Matcher.quoteReplacement(environment.getOrDefault(match.group(1), ""))
        );
        Stream<Map.Entry<String, String>> variables = environment.entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).startsWith(PREFIX))
            .map(
                entry -> Map.entry(entry.getKey().substring(PREFIX.length()).toLowerCase(Locale.ROOT), entry.getValue())
            );
        Map<String, String> merged = Stream.of(read(user, expand), read(directory.resolve(".npmrc"), expand), variables)
            .flatMap(stream -> stream)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (_, later) -> later));
        return new NpmRegistries(merged, SHARED);
    }

    /**
     * The registry a package is installed from.
     *
     * @param name the package name, scoped or not
     * @return the registry's base URL, ending in a slash
     */
    String registry(String name) {
        Optional<String> scoped = name.startsWith("@") && name.contains(SLASH)
            ? Optional.ofNullable(this.config.get(name.substring(0, name.indexOf('/')) + ':' + REGISTRY))
            : Optional.empty();
        String base = scoped.or(() -> Optional.ofNullable(this.config.get(REGISTRY))).orElse(PUBLIC);
        return base.endsWith(SLASH) ? base : base + SLASH;
    }

    /**
     * The token the configuration names for a registry, which only an HTTPS registry receives.
     *
     * @param registry the registry's URL
     * @return the token, or nothing when none applies
     */
    Optional<String> token(String registry) {
        URI url = URI.create(registry);
        if (!HTTPS.equals(url.getScheme())) {
            return Optional.empty();
        }
        String host = (url.getPort() < 0 ? url.getHost() : url.getHost() + ':' + url.getPort()).toLowerCase(
            Locale.ROOT
        );
        List<String> segments = segments(Optional.ofNullable(url.getPath()).orElse(""));
        return this.config.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("//") && entry.getKey().endsWith(TOKEN))
            .map(entry -> Map.entry(entry.getKey().substring(2, entry.getKey().length() - TOKEN.length()), entry))
            .filter(scope -> covers(scope.getKey(), host, segments))
            .max(Comparator.comparingInt(scope -> segments(scope.getKey()).size()))
            .map(scope -> scope.getValue().getValue());
    }

    /**
     * Every version a package's listing names, read from its registry with its token.
     *
     * @param name the package name, scoped or not
     * @return the versions
     * @throws IllegalStateException when the listing cannot be read, naming the registry
     */
    List<String> versions(String name) {
        String registry = this.registry(name);
        JsonNode versions = this.fetch(registry + name.replace(SLASH, "%2f"), this.token(registry)).path("versions");
        if (!versions.isObject()) {
            throw new IllegalStateException(redacted(registry) + " lists no versions of " + name);
        }
        return versions.propertyStream().map(Map.Entry::getKey).toList();
    }

    /**
     * Every long-term-support release the Node release index names, without its leading {@code v}.
     *
     * @param address the index's address
     * @return the long-term-support versions
     * @throws IllegalStateException when the index cannot be read
     */
    List<String> nodeLongTermReleases(String address) {
        JsonNode releases = this.fetch(address, Optional.empty());
        if (!releases.isArray()) {
            throw new IllegalStateException(redacted(address) + " lists no releases");
        }
        return releases.valueStream().filter(release -> release.path("lts").isTextual())
            .map(release -> release.path("version").asText())
            .map(version -> version.startsWith("v") ? version.substring(1) : version).toList();
    }

    /**
     * An address as a finding may print it: scheme, host, port and path, never a user name, password or query,
     * where a registry address written in the npm configuration can carry a credential.
     *
     * @param address the address
     * @return the address without user information and query
     */
    static String redacted(String address) {
        URI parsed = URI.create(address);
        String port = parsed.getPort() < 0 ? "" : ":" + parsed.getPort();
        String path = Optional.ofNullable(parsed.getRawPath()).orElse("");
        return parsed.getScheme() + "://" + parsed.getHost() + port + path;
    }

    private static boolean covers(String scope, String host, List<String> segments) {
        int slash = scope.indexOf('/');
        String scopeHost = (slash < 0 ? scope : scope.substring(0, slash)).toLowerCase(Locale.ROOT);
        List<String> scopeSegments = segments(slash < 0 ? "" : scope.substring(slash));
        return !scopeHost.isEmpty() && scopeHost.equals(host) && scopeSegments.size() <= segments.size()
            && scopeSegments.equals(segments.subList(0, scopeSegments.size()));
    }

    private static List<String> segments(String path) {
        return Arrays.stream(path.split(SLASH, -1)).filter(segment -> !segment.isEmpty()).toList();
    }

    private static Stream<Map.Entry<String, String>> read(Path file, UnaryOperator<String> expand) {
        if (!Files.isRegularFile(file)) {
            return Stream.empty();
        }
        try {
            return Files.readAllLines(file).stream().map(String::strip)
                .filter(line -> !line.startsWith("#") && !line.startsWith(";"))
                .filter(line -> line.indexOf('=') > 0)
                .map(
                    line -> Map.entry(
                        line.substring(0, line.indexOf('=')).strip(),
                        expand.apply(line.substring(line.indexOf('=') + 1).strip())
                    )
                );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    // A refusal names the credential the registry wants, and any other answer but a success names its status.
    private static void accepted(String address, int status) {
        if (status == OK) {
            return;
        }
        String answered = redacted(address) + " answered " + status;
        throw new IllegalStateException(
            status == UNAUTHORIZED || status == FORBIDDEN
                ? answered + ": the registry needs a credential the npm configuration does not supply for it (a"
                    + " //host/path/:_authToken line in .npmrc)"
                : answered
        );
    }

    private JsonNode fetch(String address, Optional<String> token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(address)).timeout(TIMEOUT)
            .header("Accept", "application/vnd.npm.install-v1+json, application/json");
        token.ifPresent(value -> builder.header("Authorization", "Bearer " + value));
        try {
            HttpResponse<String> response = this.client.send(
                builder.GET().build(), HttpResponse.BodyHandlers.ofString()
            );
            accepted(address, response.statusCode());
            return JSON.readTree(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException(redacted(address) + " could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading " + redacted(address), exception);
        }
    }
}
