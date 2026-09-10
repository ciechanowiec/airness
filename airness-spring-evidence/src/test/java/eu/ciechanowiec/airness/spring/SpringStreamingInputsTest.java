package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

class SpringStreamingInputsTest {

    @TempDir
    private Path directory;

    @Test
    void readsThePreparedManifestAndClassProvenance() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(StreamingWorld.type(StreamingOptions.class, "timeout"))
        );
        Path evidence = this.directory.resolve("context");
        StreamingWorld.write(evidence, inputs);
        SpringStreamingInputs read = SpringStreamingInputs.read(
            evidence.resolveSibling("streaming-timeout-inputs.evidence")
        );
        assertEquals(inputs, read);
        assertTrue(read.owns(StreamingOptions.class));
        assertEquals("timeout", read.declared(StreamingOptions.class).orElseThrow().detail());
        assertFalse(read.owns(String.class));
        assertTrue(read.declared(String.class).isEmpty());
        assertTrue(read.declared(StreamingWeb.class).isEmpty());
    }

    @Test
    @SneakyThrows
    void refusesMalformedAndMissingManifestData() {
        Path path = this.directory.resolve("invalid");
        assertThrows(UncheckedIOException.class, () -> SpringStreamingInputs.read(path));
        Files.writeString(path, "started 1\ndigest bad\n");
        assertThrows(IllegalArgumentException.class, () -> SpringStreamingInputs.read(path));
        Files.writeString(path, "digest " + StreamingWorld.DIGEST + "\n");
        assertThrows(IllegalArgumentException.class, () -> SpringStreamingInputs.read(path));
        Files.writeString(path, "started 1\nstarted 2\ndigest " + StreamingWorld.DIGEST + "\n");
        assertThrows(IllegalArgumentException.class, () -> SpringStreamingInputs.read(path));
        assertThrows(IllegalArgumentException.class, () -> SpringStreamingInput.parse("broken"));
        assertThrows(
            IllegalArgumentException.class, () -> SpringStreamingInput.parse(
                SpringStreamingInput.encode("unknown") + " YQ Yg Yw"
            )
        );
    }

    @Test
    void resourceAndForeignClassOriginsCannotEstablishProductionOwnership() {
        SpringStreamingInput resource = new SpringStreamingInput(
            "resource", StreamingOptions.class.getName(), StreamingWorld.type(StreamingOptions.class, "none").origin(),
            "hash"
        );
        SpringStreamingInput foreign = new SpringStreamingInput(
            "type", StreamingOptions.class.getName(), "file:/other/classes/", "timeout"
        );
        SpringStreamingInputs inputs = StreamingWorld.inputs(List.of(resource, foreign));
        assertFalse(inputs.owns(StreamingOptions.class));
        assertTrue(inputs.declared(StreamingOptions.class).isEmpty());
    }

    @Test
    void examinesPrototypeConfigurerDeclarationsWithoutReplayingThem() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        RootBeanDefinition configurer = new RootBeanDefinition(StreamingOptions.class);
        configurer.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        configurer.setInstanceSupplier(
            () -> {
                throw new IllegalStateException("Inspection must not construct a configurer");
            }
        );
        beans.registerBeanDefinition("prototypeConfigurer", configurer);
        assertFalse(SpringStreamingConfigurers.inspect(beans, StreamingWorld.inputs(List.of())).failures().isEmpty());
    }

    @Test
    void matchesEquivalentFileAndJarLocations() {
        SpringStreamingInput file = new SpringStreamingInput(
            "resource", "config", "file:///main/config.properties", "hash"
        );
        assertTrue(file.at("file:/main/config.properties"));
        assertFalse(file.at("file:/test/config.properties"));
        SpringStreamingInput jar = new SpringStreamingInput(
            "resource", "config", "jar:file:///main.jar!/config.properties", "hash"
        );
        assertTrue(jar.at("jar:file:/main.jar!/config.properties"));
        SpringStreamingInput remote = new SpringStreamingInput(
            "source", "remote", "https://example.test/config", "hash"
        );
        assertTrue(remote.at("https://example.test/config"));
    }
}
