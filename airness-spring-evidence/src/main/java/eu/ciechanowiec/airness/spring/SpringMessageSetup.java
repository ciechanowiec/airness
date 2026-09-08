package eu.ciechanowiec.airness.spring;

import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DelegatingMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.messageresolver.SpringMessageResolver;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Qualifies the concrete standard setup instead of guessing custom resolver behavior.
 *
 * @param resolution the qualified setup
 * @param reason     why it is outside scope when no setup is present
 */
record SpringMessageSetup(Optional<SpringMessageResolution> resolution, String reason) {

    private static final String CLASSPATH = "classpath:";

    static SpringMessageSetup inspect(SpringTemplateEngine engine, ClassLoader loader) {
        if (engine.getClass() != SpringTemplateEngine.class) {
            return unsupported("custom template engine");
        }
        return configured(engine, loader);
    }

    private static SpringMessageSetup configured(SpringTemplateEngine engine, ClassLoader loader) {
        if (
            engine.getConfiguration().getMessageResolvers().size() != 1
                || engine.getConfiguration().getTemplateResolvers().size() != 1
        ) {
            return unsupported("multiple or absent message/template resolvers");
        }
        Object messages = engine.getConfiguration().getMessageResolvers().iterator().next();
        Object templates = engine.getConfiguration().getTemplateResolvers().iterator().next();
        return messages.getClass() == SpringMessageResolver.class && templates.getClass()
            == SpringResourceTemplateResolver.class
                ? standard((SpringMessageResolver) messages, (SpringResourceTemplateResolver) templates, loader)
                : unsupported("custom message or template resolver");
    }

    private static SpringMessageSetup standard(
        SpringMessageResolver messages, SpringResourceTemplateResolver templates,
        ClassLoader loader
    ) {
        String prefix = Optional.ofNullable(templates.getPrefix()).orElse("");
        if (!ordinary(templates) || !prefix.startsWith(CLASSPATH)) {
            return unsupported("template resolution is not ordinary classpath HTML");
        }
        MessageSource source = unwrap(messages.getMessageSource());
        String suffix = Optional.ofNullable(templates.getSuffix()).orElse("");
        String path = prefix.substring(CLASSPATH.length()).replaceFirst("^/+", "");
        return supported(source, loader) ? new SpringMessageSetup(
            Optional.of(new SpringMessageResolution(source, path, suffix, loader)), ""
        )
            : unsupported("message source is custom, has a parent, or lacks an unqualified base resource");
    }

    private static boolean ordinary(SpringResourceTemplateResolver templates) {
        boolean selection = templates.getTemplateMode() == TemplateMode.HTML && templates.getResolvablePatterns()
            .isEmpty()
            && templates.getTemplateAliases().isEmpty();
        return selection && !templates.getUseDecoupledLogic() && modes(templates);
    }

    private static boolean modes(SpringResourceTemplateResolver templates) {
        boolean documents = templates.getXmlTemplateModePatterns().isEmpty()
            && templates.getRawTemplateModePatterns().isEmpty() && templates.getTextTemplateModePatterns().isEmpty();
        return documents && templates.getJavaScriptTemplateModePatterns().isEmpty()
            && templates.getCSSTemplateModePatterns().isEmpty();
    }

    private static MessageSource unwrap(MessageSource source) {
        return source instanceof ApplicationContext context ? context.getBean("messageSource", MessageSource.class)
            : source;
    }

    private static boolean supported(MessageSource source, ClassLoader loader) {
        if (source.getClass() == DelegatingMessageSource.class) {
            return Optional.ofNullable(((HierarchicalMessageSource) source).getParentMessageSource()).isEmpty();
        }
        return source.getClass() == ResourceBundleMessageSource.class && bundles(
            (ResourceBundleMessageSource) source, loader
        );
    }

    private static boolean bundles(ResourceBundleMessageSource source, ClassLoader loader) {
        return Optional.ofNullable(source.getParentMessageSource()).isEmpty() && source.getBasenameSet().stream()
            .allMatch(
                name -> Optional.ofNullable(loader.getResource(name.replace('.', '/') + ".properties")).isPresent()
            );
    }

    private static SpringMessageSetup unsupported(String reason) {
        return new SpringMessageSetup(Optional.empty(), reason);
    }
}
