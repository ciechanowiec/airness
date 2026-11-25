package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

/**
 * The route inventory of a reactive application is every address at which it renders a page, read from the
 * mappings the container registered, as it is for a servlet application.
 */
class SpringReactiveHtmlRoutesTest {

    private static GenericApplicationContext application() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("mapping", RequestMappingHandlerMapping.class);
        context.registerBean(ReactivePages.class);
        context.refresh();
        return context;
    }

    @Test
    void listsEveryAddressAReactiveControllerRendersAViewAt() {
        try (GenericApplicationContext context = application()) {
            assertEquals(
                List.of("html-route\t/orders\tReactivePages#order", "html-route\t/orders/{id}\tReactivePages#order"),
                SpringReactiveHtmlRoutes.rendered(context, Set.of(SpringEndpointFixtures.ROOT)),
                "a page is a GET resolved to a view; bodies, other media types and the error address are not"
            );
        }
    }

    @Test
    void listsNothingForHandlersOutsideTheApplication() {
        try (GenericApplicationContext context = application()) {
            assertEquals(
                List.of(), SpringReactiveHtmlRoutes.rendered(context, Set.of("com.example.elsewhere")),
                "a handler outside the declared roots is no page of the application"
            );
        }
    }

    @Test
    void listsNothingForAContextThatIsNotActive() {
        assertEquals(
            List.of(), SpringReactiveHtmlRoutes.rendered(closed(), Set.of(SpringEndpointFixtures.ROOT)),
            "a context that is not active maps nothing"
        );
    }

    private static GenericApplicationContext closed() {
        GenericApplicationContext context = application();
        context.close();
        return context;
    }
}
