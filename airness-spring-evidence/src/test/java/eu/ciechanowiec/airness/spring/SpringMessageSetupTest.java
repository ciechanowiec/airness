package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;

class SpringMessageSetupTest {

    private static final String MISSING = "missing";

    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void usesTheEngineSourceInsteadOfAnUnrelatedApplicationSource() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "caption=Caption\n");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            ResourceBundleMessageSource unrelated = MessageContexts.source(loader, "labels");
            context.registerBean("messageSource", ResourceBundleMessageSource.class, () -> unrelated);
            context.refresh();
            SpringTemplateEngine engine = context.getBean(SpringTemplateEngine.class);
            ResourceBundleMessageSource empty = new ResourceBundleMessageSource();
            engine.setTemplateEngineMessageSource(empty);
            SpringMessageResolution resolution = SpringMessageSetup.inspect(engine, loader).resolution().orElseThrow();
            List<SpringMessageResult> result = resolution.assess(List.of(reference("caption")));
            assertTrue(result.stream().anyMatch(value -> MISSING.equals(value.status())));
        }
    }

    // The conventional bundle beside the selected one holds the key, and the selected one does not. Reading
    // the convention instead of the basename the source names would pass a key the page never resolves.
    @Test
    @SneakyThrows
    void readsOnlyTheBundleTheSelectedSourceNames() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "other=Other\n");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            Files.writeString(this.root.resolve("messages.properties"), "caption=Caption\n");
            context.registerBean(
                "messageSource", ResourceBundleMessageSource.class,
                () -> MessageContexts.source(loader, "labels")
            );
            context.refresh();
            SpringMessageResolution resolution = SpringMessageSetup.inspect(
                context.getBean(SpringTemplateEngine.class), loader
            ).resolution().orElseThrow();
            List<SpringMessageResult> result = resolution.assess(List.of(reference("caption")));
            assertEquals(
                List.of(MISSING),
                result.stream().skip(1).map(SpringMessageResult::status).toList(),
                "a key only an unselected bundle defines is missing from the selected source"
            );
        }
    }

    @Test
    @SneakyThrows
    void checksBaseMessagesAndReportsResourcesOutsideTheResolver() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "caption=Caption\n");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.registerBean(
                "messageSource", ResourceBundleMessageSource.class,
                () -> MessageContexts.source(loader, "labels")
            );
            context.refresh();
            SpringMessageResolution resolution = SpringMessageSetup.inspect(
                context.getBean(SpringTemplateEngine.class), loader
            ).resolution().orElseThrow();
            List<SpringMessageResult> result = resolution.assess(
                List.of(
                    reference("caption"), reference(MISSING),
                    new SpringMessageInput("outside/page.html", "outside:1:1", "absent")
                )
            );
            assertEquals(new SpringMessageResult("assessed", "2"), result.getFirst());
            assertEquals(1, result.stream().filter(value -> MISSING.equals(value.status())).count());
            assertEquals("unassessed", result.getLast().status());
        }
    }

    // A source answering every code with the code itself would hand a template the key as its text. The
    // assessment reads the bundles instead, so the key the template asks for is still missing.
    @Test
    @SneakyThrows
    void reportsAMissingKeyThatCodeAsDefaultWouldHaveAnswered() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "caption=Caption\n");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            ResourceBundleMessageSource answering = MessageContexts.source(loader, "labels");
            answering.setUseCodeAsDefaultMessage(true);
            context.registerBean("messageSource", ResourceBundleMessageSource.class, () -> answering);
            context.refresh();
            SpringMessageResolution resolution = SpringMessageSetup.inspect(
                context.getBean(SpringTemplateEngine.class), loader
            ).resolution().orElseThrow();
            List<SpringMessageResult> result = resolution.assess(List.of(reference("caption"), reference("absent")));
            assertEquals(new SpringMessageResult("assessed", "2"), result.getFirst());
            assertEquals(
                List.of(MISSING),
                result.stream().skip(1).map(SpringMessageResult::status).toList(),
                "only the key no bundle defines is reported"
            );
        }
    }

    @Test
    @SneakyThrows
    void treatsAnAbsentStandardMessageSourceAsMissingDefinitions() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.refresh();
            SpringMessageResolution resolution = SpringMessageSetup.inspect(
                context.getBean(SpringTemplateEngine.class), loader
            ).resolution().orElseThrow();
            assertTrue(
                resolution.assess(List.of(reference(MISSING))).stream()
                    .anyMatch(value -> MISSING.equals(value.status()))
            );
        }
    }

    @Test
    @SneakyThrows
    void leavesCustomSourcesAndResolversExplicitlyUnassessed() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.registerBean("messageSource", ReloadableResourceBundleMessageSource.class);
            context.refresh();
            assertTrue(
                SpringMessageSetup.inspect(context.getBean(SpringTemplateEngine.class), loader).resolution().isEmpty()
            );
            SpringTemplateEngine custom = MessageContexts.engine(context);
            custom.setMessageResolver(new StandardMessageResolver());
            assertFalse(SpringMessageSetup.inspect(custom, loader).reason().isEmpty());
        }
    }

    private static SpringMessageInput reference(String key) {
        return new SpringMessageInput("templates/page.html", "page.html:1:1", key);
    }
}
