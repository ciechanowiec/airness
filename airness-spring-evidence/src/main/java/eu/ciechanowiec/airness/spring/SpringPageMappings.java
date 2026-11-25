package eu.ciechanowiec.airness.spring;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;

/**
 * What decides whether a handler method renders a page, in the terms the servlet and the reactive stacks
 * share, so each route inventory reads its own mappings and judges their handlers alike. It loads no class
 * of either stack.
 */
@UtilityClass
final class SpringPageMappings {

    /**
     * The prefix of every route line.
     */
    static final String PREFIX = "html-route\t";

    private static final Set<Class<?>> BODIES = Set.of(HttpEntity.class, Resource.class);

    /**
     * Whether the application declares the handler, under one of its package roots.
     *
     * @param handler the handler
     * @param roots   the package roots the application declares
     * @return whether the handler is the application's own
     */
    static boolean owned(HandlerMethod handler, Collection<String> roots) {
        String declaring = handler.getBeanType().getName();
        return roots.stream().anyMatch(root -> declaring.startsWith(root + '.'));
    }

    /**
     * Whether a mapping renders a page: it answers a GET, its handler writes no body, and it produces a page.
     *
     * @param methods  the request methods the mapping answers, none for every method
     * @param produced the media types the mapping produces, none for any
     * @param handler  the handler
     * @param bodies   the return types that write a body on this stack, beside an entity and a resource
     * @return whether the mapping renders a page
     */
    static boolean renders(
        Set<RequestMethod> methods, Set<MediaType> produced, HandlerMethod handler, Set<Class<?>> bodies
    ) {
        boolean read = methods.isEmpty() || methods.contains(RequestMethod.GET);
        boolean page = produced.isEmpty() || produced.stream().anyMatch(MediaType.TEXT_HTML::isCompatibleWith);
        return read && !writesBody(handler, bodies) && page;
    }

    /**
     * The route line of an address.
     *
     * @param pattern the address pattern
     * @param handler the handler that answers it
     * @return the line
     */
    static String line(String pattern, HandlerMethod handler) {
        return line(pattern, handler.getBeanType().getSimpleName() + '#' + handler.getMethod().getName());
    }

    /**
     * The route line of an address.
     *
     * @param pattern the address pattern
     * @param source  what answers it
     * @return the line
     */
    static String line(String pattern, String source) {
        return PREFIX + pattern + '\t' + source;
    }

    private static boolean writesBody(HandlerMethod handler, Set<Class<?>> bodies) {
        Method method = handler.getMethod();
        Class<?> returned = method.getReturnType();
        return AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), ResponseBody.class)
            || AnnotatedElementUtils.hasAnnotation(method, ResponseBody.class)
            || BODIES.stream().anyMatch(body -> body.isAssignableFrom(returned))
            || bodies.stream().anyMatch(body -> body.isAssignableFrom(returned));
    }
}
