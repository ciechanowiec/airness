package eu.ciechanowiec.airness.spring;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.ModelAndViewMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitterReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBodyReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.ViewMethodReturnValueHandler;

/**
 * Qualifies dispatch through Spring's streaming implementation without calling application extensions.
 */
@UtilityClass
final class SpringStreamingReturns {

    private static final Set<Class<?>> FRAMEWORK = Set.of(
        ModelAndViewMethodReturnValueHandler.class, ModelMethodProcessor.class,
        ViewMethodReturnValueHandler.class, ResponseBodyEmitterReturnValueHandler.class,
        StreamingResponseBodyReturnValueHandler.class
    );

    static Optional<SpringStreamingResult> inspect(RequestMappingHandlerAdapter adapter, List<HandlerMethod> handlers) {
        List<HandlerMethodReturnValueHandler> values = Optional.ofNullable(adapter.getReturnValueHandlers()).orElse(
            List.of()
        );
        boolean supported = handlers.stream().allMatch(handler -> standard(values, handler));
        return supported ? Optional.empty() : Optional.of(
            SpringStreamingResult.unsupported(
                "MVC return-value dispatch is not the standard streaming implementation"
            )
        );
    }

    private static boolean standard(List<HandlerMethodReturnValueHandler> values, HandlerMethod method) {
        return values.stream().filter(
            handler -> !framework(handler) || handler.supportsReturnType(method.getReturnType())
        )
            .findFirst().filter(handler -> handler.getClass() == StreamingResponseBodyReturnValueHandler.class)
            .isPresent();
    }

    private static boolean framework(HandlerMethodReturnValueHandler handler) {
        boolean passive = handler.getClass() != ResponseBodyEmitterReturnValueHandler.class || standardRegistry(
            handler
        );
        return FRAMEWORK.contains(handler.getClass()) && passive;
    }

    private static boolean standardRegistry(HandlerMethodReturnValueHandler handler) {
        return Optional.ofNullable(new DirectFieldAccessor(handler).getPropertyValue("reactiveHandler.adapterRegistry"))
            .map(Object::getClass).filter(ReactiveAdapterRegistry.class::equals).isPresent();
    }
}
