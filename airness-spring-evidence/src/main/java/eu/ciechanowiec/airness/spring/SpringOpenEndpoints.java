package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * The mappings of a ready application that an unauthenticated caller is allowed to reach.
 *
 * <p>Which paths an application maps and what its filter chain decides about them are settled by the
 * container, out of a mapping written in one file and a matcher written in another, joined by a path
 * the source of neither states in full. Reading either half alone answers nothing, which is why this
 * is asked of the built context rather than of the text that produced it.
 *
 * <p>The question put to the container is the one the authorization filter asks itself: given an
 * anonymous caller and this request, what does the manager decide. A manager that grants, and a
 * manager that decides nothing because no matcher of the chain covers the path, both leave the
 * request to continue into the application, so both are read here as reached. Nothing else of the
 * chain runs. No filter of the project's own is invoked, no session is created, no statement is
 * issued, and the handler behind the mapping is never called, so recording this evidence cannot
 * change what the test around it observes.
 *
 * <p>A handler carrying a method-security annotation is passed over, because the decision that
 * governs it is taken after the filter chain and is not the decision read here. Nothing else makes
 * that annotation trustworthy: it is the reactor rule requiring the matching {@code @EnableX} that
 * keeps it from being an inert word.
 *
 * <p>Only handlers the application itself declares are read. Mappings contributed by the framework,
 * the error controller and the actuator endpoints among them, belong to the rules already written
 * about them.
 *
 * <p>A context that is not active is not a context this can ask, and nothing is recorded for one.
 * That silence cannot stand in for an answer: the rule reading this evidence is written beside the
 * one requiring a ready run of the production application, so a build in which nothing was asked is
 * already a build that failed. Anything else that goes wrong here is left to propagate, because a
 * harness that quietly reports an unexamined application as a clean one is worse than a loud one.
 */
@UtilityClass
final class SpringOpenEndpoints {

    /**
     * The annotations that move the decision off the filter chain, matched by simple name so that a
     * package the framework moves them to does not quietly empty this set.
     */
    private static final Set<String> GUARDS = Set.of(
        "PreAuthorize", "PostAuthorize", "PreFilter", "PostFilter", "Secured", "RolesAllowed", "DenyAll"
    );

    private static final String ANONYMOUS = "anonymous";
    private static final String SEGMENT = "1";

    /**
     * Every mapping of the application that an anonymous request is allowed to reach.
     *
     * @param context the context Spring Boot made ready
     * @param roots   the package roots the application declares, which its own handlers sit under
     * @return one {@code open <METHOD> <pattern>} line per reached mapping, sorted, and nothing when
     *         the context builds no security chain or maps nothing
     */
    static List<String> reached(ConfigurableApplicationContext context, Collection<String> roots) {
        return context.isActive()
            ? context.getBeanProvider(FilterChainProxy.class)
                .stream()
                .findFirst()
                .map(proxy -> admitted(context, roots, proxy))
                .orElseGet(List::of)
            : List.of();
    }

    /**
     * Every mapping the built chain admits, once that chain is known to exist.
     *
     * @param context the ready context
     * @param roots   the package roots the application declares
     * @param proxy   the built security chain of the application
     * @return the evidence lines
     */
    private static List<String> admitted(
        ConfigurableApplicationContext context, Collection<String> roots, FilterChainProxy proxy
    ) {
        return context.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
            .map(RequestMappingHandlerMapping::getHandlerMethods)
            .flatMap(handlers -> handlers.entrySet().stream())
            .filter(handler -> owned(handler.getValue(), roots))
            .filter(handler -> !guarded(handler.getValue()))
            .flatMap(handler -> probes(handler.getKey()))
            .distinct()
            .filter(probe -> open(proxy, probe))
            .map(Probe::line)
            .sorted()
            .toList();
    }

    /**
     * Whether the handler was declared by the application rather than contributed to it.
     *
     * @param handler the mapped handler
     * @param roots   the package roots the application declares
     * @return whether the declaring type sits under one of those roots
     */
    private static boolean owned(HandlerMethod handler, Collection<String> roots) {
        String declaring = handler.getBeanType().getName();
        return roots.stream().anyMatch(root -> declaring.startsWith(root + '.'));
    }

