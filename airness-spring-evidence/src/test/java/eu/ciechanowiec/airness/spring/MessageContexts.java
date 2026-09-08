package eu.ciechanowiec.airness.spring;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

@UtilityClass
final class MessageContexts {

    @SneakyThrows
    static URLClassLoader files(Path root, String content) {
        Files.createDirectories(root.resolve("templates"));
        Files.writeString(root.resolve("labels.properties"), content);
        Files.writeString(root.resolve("templates/page.html"), "<p th:text=\"#{caption}\"></p>");
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, MessageContexts.class.getClassLoader());
    }

    static ResourceBundleMessageSource source(ClassLoader loader, String basename) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename(basename);
        source.setBundleClassLoader(loader);
        source.setFallbackToSystemLocale(false);
        return source;
    }

    static GenericApplicationContext context(ClassLoader loader) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setClassLoader(loader);
        context.registerBean("templateEngine", SpringTemplateEngine.class, () -> engine(context));
        return context;
    }

    static SpringTemplateEngine engine(GenericApplicationContext context) {
        SpringResourceTemplateResolver resolver = resolver(context);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    static SpringResourceTemplateResolver resolver(GenericApplicationContext context) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(context);
        resolver.setPrefix("classpath:/templates/");
        resolver.setSuffix(".html");
        return resolver;
    }
}
