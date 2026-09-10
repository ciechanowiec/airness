package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

class SpringStreamingPropertiesTest {

    private static final String KEY = "spring.mvc.async.request-timeout";
    @TempDir
    private Path directory;

    @Test
    void acceptsALoadedProductionDeclarationWithoutRequiringItsValueToWin() {
        Path main = this.source("main/application.properties");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(property(main, KEY));
        environment.getPropertySources().addFirst(new MapPropertySource("test override", Map.of(KEY, "1s")));
        assertTrue(SpringStreamingProperties.declared(environment, List.of(input(main))));
    }

    @Test
    void aTestFileWithTheSameNameCannotStandInForProduction() {
        Path main = this.source("main/application.properties");
        Path tests = this.source("test/application.properties");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(property(tests, KEY));
        assertFalse(SpringStreamingProperties.declared(environment, List.of(input(main))));
    }

    @Test
    @SneakyThrows
    void changingCompiledConfigurationInvalidatesItsProvenance() {
        Path main = this.source("main/application.properties");
        SpringStreamingInput input = input(main);
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(property(main, KEY));
        Files.writeString(main, "changed configuration");
        assertFalse(SpringStreamingProperties.declared(environment, List.of(input)));
    }

    @Test
    void inactiveAndOriginlessValuesDoNotDeclareProductionPolicy() {
        Path main = this.source("main/application.properties");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test only", Map.of(KEY, "0")));
        assertFalse(SpringStreamingProperties.declared(environment, List.of(input(main))));
        environment.getPropertySources().addFirst(property(main, "other.setting"));
        assertFalse(SpringStreamingProperties.declared(environment, List.of(input(main))));
    }

    @Test
    void supportsCompositeSourcesAndRelaxedPropertySpelling() {
        Path main = this.source("main/application.properties");
        StandardEnvironment environment = new StandardEnvironment();
        CompositePropertySource composite = new CompositePropertySource("composite");
        composite.addPropertySource(property(main, "spring.mvc.async.requestTimeout"));
        environment.getPropertySources().addFirst(composite);
        assertTrue(SpringStreamingProperties.declared(environment, List.of(input(main))));
    }

    @SneakyThrows
    private Path source(String name) {
        Path file = this.directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, KEY + "=${ARCHIVE_TIMEOUT:0}\n");
        return file;
    }

    private static OriginTrackedMapPropertySource property(Path file, String key) {
        TextResourceOrigin origin = new TextResourceOrigin(
            new FileSystemResource(file), new TextResourceOrigin.Location(0, 0)
        );
        return new OriginTrackedMapPropertySource(file.toString(), Map.of(key, OriginTrackedValue.of("0", origin)));
    }

    @SneakyThrows
    private static SpringStreamingInput input(Path file) {
        String hash = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(Files.readString(file).getBytes(StandardCharsets.UTF_8))
        );
        return new SpringStreamingInput("resource", file.getFileName().toString(), file.toUri().toString(), hash);
    }
}