    /**
     * Whether a method-security annotation takes the decision off the filter chain.
     *
     * @param handler the mapped handler
     * @return whether the method or the type declaring it carries one
     */
    private static boolean guarded(HandlerMethod handler) {
        return named(handler.getMethod()) || named(handler.getBeanType());
    }

    private static boolean named(AnnotatedElement element) {
        return Stream.of(element.getAnnotations())
            .map(Annotation::annotationType)
            .map(Class::getSimpleName)
            .anyMatch(GUARDS::contains);
    }

    /**
     * One probe per method and pattern the mapping declares.
     *
     * @param mapping the mapping the container registered
     * @return the probes, a mapping restricted to no method being read as the one method a caller
     *         reaches an unguarded endpoint by
     */
    private static Stream<Probe> probes(RequestMappingInfo mapping) {
        Set<String> patterns = mapping.getPatternValues();
        List<String> methods = mapping.getMethodsCondition().getMethods().isEmpty()
            ? List.of("GET")
            : mapping.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
        return patterns.stream().flatMap(pattern -> methods.stream().map(method -> new Probe(method, pattern)));
    }

    /**
     * What the chain that covers this request decides about an anonymous caller.
     *
     * @param proxy the built security chain of the application
     * @param probe the method and pattern to ask about
     * @return whether the request is left to continue into the application
     */
    private static boolean open(FilterChainProxy proxy, Probe probe) {
        MockHttpServletRequest request = new MockHttpServletRequest(probe.method(), concrete(probe.pattern()));
        ServletRequestPathUtils.parseAndCache(request);
        try {
            return authorization(proxy, request).map(manager -> granted(manager, request)).orElse(Boolean.TRUE);
        } finally {
            ServletRequestPathUtils.clearParsedRequestPath(request);
        }
    }

    /**
     * What one manager decides about an anonymous caller.
     *
     * @param manager the manager the covering chain installed
     * @param request the request to decide about
     * @return whether the request is left to continue, a manager reaching no decision leaving it to
     */
    private static boolean granted(AuthorizationManager<HttpServletRequest> manager, HttpServletRequest request) {
        return Optional.ofNullable(manager.authorize(SpringOpenEndpoints::anonymous, request))
            .map(AuthorizationResult::isGranted)
            .orElse(Boolean.TRUE);
    }

    /**
     * The authorization manager of the first chain that covers the request.
     *
     * @param proxy   the built security chain of the application
     * @param request the request to cover
     * @return the manager, and nothing when no chain covers the request or the covering chain
     *         installs no authorization filter, both of which leave the request unexamined
     */
    private static Optional<AuthorizationManager<HttpServletRequest>> authorization(
        FilterChainProxy proxy, HttpServletRequest request
    ) {
        return proxy.getFilterChains().stream()
            .filter(chain -> chain.matches(request))
            .findFirst()
            .map(SecurityFilterChain::getFilters)
            .stream()
            .flatMap(List::stream)
            .filter(AuthorizationFilter.class::isInstance)
            .map(AuthorizationFilter.class::cast)
            .map(AuthorizationFilter::getAuthorizationManager)
            .findFirst();
    }

    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken(
            "airness", ANONYMOUS, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
    }

    /**
     * A pattern rewritten into a path a request can carry.
     *
     * @param pattern the pattern the mapping declares
     * @return the pattern with every variable and wildcard replaced by one ordinary segment
     */
    private static String concrete(String pattern) {
        String path = pattern.replaceAll("\\{[^/{}]*}", SEGMENT).replaceAll("\\*+", SEGMENT);
        return path.startsWith("/") ? path : '/' + path;
    }

    /**
     * One method and pattern of the application, and the evidence line recording that it is reached.
     *
     * @param method  the HTTP method a caller arrives by
     * @param pattern the pattern the mapping declares, kept as written so that the rule reading this
     *                evidence compares it with the matcher a project wrote rather than with a path it did not
     */
    private record Probe(String method, String pattern) {

        private String line() {
            return "open " + this.method() + ' ' + this.pattern();
        }
    }
}
