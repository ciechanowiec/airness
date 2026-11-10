package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.SingleResultAuthorizationManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.context.support.GenericWebApplicationContext;

/**
 * Which handlers this evidence asks about at all. A guard written beside the handler moves the decision
 * off the chain, a handler the application did not declare belongs to the rules written about it, and a
 * context that is not active is not one that can be asked.
 */
class SpringOpenEndpointsScopeTest {

    private static GenericWebApplicationContext admitting(Class<?> endpoints) {
        return SpringEndpointFixtures.context(SpringEndpointFixtures.admitting(), endpoints);
    }

    private static List<String> reached(GenericWebApplicationContext context) {
        return SpringOpenEndpoints.reached(context, Set.of(SpringEndpointFixtures.ROOT));
    }

    private static FilterChainProxy webhooks() {
        return SpringEndpointFixtures.proxy(
            RequestMatcherDelegatingAuthorizationManager.builder().add(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/webhooks/**"),
                SingleResultAuthorizationManager.permitAll()
            ).add(AnyRequestMatcher.INSTANCE, AuthenticatedAuthorizationManager.authenticated())
                .build()
        );
    }

    @Test
    void collectionFiltersDoNotExemptPublicEndpoints() {
        try (
            GenericWebApplicationContext context = SpringEndpointFixtures.context(
                SpringEndpointFixtures.proxy(
                    RequestMatcherDelegatingAuthorizationManager.builder().add(
                        PathPatternRequestMatcher.withDefaults().matcher("/records/**"),
                        SingleResultAuthorizationManager.permitAll()
                    ).add(AnyRequestMatcher.INSTANCE, AuthenticatedAuthorizationManager.authenticated())
                        .build()
                ), FilteredEndpoints.class
            )
        ) {
            assertEquals(List.of("open GET /records/post", "open POST /records/pre"), reached(context));
        }
    }

    @Test
    void passesOverAHandlerAMethodSecurityAnnotationGuards() {
        try (GenericWebApplicationContext context = admitting(Guarded.class)) {
            assertTrue(
                reached(context).isEmpty(),
                "a guarded handler is decided after the chain, so the chain says nothing about it"
            );
        }
    }

    @Test
    void passesOverAHandlerWhoseTypeAMethodSecurityAnnotationGuards() {
        try (GenericWebApplicationContext context = admitting(GuardedType.class)) {
            assertTrue(
                reached(context).isEmpty(),
                "a guard written once on the type governs every handler the type declares"
            );
        }
    }

    @Test
    void passesOverAHandlerOutsideTheDeclaredPackageRoot() {
        try (GenericWebApplicationContext context = admitting(Endpoints.class)) {
            assertTrue(
                SpringOpenEndpoints.reached(context, Set.of("com.example")).isEmpty(),
                "a mapping the application did not declare belongs to the rules written about it"
            );
        }
    }

    @Test
    void readsAMappingThatRestrictsItselfToNoMethod() {
        try (GenericWebApplicationContext context = admitting(Any.class)) {
            assertEquals(
                List.of("open GET /any"), reached(context),
                "a mapping open to every method is recorded once, under the one a caller would try first"
            );
        }
    }

    @Test
    void readsAMappingThatRestrictsItselfToNoMethodByEveryMethodACallerCanSend() {
        try (GenericWebApplicationContext context = SpringEndpointFixtures.context(webhooks(), Webhook.class)) {
            assertEquals(
                List.of("open POST /webhooks/payment"), reached(context),
                "a mapping restricted to no method is open by the one method the chain admits a stranger by"
            );
        }
    }

    @Test
    void passesOverAMappingThatRestrictsItselfToNoMethodTheChainAdmitsByNone() {
        try (
            GenericWebApplicationContext context = SpringEndpointFixtures.context(
                SpringEndpointFixtures.deciding(), Webhook.class
            )
        ) {
            assertTrue(reached(context).isEmpty(), "a mapping no method of which is admitted is not open");
        }
    }

    @Test
    void reportsNothingForAContextThatWasNeverRefreshed() {
        try (
            GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext())
        ) {
            assertTrue(reached(context).isEmpty(), "a context that is not active is not one this can ask");
        }
    }
}
