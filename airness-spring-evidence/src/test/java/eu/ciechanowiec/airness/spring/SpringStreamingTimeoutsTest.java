package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class SpringStreamingTimeoutsTest {

    @TempDir
    private Path directory;

    @Test
    void acceptsProductionJavaConfigurationWithoutReplayingCallbacks() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(
                StreamingWorld.type(StreamingEndpoints.class, "none"),
                StreamingWorld.type(StreamingOptions.class, "timeout")
            )
        );
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            List<String> lines = this.evidence(context, inputs);
            assertEquals(1, lines.size());
            assertTrue(lines.getFirst().contains(" assessed "));
            assertEquals(1, context.getBean(StreamingOptions.class).calls());
            assertEquals(0, context.getBean(StreamingEndpoints.class).calls());
        }
    }

    @Test
    void reportsMissingPolicyAndInheritedContainerTimeout() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(StreamingWorld.type(StreamingEndpoints.class, "none"))
        );
        try (AnnotationConfigWebApplicationContext context = StreamingWorld.context(StreamingWeb.class)) {
            List<String> lines = this.evidence(context, inputs);
            assertTrue(lines.stream().anyMatch(line -> line.contains(" missing ")));
            assertTrue(lines.stream().anyMatch(line -> line.contains(" inherited ")));
            assertEquals(3, lines.size());
        }
    }

    @Test
    void aTestOnlyConfigurerCannotSupplyTheProductionDeclaration() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(StreamingWorld.type(StreamingEndpoints.class, "none"))
        );
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            List<String> lines = this.evidence(context, inputs);
            assertTrue(lines.stream().anyMatch(line -> line.contains(" unsupported ")));
            assertTrue(lines.stream().anyMatch(line -> line.contains(" missing ")));
            assertFalse(lines.stream().anyMatch(line -> line.contains(" inherited ")));
        }
    }

    @Test
    void anOpaqueProductionCallbackCannotPass() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(
                StreamingWorld.type(StreamingEndpoints.class, "none"),
                StreamingWorld.type(StreamingOptions.class, "unsupported")
            )
        );
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            assertTrue(this.evidence(context, inputs).stream().anyMatch(line -> line.contains(" unsupported ")));
        }
    }

    @Test
    void aNonTimeoutDeclarationDoesNotEstablishAPolicy() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(
            List.of(StreamingWorld.type(StreamingOptions.class, "none"))
        );
        try (
            AnnotationConfigWebApplicationContext context = StreamingWorld.context(
                StreamingWeb.class, StreamingOptions.class
            )
        ) {
            SpringStreamingConfigurers result = SpringStreamingConfigurers.inspect(context.getBeanFactory(), inputs);
            assertFalse(result.declared());
            assertTrue(result.failures().isEmpty());
        }
    }

    @Test
    void noRegisteredProductionStreamingHandlersNeedNoPolicy() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(List.of());
        try (AnnotationConfigWebApplicationContext context = StreamingWorld.context(StreamingWeb.class)) {
            assertEquals(1, this.evidence(context, inputs).size());
        }
    }

    @Test
    void nonWebContextsProduceAnExplicitEmptyAssessment() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(List.of());
        Path evidence = this.directory.resolve("context.evidence");
        StreamingWorld.write(evidence, inputs);
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            assertEquals(1, SpringStreamingTimeouts.evidence(context, inputs.applications(), evidence).size());
            assertTrue(SpringStreamingTimeouts.evidence(context, List.of("other.Application"), evidence).isEmpty());
        }
    }

    @Test
    void absenceOfAManifestCannotManufactureAnAssessment() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            assertTrue(
                SpringStreamingTimeouts.evidence(
                    context, List.of(StreamingWeb.class.getName()),
                    this.directory.resolve("absent.evidence")
                ).isEmpty()
            );
        }
    }

    @Test
    void distinguishesExplicitZeroAndFiniteValuesFromAnUnsetField() {
        RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
        assertEquals("inherited", SpringStreamingAdapter.configured(adapter).orElseThrow().status());
        adapter.setAsyncRequestTimeout(0);
        assertTrue(SpringStreamingAdapter.configured(adapter).isEmpty());
        adapter.setAsyncRequestTimeout(1);
        assertTrue(SpringStreamingAdapter.configured(adapter).isEmpty());
        adapter.setAsyncRequestTimeout(-1);
        assertTrue(SpringStreamingAdapter.configured(adapter).isEmpty());
    }

    @Test
    @SneakyThrows
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void theExistingReadyListenerPublishesTheAssessment() {
        SpringStreamingInputs inputs = StreamingWorld.inputs(List.of());
        Path evidence = this.directory.resolve("listener.evidence");
        StreamingWorld.write(evidence, inputs);
        String property = "airness.spring.context.evidence.file";
        Optional<String> previous = Optional.ofNullable(System.getProperty(property));
        System.setProperty(property, evidence.toString());
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.refresh();
            new SpringContextEvidence(new SpringApplication(StreamingWeb.class)).ready(context, Duration.ZERO);
            assertTrue(Files.readAllLines(evidence).stream().anyMatch(line -> line.startsWith("streaming ")));
        } finally {
            previous.ifPresentOrElse(
                value -> System.setProperty(property, value), () -> System.clearProperty(property)
            );
        }
    }

    private List<String> evidence(AnnotationConfigWebApplicationContext context, SpringStreamingInputs inputs) {
        Path evidence = this.directory.resolve("context.evidence");
        StreamingWorld.write(evidence, inputs);
        return SpringStreamingTimeouts.evidence(context, inputs.applications(), evidence);
    }
}
