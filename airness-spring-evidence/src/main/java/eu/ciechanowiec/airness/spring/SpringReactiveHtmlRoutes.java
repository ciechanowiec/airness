package eu.ciechanowiec.airness.spring;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * The addresses at which a ready reactive application renders a page, read as {@link SpringHtmlRoutes}
 * reads them from a servlet application.
 *
 * <p>A mapping renders a page when it answers a GET, sits in a type the application declares, and is
 * resolved to a view rather than written as a body. A handler carrying {@link ResponseBody}, directly or
 * through a REST controller, writes a body, and so does one returning an entity or a resource, or one that
 * produces only media types a page is not. The reactive stack has no view controllers, and a router
 * function states nothing about what it answers, so neither adds an address here. The error address
 * renders no page of its own and is passed over.
 */
@UtilityClass
final class SpringReactiveHtmlRoutes {

    /**
     * Every address of the reactive application that renders a page.
     *
     * @param context the context Spring Boot made ready
     * @param roots   the package roots the application declares, which its own handlers sit under
     * @return one {@code html-route<TAB>pattern<TAB>source} line per address, sorted and distinct, and
     *         nothing for a context that is not active or maps no page
     */
    static List<String> rendered(ConfigurableApplicationContext context, Collection<String> roots) {
        return context.isActive()
            ? context.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
                .map(RequestMappingHandlerMapping::getHandlerMethods)
                .flatMap(handlers -> handlers.entrySet().stream())
                .filter(handler -> SpringPageMappings.owned(handler.getValue(), roots))
                .filter(handler -> renders(handler.getKey(), handler.getValue()))
                .flatMap(handler -> lines(handler.getKey(), handler.getValue()))
                .filter(line -> !line.startsWith(SpringPageMappings.PREFIX + SpringHtmlRoutes.ERROR_ADDRESS + '\t'))
                .distinct()
                .sorted()
                .toList()
            : List.of();
    }

    private static Stream<String> lines(RequestMappingInfo mapping, HandlerMethod handler) {
        return mapping.getPatternsCondition().getPatterns().stream()
            .map(PathPattern::getPatternString)
            .map(pattern -> SpringPageMappings.line(pattern, handler));
    }

    private static boolean renders(RequestMappingInfo mapping, HandlerMethod handler) {
        return SpringPageMappings.renders(
            mapping.getMethodsCondition().getMethods(), mapping.getProducesCondition().getProducibleMediaTypes(),
            handler, Set.of()
        );
    }
}
