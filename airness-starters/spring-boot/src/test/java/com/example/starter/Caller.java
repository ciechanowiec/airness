package com.example.starter;

import com.example.starter.web.Htmx;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A caller that reaches the running application over real HTTP and signs in the way a browser does. The
 * cookies of one caller are its own, so two callers of one test are two readers.
 *
 * <p>The token posted with a form is read out of a page the application drew, rather than arranged
 * around: a caller that could post without one would prove nothing about a chain that demands it.
 */
public final class Caller {

    /**
     * The address the first administrator of a test run signs in with, which application-test.yaml writes.
     */
    public static final String ADMINISTRATOR = "administrator@starter.test";

    /**
     * The password that administrator signs in with. It reaches only a server a test started.
     */
    public static final String SECRET = "starter-" + "fixture-not-a-secret";

    private static final String LOGIN = "/login";

    // A multipart body separates its lines with a carriage return and a line feed whatever the platform.
    private static final String CRLF = "\r\n";

    // Every page carries the language switch, which is a form, so every page carries a token.
    private static final String TOKENED = "/";

    private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final int port;

    private final CookieManager cookies;

    /**
     * Reaches the application on the given port, as somebody who has not signed in.
     *
     * @param port the port the application listens on
     */
    public Caller(int port) {
        this.port = port;
        this.cookies = new CookieManager();
    }

    /**
     * Answers a caller signed in under the given account.
     *
     * @param port   the port the application listens on
     * @param email  the address to sign in under
     * @param secret the password to sign in with
     * @return that caller, with the session the sign-in opened
     */
    public static Caller signedIn(int port, String email, String secret) {
        Caller caller = new Caller(port);
        caller.signIn(email, secret);
        return caller;
    }

    /**
     * Answers a caller signed in as the first administrator of the test run.
     *
     * @param port the port the application listens on
     * @return that caller
     */
    public static Caller administrator(int port) {
        return signedIn(port, ADMINISTRATOR, SECRET);
    }

    /**
     * Answers a second caller holding the cookies this one holds now, as whoever copied them would, and
     * keeping them whatever this caller does afterwards.
     *
     * @return that caller
     */
    public Caller copied() {
        Caller copy = new Caller(this.port);
        URI application = URI.create("http://localhost:%d/".formatted(this.port));
        CookieStore kept = copy.cookies.getCookieStore();
        this.cookies.getCookieStore().getCookies().forEach(cookie -> kept.add(application, cookie));
        return copy;
    }

    /**
     * Says who is calling, the way the filter chain says it for a call that arrived over HTTP, for a test
     * that calls an application layer directly.
     *
     * @param email     the address the caller signs in with
     * @param authority the authority the caller holds
     */
    public static void acting(String email, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(email, "", List.of(new SimpleGrantedAuthority(authority)))
        );
    }

    /**
     * Signs in, by reading the form the application draws and posting it back with its token.
     *
     * @param email  the address to sign in under
     * @param secret the password to sign in with
     * @return what the application answered, which says where it sends the reader
     */
    public HttpResponse<String> signIn(String email, String secret) {
        return this.posting(LOGIN, LOGIN, "username=%s&password=%s".formatted(encoded(email), encoded(secret)));
    }

    /**
     * Posts the given form with a token from a page this caller may read.
     *
     * @param path the address to post to
     * @param form the encoded body, without the token
     * @return what the application answered
     */
    public HttpResponse<String> post(String path, String form) {
        return this.posting(TOKENED, path, form);
    }

    /**
     * Posts the given form the way htmx does, with a token from a page this caller may read.
     *
     * @param path the address to post to
     * @param form the encoded body, without the token
     * @return what the application answered
     */
    public HttpResponse<String> partialPost(String path, String form) {
        String body = "%s&_csrf=%s".formatted(form, encoded(this.token(TOKENED)));
        return this.read(
            this.request(path)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(Htmx.HEADER, "true")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()
        );
    }

    /**
     * Posts the given form with no token at all, which the chain refuses.
     *
     * @param path the address to post to
     * @param form the encoded body
     * @return what the application answered
     */
    public HttpResponse<String> forged(String path, String form) {
        return this.read(
            this.request(path)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build()
        );
    }

    /**
     * Reads the page at the given address.
     *
     * @param path the address to read
     * @return what the application answered
     */
    public HttpResponse<String> get(String path) {
        return this.read(this.request(path).header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE).GET().build());
    }

    /**
     * Reads the page at the given address the way htmx asks for the part of it it replaces.
     *
     * @param path the address to read
     * @return what the application answered
     */
    public HttpResponse<String> partial(String path) {
        return this.read(this.request(path).header(Htmx.HEADER, "true").GET().build());
    }

    /**
     * Reads the page at the given address the way a link the page shell boosts navigates to it.
     *
     * @param path the address to read
     * @return what the application answered
     */
    public HttpResponse<String> boosted(String path) {
        return this.read(this.request(path).header(Htmx.HEADER, "true").header(Htmx.BOOSTED, "true").GET().build());
    }

    /**
     * Reads the page at the given address the way a browser set to the given language asks for it.
     *
     * @param path     the address to read
     * @param language the language the browser asks for, as an Accept-Language header carries it
     * @return what the application answered
     */
    public HttpResponse<String> reading(String path, String language) {
        return this.read(this.request(path).header(HttpHeaders.ACCEPT_LANGUAGE, language).GET().build());
    }

    /**
     * Posts the given files as a multipart form under the given field, with a token from a page this caller
     * may read, the way a browser sends a form that uploads files.
     *
     * @param path  the address to post to
     * @param field the name of the field the files are sent under
     * @param files each file's name and bytes, in the order they are sent
     * @return what the application answered
     */
    public HttpResponse<String> upload(String path, String field, Map<String, byte[]> files) {
        String boundary = "starter-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        part(
            body, boundary, "Content-Disposition: form-data; name=\"_csrf\"", this.token(TOKENED).getBytes(
                StandardCharsets.UTF_8
            )
        );
        files.forEach(
            (name, content) -> part(
                body, boundary,
                "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + name + "\"" + CRLF
                    + "Content-Type: application/octet-stream",
                content
            )
        );
        body.writeBytes(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
        return this.read(
            this.request(path)
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build()
        );
    }

    /**
     * Reads the bytes at the given address, such as a stored file.
     *
     * @param path the address to read
     * @return what the application answered
     */
    @SneakyThrows
    public HttpResponse<byte[]> bytes(String path) {
        try (HttpClient client = HttpClient.newBuilder().cookieHandler(this.cookies).build()) {
            return client.send(this.request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static void part(ByteArrayOutputStream body, String boundary, String headers, byte[] content) {
        body.writeBytes(("--" + boundary + CRLF + headers + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        body.writeBytes(content);
        body.writeBytes(CRLF.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> posting(String tokened, String path, String form) {
        String body = "%s&_csrf=%s".formatted(form, encoded(this.token(tokened)));
        return this.read(
            this.request(path)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()
        );
    }

    private String token(String path) {
        Matcher token = TOKEN.matcher(this.get(path).body());
        if (!token.find()) {
            throw new IllegalStateException("The page at %s carried no token to post back".formatted(path));
        }
        return token.group(1);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:%d%s".formatted(this.port, path)));
    }

    @SneakyThrows
    private HttpResponse<String> read(HttpRequest request) {
        try (HttpClient client = HttpClient.newBuilder().cookieHandler(this.cookies).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    /**
     * Answers the given value encoded for a form body or a query.
     *
     * @param value the value to encode
     * @return the encoded value
     */
    public static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
