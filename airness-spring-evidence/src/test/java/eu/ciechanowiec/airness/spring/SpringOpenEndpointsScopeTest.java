package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

/**
 * Which handlers this evidence asks about at all. A guard written beside the handler moves the decision
 * off the chain, a handler the application did not declare belongs to the rules written about it, and a
 * context that is not active is not one that can be asked.
 */
class SpringOpenEndpointsScopeTest {

    private static GenericWebApplicationContext admitting(Class<?> endpoints) {
        return SpringEndpointFixtures.context(SpringEndpointFixtures.admitting("/**"), endpoints);
    }

    private static List<String> reached(GenericWebApplicationContext context) {
        return SpringOpenEndpoints.reached(context, Set.of(SpringEndpointFixtures.ROOT));
    }

    @Test
    void passesOverAHandlerAMethodSecurityAnnotationGuards() {
        try (GenericWebApplicationContext context = admitting(SpringEndpointFixtures.Guarded.class)) {
            assertTrue(
                reached(context).isEmpty(),
                "a guarded handler is decided after the chain, so the chain says nothing about it"
            );
        }
    }

    @Test
    void passesOverAHandlerWhoseTypeAMethodSecurityAnnotationGuards() {
        try (GenericWebApplicationContext context = admitting(SpringEndpointFixtures.GuardedType.class)) {
            assertTrue(
                reached(context).isEmpty(),
                "a guard written once on the type governs every handler the type declares"
            );
        }
    }

    @Test
    void passesOverAHandlerOutsideTheDeclaredPackageRoot() {
        try (GenericWebApplicationContext context = admitting(SpringEndpointFixtures.Endpoints.class)) {
            assertTrue(
                SpringOpenEndpoints.reached(context, Set.of("com.example")).isEmpty(),
                "a mapping the application did not declare belongs to the rules written about it"
            );
        }
    }

    @Test
    void readsAMappingThatRestrictsItselfToNoMethod() {
        try (GenericWebApplicationContext context = admitting(SpringEndpointFixtures.Any.class)) {
            assertEquals(
                List.of("open GET /any"), reached(context),
                "a mapping open to every method is reached by the one a caller would try first"
            );
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
