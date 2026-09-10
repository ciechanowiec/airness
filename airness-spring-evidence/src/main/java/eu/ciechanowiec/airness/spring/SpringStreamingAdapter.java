package eu.ciechanowiec.airness.spring;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.springframework.beans.BeansException;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

@UtilityClass
final class SpringStreamingAdapter {

    private static final Set<String> ADAPTERS = Set.of(
        RequestMappingHandlerAdapter.class.getName(),
        "org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter",
        "org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter",
        "org.springframework.web.servlet.function.support.HandlerFunctionAdapter"
    );
    private static final Set<String> FACTORIES = Set.of(
        "org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration$EnableWebMvcConfiguration"
    );

    static Optional<SpringStreamingResult> inspect(
        ConfigurableApplicationContext context, List<HandlerMethod> handlers
    ) {
        ConfigurableListableBeanFactory beans = context.getBeanFactory();
        List<String> adapters = List.of(beans.getBeanNamesForType(RequestMappingHandlerAdapter.class, true, false));
        boolean custom = Arrays.stream(beans.getBeanNamesForType(HandlerAdapter.class, true, false))
            .map(name -> Optional.ofNullable(beans.getType(name, false)).map(ClassUtils::getUserClass))
            .anyMatch(type -> type.isEmpty() || !ADAPTERS.contains(type.orElseThrow().getName()));
        if (custom || adapters.size() != 1) {
            return Optional.of(SpringStreamingResult.unsupported("expected one standard MVC request adapter"));
        }
        return standard(context, adapters.getFirst(), handlers);
    }

    private static Optional<SpringStreamingResult> standard(
        ConfigurableApplicationContext context,
        String name, List<HandlerMethod> handlers
    ) {
        if (!standardFactory(context.getBeanFactory(), name)) {
            return Optional.of(
                SpringStreamingResult.unsupported("MVC adapter is not provided by standard MVC configuration")
            );
        }
        RequestMappingHandlerAdapter adapter = context.getBean(name, RequestMappingHandlerAdapter.class);
        return configured(adapter).or(() -> SpringStreamingReturns.inspect(adapter, handlers));
    }

    private static boolean standardFactory(ConfigurableListableBeanFactory beans, String name) {
        return Optional.ofNullable(beans.getMergedBeanDefinition(name).getFactoryBeanName())
            .flatMap(factory -> Optional.ofNullable(beans.getType(factory, false)))
            .map(ClassUtils::getUserClass).map(Class::getName).filter(FACTORIES::contains).isPresent();
    }

    static Optional<SpringStreamingResult> configured(RequestMappingHandlerAdapter adapter) {
        if (adapter.getClass() != RequestMappingHandlerAdapter.class) {
            return Optional.of(SpringStreamingResult.unsupported("custom MVC request adapter"));
        }
        return timeout(adapter);
    }

    static Optional<SpringStreamingResult> timeout(Object adapter) {
        try {
            Optional<Object> timeout = Optional.ofNullable(
                new DirectFieldAccessor(adapter)
                    .getPropertyValue("asyncRequestTimeout")
            );
            return timeout.map(SpringStreamingAdapter::representation).orElseGet(
                () -> Optional.of(
                    new SpringStreamingResult("inherited", "MVC still inherits the servlet container timeout")
                )
            );
        } catch (BeansException exception) {
            return Optional.of(
                SpringStreamingResult.unsupported(
                    "cannot inspect the MVC timeout field: " + exception.getClass().getSimpleName()
                )
            );
        }
    }

    static Optional<SpringStreamingResult> representation(Object timeout) {
        return timeout instanceof Long ? Optional.empty()
            : Optional.of(SpringStreamingResult.unsupported("unexpected MVC timeout representation"));
    }
}
