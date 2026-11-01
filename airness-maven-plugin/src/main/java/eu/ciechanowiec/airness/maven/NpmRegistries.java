package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Where an npm package's releases are listed, and the releases listed there, read as pnpm reads them.
 *
 * <p>The configuration is the one {@code release-age} reads: the user's {@code .npmrc}
 * ({@code pnpm_config_userconfig}, else {@code npm_config_userconfig}, else {@code ~/.npmrc}), then the
 * project's, then the environment, a later source winning, with {@code ${NAME}} expanded from the environment.
 * The environment is read as pnpm 12 reads it, not as npm does: a setting comes from a {@code pnpm_config_}
 * variable, an underscore in its name read as a dash, and no longer from an {@code npm_config_} one. A
 * registry credential ({@code //host/:_authToken}) comes from either prefix, and a scope's registry from
 * neither. Redirects are followed by this class, never by the client, so a token reaches no other origin. A
 * package is asked of its scope's registry, else the configured one, else the public one, with the token of the
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

    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    // A release with no pre-release or build part, which is what a latest tag naming the stable line reads as.
    private static final Pattern PLAIN_RELEASE = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final String PNPM_PREFIX = "pnpm_config_";
    private static final String NPM_PREFIX = "npm_config_";
    private static final String CREDENTIAL = "//";
    private static final String USER_CONFIG = "userconfig";
    private static final String REGISTRY = "registry";
    private static final String TOKEN = ":_authToken";
    private static final String SLASH = "/";
    private static final String HTTPS = "https";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final ObjectMapper JSON = new ObjectMapper();
    // Redirects are followed by fetch itself, never by the client: Java's client sends every header it was given
    // to wherever a redirect points, so a registry, or anyone who can make it redirect, would receive the token
    // on another host.
    private static final HttpClient SHARED = clientBuilder().build();
    private static final int MOST_REDIRECTS = 5;

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
     * The settings of the client every build shares: the connection timeout, and redirects left to this class.
     *
     * @return a builder with those settings
     */
    static HttpClient.Builder clientBuilder() {
        return HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER);
    }

    /**
     * Reads the npm configuration a node project installs with.
     *
     * @param directory   the node project directory, whose {@code .npmrc} is read
     * @param environment the environment, which names the user configuration, adds {@code pnpm_config_}
     *                    settings and registry credentials, and expands {@code ${...}}
     * @return the registries
     */
    static NpmRegistries of(Path directory, Map<String, String> environment) {
        Path user = variable(environment, PNPM_PREFIX)
            .or(() -> variable(environment, NPM_PREFIX))
            .map(Path::of)
            .orElseGet(() -> Path.of(System.getProperty("user.home"), ".npmrc"));
        UnaryOperator<String> expand = value -> VARIABLE.matcher(value).replaceAll(
            match -> Matcher.quoteReplacement(environment.getOrDefault(match.group(1), ""))
        );
        Stream<Map.Entry<String, String>> credentials = variables(environment, NPM_PREFIX)
            .filter(entry -> entry.getKey().startsWith(CREDENTIAL));
        Stream<Map.Entry<String, String>> settings = variables(environment, PNPM_PREFIX)
            .filter(entry -> !entry.getKey().startsWith("@"))
            .map(entry -> Map.entry(settingKey(entry.getKey()), entry.getValue()));
        Map<String, String> merged = Stream.of(
            read(user, expand), read(directory.resolve(".npmrc"), expand), credentials, settings
        ).flatMap(stream -> stream)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (_, later) -> later));
        return new NpmRegistries(merged, SHARED);
    }

    // Every variable whose name starts with the prefix, in any case, with the prefix removed, in a stable order
    // so a later source wins the same way on every run.
    private static Stream<Map.Entry<String, String>> variables(Map<String, String> environment, String prefix) {
        return environment.entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).startsWith(prefix))
            .map(entry -> Map.entry(entry.getKey().substring(prefix.length()), entry.getValue()))
            .sorted(Map.Entry.comparingByKey());
    }

    private static Optional<String> variable(Map<String, String> environment, String prefix) {
        return variables(environment, prefix).filter(
            entry -> USER_CONFIG.equals(entry.getKey().toLowerCase(Locale.ROOT))
        ).map(Map.Entry::getValue).findFirst();
    }

    // A setting's name as an .npmrc writes it. A credential's key names a host and a path, which keep their case.
    private static String settingKey(String name) {
        return name.startsWith(CREDENTIAL) ? name : name.toLowerCase(Locale.ROOT).replace('_', '-');
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
        if (!HTTPS.equalsIgnoreCase(url.getScheme())) {
            return Optional.empty();
        }
        String host = (url.getPort() < 0 ? url.getHost() : url.getHost() + ':' + url.getPort()).toLowerCase(
            Locale.ROOT
        );
        List<String> segments = segments(Optional.ofNullable(url.getPath()).orElse(""));
        return this.config.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(CREDENTIAL) && entry.getKey().endsWith(TOKEN))
            .map(entry -> Map.entry(entry.getKey().substring(2, entry.getKey().length() - TOKEN.length()), entry))
            .filter(scope -> covers(scope.getKey(), host, segments))
            .max(Comparator.comparingInt(scope -> segments(scope.getKey()).size()))
            .map(scope -> scope.getValue().getValue());
    }

    /**
     * Every version a package's listing names that its publisher has released as the package, read from its
     * registry with its token.
     *
     * <p>The {@code latest} distribution tag is what the registry installs when no version is asked for, so it is
     * where a publisher says which line is the stable one. A release numbered above it, such as a new major published
     * under {@code next} while it settles, is one the publisher has not made the package yet, and it is left out. A
     * listing without that tag, or whose tag names no plain release, is read whole.
     *
     * @param name the package name, scoped or not
     * @return the versions
     * @throws IllegalStateException when the listing cannot be read, naming the registry
     */
    List<String> versions(String name) {
        String registry = this.registry(name);
        JsonNode listing = this.fetch(registry + name.replace(SLASH, "%2f"), this.token(registry));
        JsonNode versions = listing.path("versions");
        if (!versions.isObject()) {
            throw new IllegalStateException(redacted(registry) + " lists no versions of " + name);
        }
        List<String> named = versions.propertyStream().map(Map.Entry::getKey).toList();
        return released(named, listing.path("dist-tags").path("latest").asText(""));
    }

    /**
     * The versions a listing names, without those numbered above the release its {@code latest} tag names.
     *
     * @param versions every version the listing names
     * @param latest   the version the {@code latest} tag names, empty when there is none
     * @return the versions the publisher has released as the package
     */
    static List<String> released(List<String> versions, String latest) {
        if (!PLAIN_RELEASE.matcher(latest).matches()) {
            return versions;
        }
        ComparableVersion ceiling = new ComparableVersion(latest);
        return versions.stream().filter(version -> new ComparableVersion(version).compareTo(ceiling) <= 0).toList();
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
        boolean hostMatches = !scopeHost.isEmpty() && scopeHost.equals(host);
        return hostMatches && scopeSegments.size() <= segments.size()
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

    // Follows at most a few redirects itself, each hop by follow.
    private JsonNode fetch(String address, Optional<String> token) {
        Hop hop = new Hop(URI.create(address), token);
        for (int count = 0; count <= MOST_REDIRECTS; count++) {
            HttpResponse<String> response = this.send(hop.address(), hop.token());
            Optional<URI> next = RegistryRedirects.target(
                hop.address(), response.statusCode(), response.headers().firstValue("Location")
            );
            if (next.isEmpty()) {
                accepted(hop.address().toString(), response.statusCode());
                return parsed(hop.address(), response.body());
            }
            hop = this.follow(hop, next.orElseThrow());
        }
        throw new IllegalStateException(
            redacted(address) + " redirected more than " + MOST_REDIRECTS + " times; the last address was "
                + redacted(hop.address().toString())
        );
    }

    // A redirect to another scheme, host or port carries no token unless the configuration names one for the new
    // address under the same rules as the first (HTTPS only, exact host and port, whole leading path segments),
    // a request that carried none never gains one, and a redirect from HTTPS to plain HTTP is refused.
    private Hop follow(Hop hop, URI next) {
        if (!RegistryRedirects.permitted(hop.address(), next)) {
            throw new IllegalStateException(
                redacted(hop.address().toString()) + " redirected to " + redacted(next.toString())
                    + ", which is not HTTPS; the redirect is not followed"
            );
        }
        boolean keeps = RegistryRedirects.sameOrigin(hop.address(), next) || hop.token().isEmpty();
        return new Hop(next, keeps ? hop.token() : this.token(next.toString()));
    }

    private HttpResponse<String> send(URI address, Optional<String> token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(address).timeout(TIMEOUT)
            .header("Accept", "application/vnd.npm.install-v1+json, application/json");
        token.ifPresent(value -> builder.header("Authorization", "Bearer " + value));
        try {
            return this.client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new IllegalStateException(redacted(address.toString()) + " could not be read", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading " + redacted(address.toString()), exception);
        }
    }

    private static JsonNode parsed(URI address, String body) {
        try {
            return JSON.readTree(body);
        } catch (JsonMappingException exception) {
            throw new IllegalStateException(redacted(address.toString()) + " returned an invalid JSON tree", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(redacted(address.toString()) + " could not be read", exception);
        }
    }

    /**
     * One request of a chain of redirects: the address, and the token it carries.
     *
     * @param address the address
     * @param token   the token, or nothing
     */
    private record Hop(URI address, Optional<String> token) {
    }
}
