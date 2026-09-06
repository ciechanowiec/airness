package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringModifyingCheckstyleTest {

    private static final String RULE = "AirnessSpringModifyingClearsThePersistenceContext";

    private static final String QUALIFIED = "org.springframework.data.jpa.repository.Modifying";

    @Test
    void reportsMissingClearing(@TempDir Path directory) {
        for (String annotation : List.of("Modifying", QUALIFIED)) {
            assertEquals(1, findings(directory, annotation, ""), "the default leaves stale objects in memory");
        }
    }

    @Test
    void reportsDisabledClearing(@TempDir Path directory) {
        for (String annotation : List.of("Modifying", QUALIFIED)) {
            assertEquals(1, findings(directory, annotation, "(clearAutomatically = false)"));
        }
    }

    @Test
    void acceptsExplicitClearing(@TempDir Path directory) {
        for (String annotation : List.of("Modifying", QUALIFIED)) {
            assertEquals(0, findings(directory, annotation, "(clearAutomatically = true)"));
        }
    }

    @Test
    void requiresALiteralInsteadOfAnExpression(@TempDir Path directory) {
        for (String value : List.of("ENABLED", "DISABLED", "!false", "true || false", "true && false")) {
            assertEquals(1, findings(directory, "Modifying", "(clearAutomatically = %s)".formatted(value)));
        }
    }

    @Test
    void doesNotMistakeFlushingForClearing(@TempDir Path directory) {
        for (
            String attributes : List.of(
                "(flushAutomatically = true)",
                "(flushAutomatically = clearAutomatically)",
                "(clearAutomatically = false, flushAutomatically = true)"
            )
        ) {
            assertEquals(1, findings(directory, "Modifying", attributes));
        }
    }

    @Test
    void leavesFlushingToItsExistingContract(@TempDir Path directory) {
        for (
            String attributes : List.of(
                "(clearAutomatically = true, flushAutomatically = true)",
                "(flushAutomatically = true, clearAutomatically = true)",
                "(clearAutomatically = true, flushAutomatically = false)"
            )
        ) {
            assertEquals(0, findings(directory, QUALIFIED, attributes));
        }
    }

    @Test
    void passesOverOtherAnnotationTypes(@TempDir Path directory) {
        for (
            String annotation : List.of(
                "Other", "example.Modifying", "example.org.springframework.data.jpa.repository.Modifying"
            )
        ) {
            assertEquals(0, findings(directory, annotation, ""));
        }
    }

    @Test
    void doesNotTreatAnImportedTypeAsAnAppliedAnnotation(@TempDir Path directory) {
        String source = """
            import org.springframework.data.jpa.repository.Modifying;
            interface ChangesRepository {
                void change();
            }
            """;
        assertEquals(0, CheckstyleRule.findings(directory, source, RULE, "clearAutomatically"));
    }

    private static int findings(Path directory, String annotation, String attributes) {
        String source = """
            import org.springframework.data.jpa.repository.Modifying;
            import org.springframework.data.jpa.repository.Query;
            interface ChangesRepository {
                boolean ENABLED = true;
                boolean DISABLED = false;
                boolean clearAutomatically = true;
                @Query("delete from Entry entry")
                @%s%s
                void change();
            }
            """.formatted(annotation, attributes);
        return CheckstyleRule.findings(directory, source, RULE, "clearAutomatically");
    }
}
