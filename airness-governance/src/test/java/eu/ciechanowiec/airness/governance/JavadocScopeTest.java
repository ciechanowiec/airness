package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class JavadocScopeTest {

    private static final Path SOURCE = Path.of("src/main/java/example/Example.java");

    @Test
    void ignoresAnImportWrittenInsideATextBlock() {
        Predicate<String> resolves = JavadocScope.over(List.of(SOURCE)).named(
            SOURCE,
            """
                package example;

                final class Example {
                    String fixture = \"""
                        import sample.Vault;
                        \""";
                }
                """
        );
        assertFalse(resolves.test("Vault"));
    }

    @Test
    void keepsARealImportInScope() {
        Predicate<String> resolves = JavadocScope.over(List.of(SOURCE)).named(
            SOURCE, "package example;\nimport sample.Vault;\nfinal class Example {}"
        );
        assertTrue(resolves.test("Vault"));
    }

    @Test
    void recognizesJavaLangApartFromWhatTheFileNamesAndRejectsAnUnknownSimpleName() {
        Predicate<String> named = JavadocScope.over(List.of(SOURCE)).named(
            SOURCE, "package example; final class Example {}"
        );
        assertTrue(named.test("Example"), "a type the file declares is named by it");
        assertFalse(named.test("String"), "java.lang is resolved without the file naming it");
        assertTrue(JavadocScope.inJavaLang("String"), "but resolved all the same");
        assertFalse(named.test("Vault"));
        assertFalse(JavadocScope.inJavaLang("Vault"));
    }
}
