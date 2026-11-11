package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringTestListenersCheckstyleTest {

    private static final String RULE = "AirnessSpringTestListenersMergeWithDefaults";

    private static String declared(String declaration) {
        return """
            package example;
            %s
            final class ContextTest {
            }
            """.formatted(declaration);
    }

    @Test
    void reportsListenersNamedWithoutAMergeMode(@TempDir Path directory) {
        String source = declared("@TestExecutionListeners(Auditing.class)");

        assertEquals(1, CheckstyleRule.findings(directory, source, RULE), "the defaults are replaced");
    }

    @Test
    void reportsListenersNamedUnderTheValueAttribute(@TempDir Path directory) {
        String source = declared("@TestExecutionListeners(value = {Auditing.class, Seeding.class})");

        assertEquals(1, CheckstyleRule.findings(directory, source, RULE), "the defaults are replaced");
    }

    @Test
    void reportsListenersThatReplaceTheDefaultsExplicitly(@TempDir Path directory) {
        String source = declared(
            "@TestExecutionListeners(listeners = Auditing.class, mergeMode = MergeMode.REPLACE_DEFAULTS)"
        );

        assertEquals(1, CheckstyleRule.findings(directory, source, RULE), "the defaults are replaced");
    }

    @Test
    void reportsAFullyQualifiedDeclaration(@TempDir Path directory) {
        String source = declared("@org.springframework.test.context.TestExecutionListeners(Auditing.class)");

        assertEquals(1, CheckstyleRule.findings(directory, source, RULE), "the defaults are replaced");
    }

    @Test
    void acceptsListenersMergedThroughTheNestedEnum(@TempDir Path directory) {
        String source = declared(
            "@TestExecutionListeners(listeners = Auditing.class, mergeMode = MergeMode.MERGE_WITH_DEFAULTS)"
        );

        assertEquals(0, CheckstyleRule.findings(directory, source, RULE), "the defaults are kept");
    }

    @Test
    void acceptsListenersMergedThroughTheQualifiedEnum(@TempDir Path directory) {
        String source = declared(
            """
                @TestExecutionListeners(
                    value = Auditing.class,
                    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
                )"""
        );

        assertEquals(0, CheckstyleRule.findings(directory, source, RULE), "the defaults are kept");
    }

    @Test
    void acceptsListenersMergedThroughAStaticImport(@TempDir Path directory) {
        String source = declared(
            "@TestExecutionListeners(listeners = Auditing.class, mergeMode = MERGE_WITH_DEFAULTS)"
        );

        assertEquals(0, CheckstyleRule.findings(directory, source, RULE), "the defaults are kept");
    }
}
