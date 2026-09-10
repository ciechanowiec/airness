package eu.ciechanowiec.airness.spring;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

record SpringStreamingConfigurers(boolean declared, List<SpringStreamingResult> failures) {

    private static final String NONE = "none";
    private static final Set<String> STANDARD = Set.of(
        WebMvcConfigurer.class.getName(),
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration$WebMvcAutoConfigurationAdapter"
    );

    SpringStreamingConfigurers {
        failures = List.copyOf(failures);
    }

    static SpringStreamingConfigurers inspect(ConfigurableListableBeanFactory beans, SpringStreamingInputs inputs) {
        List<SpringStreamingResult> results = Arrays.stream(
            beans.getBeanNamesForType(WebMvcConfigurer.class, true, false)
        )
            .map(name -> inspect(beans, name, inputs)).flatMap(Optional::stream).toList();
        boolean declared = results.stream().anyMatch(result -> "declaration".equals(result.status()));
        return new SpringStreamingConfigurers(
            declared,
            results.stream().filter(result -> "unsupported".equals(result.status())).toList()
        );
    }

    private static Optional<SpringStreamingResult> inspect(
        ConfigurableListableBeanFactory beans,
        String name, SpringStreamingInputs inputs
    ) {
        return Optional.ofNullable(beans.getType(name, false)).map(ClassUtils::getUserClass)
            .flatMap(type -> callback(type, inputs));
    }

    @SneakyThrows
    private static Optional<SpringStreamingResult> callback(Class<?> type, SpringStreamingInputs inputs) {
        Method method = type.getMethod("configureAsyncSupport", AsyncSupportConfigurer.class);
        Class<?> declaring = method.getDeclaringClass();
        if (STANDARD.contains(declaring.getName())) {
            return Optional.empty();
        }
        return inputs.declared(declaring).map(input -> classified(input, declaring.getName()))
            .orElseGet(
                () -> Optional.of(
                    SpringStreamingResult.unsupported(
                        declaring.getName() + ": async callback is not a recognized named production declaration"
                    )
                )
            );
    }

    private static Optional<SpringStreamingResult> classified(SpringStreamingInput input, String name) {
        if (NONE.equals(input.detail())) {
            return Optional.empty();
        }
        SpringStreamingResult result = "timeout".equals(input.detail())
            ? new SpringStreamingResult("declaration", name)
            : SpringStreamingResult.unsupported(
                name + ": use a straight-line configureAsyncSupport callback calling setDefaultTimeout directly"
            );
        return Optional.of(result);
    }
}
