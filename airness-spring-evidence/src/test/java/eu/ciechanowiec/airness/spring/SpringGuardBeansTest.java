package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * What a ready application answers about the beans its guards name.
 *
 * <p>No chain and no mapping are built here. The question is not what a request reaches but whether an
 * expression could be evaluated at all, which the bean factory alone settles, so each fixture is one
 * context holding the bean a guard delegates to and the type that guards.
 */
class SpringGuardBeansTest {

    private static final String CLEARANCE = "clearance";

    private static GenericApplicationContext delegating(Class<?> guarded) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(CLEARANCE, Clearance.class);
        context.registerBean(guarded);
        context.refresh();
        return context;
    }

    private static List<String> unresolved(GenericApplicationContext context) {
        return SpringGuardBeans.unresolved(context, Set.of(SpringEndpointFixtures.ROOT));
    }

    @Test
    void readsNothingOutOfAContextThatIsNotActive() {
        try (GenericApplicationContext context = delegating(Misdirected.class)) {
            context.close();
            assertTrue(
                unresolved(context).isEmpty(),
                "a context that is not active is not a context anything can be asked of"
            );
        }
    }

    @Test
    void passesOverAGuardThatNamesNoBeanAtAll() {
        try (GenericApplicationContext context = delegating(Guarded.class)) {
            assertTrue(
                unresolved(context).isEmpty(),
                "a guard asking only for a role delegates to nothing and has nothing to resolve"
            );
        }
    }

    @Test
    void passesOverAGuardWhoseBeanAndCallBothResolve() {
        try (GenericApplicationContext context = delegating(Delegating.class)) {
            assertTrue(
                unresolved(context).isEmpty(),
                "an expression the container could evaluate is not a finding"
            );
        }
    }

    @Test
    void reportsAGuardNamingABeanTheApplicationDoesNotDeclare() {
        try (GenericApplicationContext context = delegating(Misdirected.class)) {
            assertEquals(1, unresolved(context).size());
        }
    }

    @Test
    void namesTheBeanTheGuardAskedForAndTheTypeThatAskedForIt() {
        try (GenericApplicationContext context = delegating(Misdirected.class)) {
            String reported = unresolved(context).getFirst();
            assertTrue(
                reported.contains("clearence") && reported.contains(Misdirected.class.getName()),
                "a reader is told which name was written and where to go and change it"
            );
        }
    }

    @Test
    void reportsAGuardCallingAMethodTheBeanItNamesDoesNotAnswerTo() {
        try (GenericApplicationContext context = delegating(Miscalled.class)) {
            String reported = unresolved(context).getFirst();
            assertTrue(
                reported.contains("grantd") && reported.contains("#reached"),
                "the method that was called and the method that guards are both named"
            );
        }
    }

    @Test
    void passesOverAReferenceThatCallsNothing() {
        try (GenericApplicationContext context = delegating(Reading.class)) {
            assertTrue(
                unresolved(context).isEmpty(),
                "a property is reached through a name the engine derives rather than the one written"
            );
        }
    }

    @Test
    void passesOverABeanDeclaredOutsideTheRootsTheApplicationNames() {
        try (GenericApplicationContext context = delegating(Misdirected.class)) {
            assertTrue(
                SpringGuardBeans.unresolved(context, Set.of("com.example")).isEmpty(),
                "a guard the application did not write is resolved against a context this one cannot see"
            );
        }
    }
}
