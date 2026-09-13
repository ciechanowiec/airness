package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Where an application draws the refusals it answers to a caller without an account. A chain that
 * refuses the error address refuses its own refusals, and answers a redirect in place of each.
 */
class SpringErrorDispatchTest {

    private static final String TYPES = "spring.security.filter.dispatcher-types";

    private static GenericWebApplicationContext application(FilterChainProxy proxy) {
        return SpringEndpointFixtures.context(proxy, Endpoints.class);
    }

    private static List<String> errorDispatch(GenericWebApplicationContext context) {
        return SpringOpenEndpoints.errorDispatch(context, List.of("open GET /public"));
    }

    @Test
    void asksNothingOfAChainThatAdmitsNobodyToAnyMapping() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.deciding())) {
            assertTrue(
                SpringOpenEndpoints.errorDispatch(context, List.of()).isEmpty(),
                "a chain this has put no request to is not one an assembled request may be put to"
            );
        }
    }

    @Test
    void reportsTheChainThatRefusesTheAddressItsOwnRefusalsAreDrawnAt() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.deciding())) {
            assertEquals(
                List.of("error-dispatch closed"), errorDispatch(context),
                "a chain answering for every request answers for the second dispatch of a refusal too"
            );
        }
    }

    @Test
    void acceptsTheChainThatAdmitsThatAddress() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.admitting())) {
            assertEquals(
                List.of("error-dispatch open"), errorDispatch(context),
                "a chain that admits the error address draws the refusal the application decided on"
            );
        }
    }

    @Test
    void passesOverAnApplicationWhoseChainNeverCoversThatDispatch() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.deciding())) {
            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of(TYPES, "async,request"))
            );
            assertEquals(
                List.of("error-dispatch unfiltered"), errorDispatch(context),
                "a deployment leaving that dispatch to the application has answered this the other way"
            );
        }
    }

    @Test
    void readsADispatchTypeTheDeploymentWroteAsAList() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.deciding())) {
            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                    "test", Map.of(TYPES + "[0]", "request", TYPES + "[1]", "error")
                )
            );
            assertEquals(
                List.of("error-dispatch closed"), errorDispatch(context),
                "a list and one comma-separated value name the same dispatches"
            );
        }
    }

    @Test
    void recordsNothingWhereTheContextBuildsNoSecurityChain() {
        try (
            GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext())
        ) {
            context.registerBean("mapping", RequestMappingHandlerMapping.class);
            context.refresh();
            assertTrue(
                errorDispatch(context).isEmpty(), "a chain that was never built is not a chain this can read"
            );
        }
    }

    @Test
    void recordsNothingForAContextThatIsNoLongerActive() {
        try (GenericWebApplicationContext context = application(SpringEndpointFixtures.deciding())) {
            context.close();
            assertTrue(errorDispatch(context).isEmpty(), "a closed context is not a context this can ask");
        }
    }
}
