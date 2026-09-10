package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingJavaIndexTest {

    private static final String UNSUPPORTED = "unsupported";
    private static final String ORIGIN = "file:/production/classes/";
    @TempDir
    private Path directory;

    @Test
    void recognizesDirectAndFluentTimeoutDeclarations() {
        assertEquals("timeout", this.policy("configurer.setDefaultTimeout(0);"));
        assertEquals("timeout", this.policy("long duration = choose(); configurer.setDefaultTimeout(duration);"));
        assertEquals("timeout", this.policy("configurer.setTaskExecutor(executor).setDefaultTimeout(30000);"));
    }

    @Test
    void rejectsConditionalAndDelegatedCallbacks() {
        assertEquals(UNSUPPORTED, this.policy("if (enabled) { configurer.setDefaultTimeout(0); }"));
        assertEquals(UNSUPPORTED, this.policy("configure(configurer);"));
        assertEquals(UNSUPPORTED, this.policy("Object alias = configurer; alias.setDefaultTimeout(0);"));
        assertEquals(UNSUPPORTED, this.policy("configure(configurer.setDefaultTimeout(0));"));
        assertEquals(UNSUPPORTED, this.policy("configurer.other();"));
    }

    @Test
    void unrelatedCallsAndCommentsDoNotDeclareAPolicy() {
        assertEquals("none", this.policy("other.setDefaultTimeout(0);"));
        assertEquals("none", this.policy("// configurer.setDefaultTimeout(0);\n"));
        assertEquals("none", this.policy("configurer.setTaskExecutor(executor);"));
    }

    @Test
    void indexesNamedNestedTypesAndEveryProductionOrigin() {
        List<StreamingInput> inputs = this.read("package example; class Outer { static class Inner {} }");
        assertTrue(inputs.stream().anyMatch(input -> "example.Outer$Inner".equals(input.name())));
        assertTrue(
            inputs.stream().filter(input -> "type".equals(input.kind())).allMatch(
                input -> ORIGIN.equals(input.origin())
            )
        );
        assertTrue(this.read("record Value() {}").stream().anyMatch(input -> "Value".equals(input.name())));
    }

    @Test
    void rejectsAmbiguousAndBodylessCallbackDeclarations() {
        List<StreamingInput> overloaded = this.read(
            """
                class Options {
                    void configureAsyncSupport(AsyncSupportConfigurer configurer) { configurer.setDefaultTimeout(0); }
                    void configureAsyncSupport(Object other) {}
                }
                """
        );
        assertEquals(UNSUPPORTED, type(overloaded).detail());
        assertEquals(UNSUPPORTED, type(this.read("interface Options { void configureAsyncSupport(); }")).detail());
        assertEquals(
            UNSUPPORTED, type(
                this.read("abstract class Options { abstract void configureAsyncSupport(Object input); }")
            ).detail()
        );
    }

    @Test
    void bindsTheManifestToSourceChanges() {
        StreamingInput first = this.read("class First {}").getFirst();
        StreamingInput changed = this.read("class Changed {}").getFirst();
        assertNotEquals(first.detail(), changed.detail());
    }

    @Test
    void recognizesTheProductionPropertyInBothSupportedFileFormats() {
        assertTrue(StreamingResourceIndex.declares("application.properties", "spring.mvc.async.requestTimeout=2m"));
        assertTrue(
            StreamingResourceIndex.declares(
                "application.yaml", "spring:\n  mvc:\n    async:\n      request-timeout: 0\n"
            )
        );
        assertFalse(StreamingResourceIndex.declares("application.properties", "# spring.mvc.async.request-timeout=0"));
    }

    @Test
    void rejectsUnparseableProductionSource() {
        assertThrows(IllegalArgumentException.class, () -> this.read("class Options {"));
    }

    private String policy(String statements) {
        return type(
            this.read(
                "class Options { void configureAsyncSupport(AsyncSupportConfigurer configurer) { "
                    + statements + " } }"
            )
        ).detail();
    }

    @SneakyThrows
    private List<StreamingInput> read(String content) {
        Path source = this.directory.resolve("Options.java");
        Files.writeString(source, content);
        return StreamingJavaIndex.read(source, "src/main/java/Options.java", List.of(ORIGIN));
    }

    private static StreamingInput type(List<StreamingInput> inputs) {
        return inputs.stream().filter(input -> "type".equals(input.kind())).findFirst().orElseThrow();
    }
}
