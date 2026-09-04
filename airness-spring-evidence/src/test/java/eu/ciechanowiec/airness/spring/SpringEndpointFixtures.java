package eu.ciechanowiec.airness.spring;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The applications the open-endpoint evidence is read out of.
 *
 * <p>Each is a real handler mapping and a real filter chain in one context, because the question the
 * evidence answers is what the two decide together and neither half proves anything alone.
 */
@UtilityClass
final class SpringEndpointFixtures {

    /**
     * The package every fixture handler is declared under, which an application would name as its root.
     */
    static final String ROOT = "eu.ciechanowiec.airness.spring";

    /**
     * A ready web application holding one chain and the handlers of one type.
     *
     * @param proxy     the chain the application built
     * @param endpoints the type declaring its handlers
     * @return the refreshed context
     */
    static GenericWebApplicationContext context(FilterChainProxy proxy, Class<?> endpoints) {
        GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
        context.registerBean("mapping", RequestMappingHandlerMapping.class);
        context.registerBean("springSecurityFilterChain", FilterChainProxy.class, () -> proxy);
        context.registerBean(endpoints);
        context.refresh();
        return context;
    }

    /**
     * A chain answering only for the named pattern and deciding nothing about anything else.
     *
     * @param pattern the pattern it answers for
     * @return the chain
     */
    static FilterChainProxy covering(String pattern) {
        return proxy(
            RequestMatcherDelegatingAuthorizationManager.builder()
                .add(PathPatternRequestMatcher.withDefaults().matcher(pattern), denying())
                .build()
        );
    }

    /**
     * A chain answering for every request by requiring something of the caller.
     *
     * @return the chain
     */
    static FilterChainProxy deciding() {
        return proxy(
            RequestMatcherDelegatingAuthorizationManager.builder()
                .add(AnyRequestMatcher.INSTANCE, denying())
                .build()
        );
    }

    /**
     * A chain admitting everything under the pattern without asking anything of the caller.
     *
     * @param pattern the pattern it admits
     * @return the chain
     */
    static FilterChainProxy admitting(String pattern) {
        return proxy(
            RequestMatcherDelegatingAuthorizationManager.builder()
                .add(PathPatternRequestMatcher.withDefaults().matcher(pattern), granting())
                .build()
        );
    }

    /**
     * A chain built around one manager.
     *
     * @param manager the manager its authorization filter asks
     * @return the chain
     */
    static FilterChainProxy proxy(AuthorizationManager<HttpServletRequest> manager) {
        return new FilterChainProxy(
            new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE, new AuthorizationFilter(manager))
        );
    }

    /**
     * A manager that reaches no decision at all.
     *
     * @return the manager
     */
    static AuthorizationManager<HttpServletRequest> abstaining() {
        return (_, _) -> null;
    }

    private static AuthorizationManager<RequestAuthorizationContext> denying() {
        return (_, _) -> new AuthorizationDecision(false);
    }

    private static AuthorizationManager<RequestAuthorizationContext> granting() {
        return (_, _) -> new AuthorizationDecision(true);
    }

    /**
     * Three ordinary handlers of an application.
     */
    @RestController
    static final class Endpoints {

        @GetMapping("/named/{name}")
        String named(String name) {
            return name;
        }

        @PostMapping("/named")
        String created() {
            return "created";
        }

        @GetMapping("/uncovered")
        String uncovered() {
            return "uncovered";
        }
    }

    /**
     * A handler whose own annotation takes the decision off the chain.
     */
    @RestController
    static final class Guarded {

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/guarded")
        String guarded() {
            return "guarded";
        }
    }

    /**
     * A type whose annotation guards every handler it declares.
     */
    @RestController
    @PreAuthorize("hasRole('ADMIN')")
    static final class GuardedType {

        @GetMapping("/typed")
        String typed() {
            return "typed";
        }
    }

    /**
     * A handler restricting itself to no HTTP method.
     */
    @RestController
    static final class Any {

        @RequestMapping("/any")
        String any() {
            return "any";
        }
    }
}
