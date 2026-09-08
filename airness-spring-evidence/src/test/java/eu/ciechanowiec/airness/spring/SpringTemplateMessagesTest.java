package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;

class SpringTemplateMessagesTest {

    private static final String APPLICATION = "example.Application";
    private static final String DIGEST = "a".repeat(64);
    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    @ResourceLock(Resources.LOCALE)
    void recordsCurrentAssessmentsForTheProductionApplication() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.registerBean(
                "messageSource", ResourceBundleMessageSource.class,
                () -> MessageContexts.source(loader, "labels")
            );
            context.refresh();
            Path destination = this.root.resolve("context.evidence");
            this.manifest();
            List<String> lines = this.evidenceWithArabicLocale(context, destination);
            assertEquals(2, lines.size());
            assertTrue(lines.stream().allMatch(line -> line.startsWith("messages 1 " + DIGEST + " ")));
            assertTrue(lines.stream().anyMatch(line -> line.contains(" missing ")));
            assertTrue(SpringTemplateMessages.evidence(context, List.of("other.Application"), destination).isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void keepsLegacyContextsUntouchedWithoutAManifestAndReportsMultipleEngines() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.registerBean("secondEngine", SpringTemplateEngine.class, () -> MessageContexts.engine(context));
            context.refresh();
            Path destination = this.root.resolve("context.evidence");
            assertTrue(SpringTemplateMessages.evidence(context, List.of(APPLICATION), destination).isEmpty());
            this.manifest();
            List<String> lines = SpringTemplateMessages.evidence(context, List.of(APPLICATION), destination);
            assertTrue(lines.getFirst().contains(" unassessed "));
        }
    }

    private List<String> evidenceWithArabicLocale(GenericApplicationContext context, Path destination) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"));
            return SpringTemplateMessages.evidence(context, List.of(APPLICATION), destination);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @SneakyThrows
    private void manifest() {
        SpringMessageInput reference = new SpringMessageInput("templates/page.html", "page.html:1:1", "missing");
        Files.write(
            this.root.resolve("template-message-inputs.evidence"), List.of(
                "started 1", "digest " + DIGEST,
                "application " + APPLICATION, "reference " + reference.encoded()
            )
        );
    }
}
