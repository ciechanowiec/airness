package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;

class SpringMessageLookupTest {

    @TempDir
    private Path root;

    @Test
    @SneakyThrows
    void distinguishesMissingKeysFromEmptyAndKeyShapedTranslations() {
        try (URLClassLoader loader = MessageContexts.files(this.root, "empty=\nsame=same\ncaption=Caption\n")) {
            SpringMessageLookup lookup = new SpringMessageLookup(MessageContexts.source(loader, "labels"));
            assertFalse(lookup.missing("empty"));
            assertFalse(lookup.missing("same"));
            assertFalse(lookup.missing("caption"));
            assertTrue(lookup.missing("absent"));
        }
    }

    @Test
    @SneakyThrows
    void defeatsCodeAsDefaultAndDoesNotReserveTranslationText() {
        try (
            URLClassLoader loader = MessageContexts.files(
                this.root,
                "caption=airness-absent-message-first\nother=airness-absent-message-second\nformatted=Hello {0}\n"
            )
        ) {
            ResourceBundleMessageSource source = MessageContexts.source(loader, "labels");
            source.setUseCodeAsDefaultMessage(true);
            source.setAlwaysUseMessageFormat(true);
            SpringMessageLookup lookup = new SpringMessageLookup(source);
            assertTrue(lookup.missing("absent"));
            assertFalse(lookup.missing("caption"));
            assertFalse(lookup.missing("other"));
            assertFalse(lookup.missing("formatted"));
        }
    }
}
