package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;

class SpringMessageDependencyTest {

    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void acceptsBaseMessagesFromARealDependencyJar() {
        Path dependency = this.root.resolve("messages.jar");
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(dependency))) {
            archive.putNextEntry(new JarEntry("library/labels.properties"));
            archive.write("caption=Dependency caption\n".getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        URL[] urls = {dependency.toUri().toURL()};
        try (
            URLClassLoader loader = new URLClassLoader(urls, this.getClass().getClassLoader());
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.registerBean(
                "messageSource", ResourceBundleMessageSource.class,
                () -> MessageContexts.source(loader, "library.labels")
            );
            context.refresh();
            SpringMessageResolution resolution = SpringMessageSetup.inspect(
                context.getBean(SpringTemplateEngine.class), loader
            )
                .resolution().orElseThrow();
            assertFalse(new SpringMessageLookup(resolution.source()).missing("caption"));
        }
    }
}
