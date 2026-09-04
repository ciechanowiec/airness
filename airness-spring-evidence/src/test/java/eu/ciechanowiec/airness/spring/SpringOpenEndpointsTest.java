package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * What the built chain decides about an anonymous caller is what this evidence records. A chain that
 * grants, and one that reaches no decision, both leave the request to continue into the application.
 */
class SpringOpenEndpointsTest {

    private static final List<String> EVERY_MAPPING = List.of(
        "open GET /named/{name}", "open GET /uncovered", "open POST /named"
    );

    private static GenericWebApplicationContext application(FilterChainProxy proxy) {
        return SpringEndpointFixtures.context(proxy, SpringEndpointFixtures.Endpoints.class);
    }

    private static List<String> reached(GenericWebApplicationContext context) {
        return SpringOpenEndpoints.reached(context, Set.of(SpringEndpointFixtures.ROOT));
    }

    @Test
    void reportsEveryMappingAWildcardMatcherAdmits() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.admitting("/**"))) {
            assertEquals(
                EVERY_MAPPING, reached(context),
                "every mapping a wildcard admits is reported by the pattern the container mapped"
            );
        }
    }

    @Test
    void passesOverAMappingTheChainRequiresAuthenticationFor() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.deciding())) {
            assertTrue(
                reached(context).isEmpty(), "a chain that answers for every mapping leaves none of them open"
            );
        }
    }

    @Test
    void passesOverAMappingNoMatcherOfTheChainCovers() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.covering("/named"))) {
            assertTrue(
                reached(context).isEmpty(),
                "this platform denies a request no matcher covers, so an uncovered mapping is not open"
            );
        }
    }

    @Test
    void reportsTheMappingAManagerDecidesNothingAbout() {
        FilterChainProxy proxy = SpringEndpointFixtures.proxy(SpringEndpointFixtures.abstaining());
        try (GenericWebApplicationContext context = application(proxy)) {
            assertEquals(
                EVERY_MAPPING, reached(context),
                "a manager that reaches no decision leaves the request to continue into the application"
            );
        }
    }

    @Test
    void reportsTheMappingNoChainOfTheProxyCovers() {
        FilterChainProxy proxy = new FilterChainProxy(
            new DefaultSecurityFilterChain(
                PathPatternRequestMatcher.withDefaults().matcher("/elsewhere"),
                new AuthorizationFilter(SpringEndpointFixtures.abstaining())
            )
        );
        try (GenericWebApplicationContext context = application(proxy)) {
            assertEquals(EVERY_MAPPING, reached(context), "a request no chain covers never reaches security");
        }
    }

    @Test
    void reportsTheMappingACoveringChainInstallsNoAuthorizationFilterFor() {
        FilterChainProxy proxy = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE));
        try (GenericWebApplicationContext context = application(proxy)) {
            assertEquals(
                EVERY_MAPPING, reached(context),
                "an emptied chain decides nothing, which is what ignoring a path leaves behind"
            );
        }
    }

    @Test
    void reportsNothingWhereTheContextBuildsNoSecurityChain() {
        try (
            GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext())
        ) {
            context.registerBean("mapping", RequestMappingHandlerMapping.class);
            context.registerBean(SpringEndpointFixtures.Endpoints.class);
            context.refresh();
            assertTrue(
                reached(context).isEmpty(), "a chain that was never built is not a chain this can read"
            );
        }
    }
}
