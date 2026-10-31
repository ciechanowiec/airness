package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The route inventory is every address at which the application renders a page, read from the mappings
 * the container registered rather than from what a crawler happens to reach.
 */
class SpringHtmlRoutesTest {

    private static List<String> rendered(GenericWebApplicationContext context) {
        return SpringHtmlRoutes.rendered(context, Set.of(SpringEndpointFixtures.ROOT));
    }

    private static GenericWebApplicationContext application(Class<?>... handlers) {
        GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
        context.registerBean("mapping", RequestMappingHandlerMapping.class);
        for (Class<?> handler : handlers) {
            context.registerBean(handler);
        }
        context.registerBean("views", SimpleUrlHandlerMapping.class, SpringHtmlRoutesTest::views);
        context.refresh();
        return context;
    }

    private static GenericWebApplicationContext closed() {
        GenericWebApplicationContext context = application(Pages.class);
        context.close();
        return context;
    }

    private static SimpleUrlHandlerMapping views() {
        ParameterizableViewController gone = new ParameterizableViewController();
        gone.setStatusCode(HttpStatus.GONE);
        gone.setStatusOnly(true);
        ParameterizableViewController moved = new ParameterizableViewController();
        moved.setView(new RedirectView("/rooms"));
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(
            Map.of("/about", named("about"), "/gone", gone, "/moved", moved, "/old", named("redirect:/rooms"))
        );
        mapping.setRootHandler(named("index"));
        return mapping;
    }

    private static ParameterizableViewController named(String view) {
        ParameterizableViewController controller = new ParameterizableViewController();
        controller.setViewName(view);
        return controller;
    }

    @Test
    void listsEveryAddressAControllerRendersAViewAt() {
        try (GenericWebApplicationContext context = application(Pages.class)) {
            assertEquals(
                List.of(
                    "html-route\t/\tview-controller",
                    "html-route\t/about\tview-controller",
                    "html-route\t/any\tPages#any",
                    "html-route\t/rooms\tPages#room",
                    "html-route\t/rooms/{id}\tPages#room"
                ),
                rendered(context),
                "a page is a GET resolved to a view; bodies, redirects, status-only views and the error address are not"
            );
        }
    }

    @Test
    void passesOverAHandlerThatWritesItsAnswerAsABody() {
        try (GenericWebApplicationContext context = application(Endpoints.class)) {
            assertTrue(
                rendered(context).stream().noneMatch(line -> line.contains("/named") || line.contains("/uncovered")),
                "a REST controller answers JSON or text rather than a page"
            );
        }
    }

    @Test
    void readsNothingFromAContextThatIsNotActive() {
        assertTrue(rendered(closed()).isEmpty(), "a closed context is not a context this can ask");
    }
}
