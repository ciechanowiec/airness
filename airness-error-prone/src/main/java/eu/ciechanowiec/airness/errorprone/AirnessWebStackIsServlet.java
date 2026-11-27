package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.IdentifierTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MemberSelectTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Serves, calls and persists through the blocking servlet stack rather than its reactive counterpart.
 *
 * <p>Every reactive API below has a blocking twin that a virtual thread runs at the same concurrency, with the
 * stack traces, the transactions and the thread-bound context the reactive one gives up. Reported is a
 * reference to a type of Spring WebFlux and its server, security and test packages, {@code WebClient} and the
 * reactive HTTP connectors among them, of Spring Boot's reactive web, WebClient and WebTestClient support, of
 * R2DBC, of Spring Data's reactive repositories and Redis templates, and of RSocket. Of the package
 * {@code org.springframework.web.server} only the exchange, filter, handler and session types are reported,
 * because Spring MVC throws that package's {@code ResponseStatusException}. An import is not reported, the
 * reference it serves is. The check admits no suppression.
 */
@BugPattern(
    summary = "Use the servlet stack: Spring MVC, RestClient, MockMvcTester or RestTestClient, and JDBC, in place"
        + " of WebFlux, WebClient, WebTestClient, R2DBC and RSocket",
    severity = BugPattern.SeverityLevel.ERROR,
    suppressionAnnotations = {}
)
public final class AirnessWebStackIsServlet extends BugChecker
                                            implements IdentifierTreeMatcher, MemberSelectTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<String> REACTIVE_PACKAGES = List.of(
        "org.springframework.web.reactive", "org.springframework.http.server.reactive",
        "org.springframework.http.client.reactive", "org.springframework.security.config.web.server",
        "org.springframework.security.web.server", "org.springframework.security.config.annotation.web.reactive",
        "org.springframework.test.web.reactive.server", "org.springframework.boot.webflux",
        "org.springframework.boot.webclient", "org.springframework.boot.webtestclient",
        "org.springframework.boot.web.reactive", "org.springframework.boot.autoconfigure.web.reactive",
        "org.springframework.boot.test.autoconfigure.web.reactive", "org.springframework.r2dbc",
        "org.springframework.data.r2dbc", "org.springframework.boot.r2dbc", "org.springframework.boot.data.r2dbc",
        "org.springframework.boot.autoconfigure.r2dbc", "org.springframework.boot.test.autoconfigure.data.r2dbc",
        "io.r2dbc", "org.springframework.data.repository.reactive", "org.springframework.messaging.rsocket",
        "org.springframework.boot.rsocket", "io.rsocket"
    );

    private static final Set<String> REACTIVE_TYPES = Set.of(
        "org.springframework.web.server.ServerWebExchange", "org.springframework.web.server.WebFilter",
        "org.springframework.web.server.WebFilterChain", "org.springframework.web.server.WebHandler",
        "org.springframework.web.server.WebSession", "org.springframework.web.server.WebExceptionHandler",
        "org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity",
        "org.springframework.data.redis.core.ReactiveRedisTemplate",
        "org.springframework.data.redis.core.ReactiveStringRedisTemplate"
    );

    @Override
    public Description matchIdentifier(IdentifierTree identifier, VisitorState state) {
        return this.judge(identifier, state);
    }

    @Override
    public Description matchMemberSelect(MemberSelectTree select, VisitorState state) {
        return this.judge(select, state);
    }

    private Description judge(Tree reference, VisitorState state) {
        boolean imported = Optional.ofNullable(state.findEnclosing(ImportTree.class)).isPresent();
        boolean reactive = !imported && Optional.ofNullable(ASTHelpers.getSymbol(reference))
            .filter(symbol -> symbol.getKind().isClass() || symbol.getKind().isInterface())
            .filter(AirnessWebStackIsServlet::reactive)
            .isPresent();
        return reactive ? this.describeMatch(reference) : Description.NO_MATCH;
    }

    private static boolean reactive(Symbol symbol) {
        return REACTIVE_TYPES.contains(symbol.getQualifiedName().toString())
            || KnownTypes.declaredIn(symbol, REACTIVE_PACKAGES);
    }
}
