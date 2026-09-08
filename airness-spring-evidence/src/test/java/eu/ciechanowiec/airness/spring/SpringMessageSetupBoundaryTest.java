package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.messageresolver.SpringMessageResolver;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

class SpringMessageSetupBoundaryTest {

    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void declinesNonstandardTemplateResolutionWithoutGuessing() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "caption=Caption");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.refresh();
            List<Consumer<SpringResourceTemplateResolver>> changes = List.of(
                resolver -> resolver.setPrefix("file:/templates/"),
                resolver -> resolver.setTemplateMode(TemplateMode.XML),
                resolver -> resolver.setResolvablePatterns(Set.of("admin/*")),
                resolver -> resolver.setTemplateAliases(Map.of("home", "page")),
                resolver -> resolver.setUseDecoupledLogic(true),
                resolver -> resolver.setXmlTemplateModePatterns(Set.of("xml/*")),
                resolver -> resolver.setJavaScriptTemplateModePatterns(Set.of("scripts/*")),
                resolver -> resolver.setCSSTemplateModePatterns(Set.of("styles/*")),
                resolver -> resolver.setRawTemplateModePatterns(Set.of("raw/*")),
                resolver -> resolver.setTextTemplateModePatterns(Set.of("text/*"))
            );
            changes.forEach(change -> assertTrue(inspect(context, loader, change).resolution().isEmpty()));
        }
    }

    @Test
    @SneakyThrows
    void declinesMissingBaseResourcesAndParentMessageSources() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.refresh();
            SpringTemplateEngine absent = MessageContexts.engine(context);
            absent.setTemplateEngineMessageSource(MessageContexts.source(loader, "absent"));
            assertTrue(SpringMessageSetup.inspect(absent, loader).resolution().isEmpty());
            ResourceBundleMessageSource child = MessageContexts.source(loader, "labels");
            child.setParentMessageSource(MessageContexts.source(loader, "labels"));
            SpringTemplateEngine parent = MessageContexts.engine(context);
            parent.setTemplateEngineMessageSource(child);
            assertTrue(SpringMessageSetup.inspect(parent, loader).resolution().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void declinesProxyEnginesAndMultipleResolvers() {
        try (
            URLClassLoader loader = MessageContexts.files(this.root, "");
            GenericApplicationContext context = MessageContexts.context(loader)
        ) {
            context.refresh();
            ProxyFactory proxy = new ProxyFactory(context.getBean(SpringTemplateEngine.class));
            proxy.setProxyTargetClass(true);
            assertTrue(
                SpringMessageSetup.inspect((SpringTemplateEngine) proxy.getProxy(), loader).resolution().isEmpty()
            );
            SpringTemplateEngine multiple = MessageContexts.engine(context);
            multiple.setMessageResolvers(Set.of(new StandardMessageResolver(), new SpringMessageResolver()));
            assertTrue(SpringMessageSetup.inspect(multiple, loader).resolution().isEmpty());
        }
    }

    private static SpringMessageSetup inspect(
        GenericApplicationContext context, ClassLoader loader,
        Consumer<SpringResourceTemplateResolver> change
    ) {
        SpringResourceTemplateResolver resolver = MessageContexts.resolver(context);
        change.accept(resolver);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setMessageSource(context);
        return SpringMessageSetup.inspect(engine, loader);
    }
}
