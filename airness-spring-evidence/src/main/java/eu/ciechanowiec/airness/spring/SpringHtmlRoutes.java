package eu.ciechanowiec.airness.spring;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The addresses at which a ready application renders a page, which the browser sweep must account for.
 *
 * <p>A crawler finds what is linked from what it already reached, and nothing else. A page whose link
 * renders only for a signed-in visitor, or one linked from nowhere, is invisible to it. The container
 * knows every address it renders a view at, because it mapped each one, so the inventory the sweep is
 * reconciled with is read from the mappings rather than from the crawl that it is meant to check.
 *
 * <p>A mapping renders a page when it answers a GET, sits in a type the application declares, and is
 * resolved to a view rather than written as a body. A handler carrying {@link ResponseBody}, directly
 * or through a REST controller, writes a body. So does one returning an entity, a resource, a stream
 * or an emitter, and one that produces only media types a page is not. The view controllers the
 * configuration registers render pages too, and are read from the URL mappings that hold them, the
 * welcome page among them. A redirect, a status-only view controller and the error address render no
 * page of their own and are passed over.
 *
 * <p>What a handler renders is decided when it runs, so a handler that answers a page for one request
 * and a fragment for another is listed once, at its address. Which of its states the sweep reached is
 * the coverage question the sweep itself answers.
 */
@UtilityClass
final class SpringHtmlRoutes {

    static final String ERROR_ADDRESS = "/error";

    private static final String VIEW_CONTROLLER = "view-controller";

    private static final String REDIRECT = "redirect:";

    private static final Set<Class<?>> BODIES = Set.of(ResponseBodyEmitter.class, StreamingResponseBody.class);

    /**
     * Every address of the application that renders a page.
     *
     * @param context the context Spring Boot made ready
     * @param roots   the package roots the application declares, which its own handlers sit under
     * @return one {@code html-route<TAB>pattern<TAB>source} line per address, sorted and distinct, and
     *         nothing for a context that is not active or maps no page
     */
    static List<String> rendered(ConfigurableApplicationContext context, Collection<String> roots) {
        return context.isActive()
            ? Stream.concat(handlers(context, roots), viewControllers(context))
                .filter(line -> !line.startsWith(SpringPageMappings.PREFIX + ERROR_ADDRESS + '\t'))
                .distinct()
                .sorted()
                .toList()
            : List.of();
    }

    private static Stream<String> handlers(ConfigurableApplicationContext context, Collection<String> roots) {
        return context.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
            .map(RequestMappingHandlerMapping::getHandlerMethods)
            .flatMap(handlers -> handlers.entrySet().stream())
            .filter(handler -> SpringPageMappings.owned(handler.getValue(), roots))
            .filter(handler -> renders(handler.getKey(), handler.getValue()))
            .flatMap(
                handler -> handler.getKey().getPatternValues().stream()
                    .map(pattern -> SpringPageMappings.line(pattern, handler.getValue()))
            );
    }

    private static Stream<String> viewControllers(ConfigurableApplicationContext context) {
        return context.getBeansOfType(AbstractUrlHandlerMapping.class).values().stream()
            .flatMap(SpringHtmlRoutes::registered)
            .filter(entry -> entry.getValue() instanceof ParameterizableViewController controller && page(controller))
            .map(entry -> SpringPageMappings.line(entry.getKey(), VIEW_CONTROLLER));
    }

    // The root handler is held apart from the rest of the map, and it is where the welcome page sits.
    private static Stream<Map.Entry<String, Object>> registered(AbstractUrlHandlerMapping mapping) {
        Stream<Map.Entry<String, Object>> root = Optional.ofNullable(mapping.getRootHandler())
            .map(handler -> Map.entry("/", handler))
            .stream();
        return Stream.concat(root, mapping.getHandlerMap().entrySet().stream());
    }

    private static boolean page(ParameterizableViewController controller) {
        boolean redirecting = controller.getView() instanceof RedirectView
            || Optional.ofNullable(controller.getViewName()).filter(name -> name.startsWith(REDIRECT)).isPresent();
        return !controller.isStatusOnly() && !redirecting;
    }

    private static boolean renders(RequestMappingInfo mapping, HandlerMethod handler) {
        return SpringPageMappings.renders(
            mapping.getMethodsCondition().getMethods(), mapping.getProducesCondition().getProducibleMediaTypes(),
            handler, BODIES
        );
    }
}
