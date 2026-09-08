package eu.ciechanowiec.airness.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.springframework.beans.BeansException;
import org.springframework.context.ConfigurableApplicationContext;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Message assessments appended by the existing startup-evidence listener.
 */
@UtilityClass
final class SpringTemplateMessages {

    static List<String> evidence(ConfigurableApplicationContext context, Collection<String> sources, Path destination) {
        Path manifest = destination.resolveSibling("template-message-inputs.evidence");
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        SpringMessageInputs inputs = SpringMessageInputs.read(manifest);
        List<String> applications = inputs.applications().stream().filter(sources::contains).toList();
        return applications.isEmpty() ? List.of() : assessed(context, applications, inputs);
    }

    private static List<String> assessed(
        ConfigurableApplicationContext context, Collection<String> applications,
        SpringMessageInputs inputs
    ) {
        List<SpringMessageResult> results = results(context, inputs.references());
        String label = context.getId() + " [" + String.join(",", context.getEnvironment().getActiveProfiles()) + "]";
        return applications.stream().flatMap(application -> lines(inputs, application, label, results)).toList();
    }

    private static List<SpringMessageResult> results(
        ConfigurableApplicationContext context, List<SpringMessageInput> inputs
    ) {
        String[] names = context.getBeanNamesForType(SpringTemplateEngine.class, false, false);
        return names.length == 1 ? resolved(context, names[0], inputs)
            : List.of(SpringMessageResult.unassessed("expected one Spring template engine, found " + names.length));
    }

    private static List<SpringMessageResult> resolved(
        ConfigurableApplicationContext context, String name,
        List<SpringMessageInput> inputs
    ) {
        try {
            SpringTemplateEngine engine = context.getBean(name, SpringTemplateEngine.class);
            SpringMessageSetup setup = SpringMessageSetup.inspect(
                engine,
                Objects.requireNonNull(context.getClassLoader(), "The application has no class loader")
            );
            return setup.resolution().map(resolution -> resolution.assess(inputs))
                .orElseGet(() -> List.of(SpringMessageResult.unassessed(setup.reason())));
        } catch (BeansException | IllegalArgumentException | TemplateEngineException exception) {
            return List.of(
                new SpringMessageResult("error", "message assessment raised " + exception.getClass().getName())
            );
        }
    }

    private static Stream<String> lines(
        SpringMessageInputs inputs, String application, String label,
        Collection<SpringMessageResult> results
    ) {
        String prefix = String.format(
            Locale.ROOT, "messages %d %s %s %s ", inputs.started(), inputs.digest(),
            SpringMessageInput.encode(application), SpringMessageInput.encode(label)
        );
        return results.stream().map(
            result -> prefix + result.status() + " " + SpringMessageInput.encode(result.detail())
        );
    }
}
