package eu.ciechanowiec.airness.spring;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

@UtilityClass
final class StreamingWorld {

    static final String DIGEST = "a".repeat(64);

    static AnnotationConfigWebApplicationContext context(Class<?>... configurations) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(configurations);
        context.refresh();
        return context;
    }

    static SpringStreamingInput type(Class<?> type, String classification) {
        String origin = Objects.requireNonNull(type.getProtectionDomain().getCodeSource()).getLocation()
            .toExternalForm();
        return new SpringStreamingInput("type", type.getName(), origin, classification);
    }

    static SpringStreamingInputs inputs(Collection<SpringStreamingInput> declarations) {
        return new SpringStreamingInputs(1, DIGEST, List.of(StreamingWeb.class.getName()), List.copyOf(declarations));
    }

    @SneakyThrows
    static void write(Path evidence, SpringStreamingInputs inputs) {
        List<String> lines = Stream.concat(
            Stream.of("started " + inputs.started(), "digest " + inputs.digest()),
            Stream.concat(
                inputs.applications().stream().map(name -> "application " + name),
                inputs.declarations().stream().map(StreamingWorld::encoded)
            )
        ).toList();
        Files.write(evidence.resolveSibling("streaming-timeout-inputs.evidence"), lines);
    }

    private static String encoded(SpringStreamingInput input) {
        return "input " + String.join(
            " ", Stream.of(input.kind(), input.name(), input.origin(), input.detail())
                .map(SpringStreamingInput::encode).toList()
        );
    }
}
