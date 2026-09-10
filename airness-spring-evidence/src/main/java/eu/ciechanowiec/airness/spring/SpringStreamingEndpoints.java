package eu.ciechanowiec.airness.spring;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@UtilityClass
final class SpringStreamingEndpoints {

    static List<HandlerMethod> registered(ConfigurableApplicationContext context, SpringStreamingInputs inputs) {
        return context.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
            .flatMap(mapping -> mapping.getHandlerMethods().values().stream())
            .filter(handler -> inputs.owns(handler.getMethod().getDeclaringClass()))
            .filter(SpringStreamingEndpoints::streaming).distinct().toList();
    }

    private static boolean streaming(HandlerMethod handler) {
        Class<?> returned = handler.getReturnType().getParameterType();
        boolean direct = StreamingResponseBody.class.isAssignableFrom(returned);
        boolean wrapped = ResponseEntity.class.isAssignableFrom(returned) && body(handler);
        return direct || wrapped;
    }

    private static boolean body(HandlerMethod handler) {
        return Optional.ofNullable(ResolvableType.forMethodParameter(handler.getReturnType()).getGeneric().resolve())
            .filter(StreamingResponseBody.class::isAssignableFrom).isPresent();
    }

    static String location(HandlerMethod handler) {
        return handler.getBeanType().getName() + '#' + handler.getMethod().getName();
    }
}
