package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class SpringTemplateMessageListenerTest {

    private static final int DIGEST_LENGTH = 64;
    private static final String DESTINATION = "airness.spring.context.evidence.file";
    private final Optional<String> previous;
    @TempDir
    private Path root;

    SpringTemplateMessageListenerTest() {
        this.previous = Optional.ofNullable(System.getProperty(DESTINATION));
    }

    @AfterEach
    void restore() {
        this.previous.ifPresentOrElse(
            value -> System.setProperty(DESTINATION, value), () -> System.clearProperty(DESTINATION)
        );
    }

    @Test
    void keepsABlankDestinationInactive() {
        System.setProperty(DESTINATION, "");
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            SpringContextEvidence listener = new SpringContextEvidence(
                new SpringApplication(SpringTemplateMessageListenerTest.class)
            );
            assertDoesNotThrow(() -> listener.ready(context, Duration.ZERO));
        }
    }

    @Test
    @SneakyThrows
    void extendsTheExistingReadyEvidenceWithMessageResults() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.refresh();
            Path destination = this.root.resolve("context.evidence");
            SpringMessageInput reference = new SpringMessageInput("templates/page.html", "page.html:1:1", "missing");
            String digest = "b".repeat(DIGEST_LENGTH);
            Files.write(
                this.root.resolve("template-message-inputs.evidence"), List.of(
                    "started 1", "digest " + digest,
                    "application " + SpringTemplateMessageListenerTest.class.getName(), "reference " + reference
                        .encoded()
                )
            );
            System.setProperty(DESTINATION, destination.toString());
            new SpringContextEvidence(new SpringApplication(SpringTemplateMessageListenerTest.class)).ready(
                context, Duration.ZERO
            );
            List<String> lines = Files.readAllLines(destination);
            assertTrue(lines.contains(SpringTemplateMessageListenerTest.class.getName()));
            assertTrue(
                lines.stream().anyMatch(line -> line.startsWith("messages 1 " + digest) && line.contains(" missing "))
            );
        }
    }
}
