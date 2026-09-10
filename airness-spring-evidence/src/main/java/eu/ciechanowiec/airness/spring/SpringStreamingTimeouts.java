package eu.ciechanowiec.airness.spring;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.springframework.beans.BeansException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.method.HandlerMethod;

@UtilityClass
final class SpringStreamingTimeouts {

    static List<String> evidence(ConfigurableApplicationContext context, Collection<String> sources, Path destination) {
        Path manifest = destination.resolveSibling("streaming-timeout-inputs.evidence");
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        SpringStreamingInputs inputs = SpringStreamingInputs.read(manifest);
        List<String> applications = inputs.applications().stream().filter(sources::contains).toList();
        List<SpringStreamingResult> results = applications.isEmpty() ? List.of() : results(context, inputs);
        String label = context.getId() + " [" + String.join(",", context.getEnvironment().getActiveProfiles()) + "]";
        return applications.stream().flatMap(
            application -> results.stream()
                .map(result -> result.encoded(inputs, application, label))
        ).toList();
    }

    private static List<SpringStreamingResult> results(
        ConfigurableApplicationContext context,
        SpringStreamingInputs inputs
    ) {
        try {
            List<HandlerMethod> handlers = SpringStreamingEndpoints.registered(context, inputs);
            List<SpringStreamingResult> failures = handlers.isEmpty() ? List.of() : failures(context, inputs, handlers);
            return Stream.concat(
                failures.stream(), Stream.of(
                    new SpringStreamingResult(
                        "assessed",
                        Integer.toString(handlers.size())
                    )
                )
            ).toList();
        } catch (BeansException | IllegalArgumentException | IllegalStateException | UncheckedIOException exception) {
            return List.of(
                new SpringStreamingResult(
                    "error",
                    "streaming assessment raised " + exception.getClass().getName()
                )
            );
        }
    }

    private static List<SpringStreamingResult> failures(
        ConfigurableApplicationContext context,
        SpringStreamingInputs inputs, List<HandlerMethod> handlers
    ) {
        SpringStreamingConfigurers javaPolicy = SpringStreamingConfigurers.inspect(context.getBeanFactory(), inputs);
        boolean property = SpringStreamingProperties.declared(context.getEnvironment(), inputs.declarations());
        List<SpringStreamingResult> missing = javaPolicy.declared() || property ? List.of() : List.of(
            new SpringStreamingResult(
                "missing", "declare spring.mvc.async.request-timeout in production configuration"
                    + " or call setDefaultTimeout in a named production WebMvcConfigurer"
            )
        );
        String locations = handlers.stream().map(SpringStreamingEndpoints::location).sorted()
            .collect(Collectors.joining(", "));
        return Stream.of(
            javaPolicy.failures().stream(), missing.stream(), SpringStreamingAdapter.inspect(context, handlers).stream()
        )
            .flatMap(stream -> stream).map(
                result -> new SpringStreamingResult(
                    result.status(),
                    locations + ": " + result.detail()
                )
            ).toList();
    }
}
