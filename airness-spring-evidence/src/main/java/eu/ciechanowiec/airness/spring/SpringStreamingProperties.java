package eu.ciechanowiec.airness.spring;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

@UtilityClass
final class SpringStreamingProperties {

    private static final String KEY = "spring.mvc.async.requesttimeout";

    static boolean declared(ConfigurableEnvironment environment, List<SpringStreamingInput> inputs) {
        return environment.getPropertySources().stream().flatMap(SpringStreamingProperties::flatten)
            .filter(EnumerablePropertySource.class::isInstance).map(EnumerablePropertySource.class::cast)
            .flatMap(SpringStreamingProperties::origins)
            .flatMap(origin -> Optional.ofNullable(origin.getResource()).stream())
            .anyMatch(resource -> production(resource, inputs));
    }

    private static Stream<PropertySource<?>> flatten(PropertySource<?> source) {
        return source instanceof CompositePropertySource composite ? composite.getPropertySources().stream()
            .flatMap(SpringStreamingProperties::flatten) : Stream.of(source);
    }

    private static Stream<TextResourceOrigin> origins(EnumerablePropertySource<?> source) {
        return Arrays.stream(source.getPropertyNames()).filter(name -> KEY.equals(canonical(name)))
            .map(name -> Optional.ofNullable(OriginLookup.getOrigin(source, name)))
            .flatMap(Optional::stream).filter(TextResourceOrigin.class::isInstance).map(TextResourceOrigin.class::cast);
    }

    private static boolean production(Resource resource, List<SpringStreamingInput> inputs) {
        try {
            String uri = resource.getURL().toExternalForm();
            Optional<SpringStreamingInput> input = inputs.stream()
                .filter(entry -> "resource".equals(entry.kind()) && entry.at(uri)).findFirst();
            return input.isPresent() && input.orElseThrow().detail().equals(fingerprint(resource));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect production timeout configuration", exception);
        }
    }

    private static String fingerprint(Resource resource) throws IOException {
        try (InputStream content = resource.getInputStream()) {
            return digest(new String(content.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @SneakyThrows
    private static String digest(String text) {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
        );
    }

    private static String canonical(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}
