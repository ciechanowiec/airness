package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class NamedFragmentRenderingTest {

    @TempDir
    private Path directory;

    @Test
    void rendersExtraNamedLocalsWithEverySupportedSignatureShape() {
        for (String declaration : List.of("card", "card()", "card(title)", "card(title,theme)")) {
            assertEquals(
                "<article>Hello/dark</article>",
                this.render(declaration, "theme='dark',title='Hello',extra='ignored'")
            );
        }
    }

    @Test
    void evaluatesVariablesBesideApostrophesInLiteralSubstitutions() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        assertEquals(
            "<span>It&#39;s VALUE and it&#39;s ready</span>",
            engine.process(
                "<span th:text=\"|It's ${ordered} and it's ready|\">Text</span>",
                new Context(Locale.ROOT, Map.of("ordered", "VALUE"))
            )
        );
    }

    @Test
    void resolvesComputedNamedLabelsAtRenderTime() {
        assertEquals("<article>Hello/dark</article>", this.render("card(title)", "${key}='Hello',theme='dark'"));
        assertEquals("<article>Hello/dark</article>", this.render("card(title)", "'ti' + 'tle'='Hello',theme='dark'"));
    }

    @Test
    void acceptsQuotedAndHyphenatedNamedLabels() {
        assertEquals("<article>Hello/dark</article>", this.render("card(title)", "'title'='Hello',theme='dark'"));
        assertEquals(
            "<article>Hello/dark</article>",
            this.render("card(title)", "title='Hello',theme='dark',data-theme='extra'")
        );
    }

    @Test
    void rejectsMissingNamesAndMixedArgumentForms() {
        for (String arguments : List.of("theme='dark',other='Hello'", "title='Hello','dark'")) {
            assertThrows(TemplateProcessingException.class, () -> this.render("card(title,theme)", arguments));
        }
        assertThrows(TemplateProcessingException.class, () -> this.render("card(title)", "title='Hello','dark'"));
    }

    @SneakyThrows
    private String render(String declaration, String arguments) {
        Files.writeString(
            this.directory.resolve("card.html"), "<article th:fragment=\"" + declaration
                + "\" th:text=\"|${title}/${theme}|\">Card</article>"
        );
        Files.writeString(
            this.directory.resolve("page.html"), "<div th:replace=\"~{card :: card("
                + arguments + ")}\"></div>"
        );
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(this.directory + "/");
        resolver.setSuffix(".html");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine.process("page", new Context(Locale.ROOT, Map.of("key", "title")));
    }
}
