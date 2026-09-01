package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParameterAnnotationLocationCheckTest {

    private static final String RULE = "AirnessParameterDeclarationStartsAfterAnnotations";

    @Test
    void reportsADeclarationBesideTheFinalAnnotation(@TempDir Path directory) {
        String source = """
            class Sample {
                void update(
                    @ModelAttribute(name = "form")
                    @Valid BookingForm bookingForm
                ) {}
            }
            """;

        assertEquals(1, findings(directory, source), "the declaration remains attached to the annotation group");
    }

    @Test
    void acceptsADeclarationAfterTheAnnotationGroup(@TempDir Path directory) {
        String source = """
            class Sample {
                void update(
                    @ModelAttribute(name = "form")
                    @Valid
                    BookingForm bookingForm
                ) {}
            }
            """;

        assertEquals(0, findings(directory, source), "the declaration begins on its own line");
    }

    @Test
    void acceptsCompactAnnotationForms(@TempDir Path directory) {
        String source = """
            class Sample {
                void single(@First Type value) {}
                void several(@First @Second Type value) {}
                void annotationsTogether(
                    @First @Second
                    Type value
                ) {}
            }
            """;

        assertEquals(0, findings(directory, source), "an annotation group confined to one line stays compact");
    }

    @Test
    void readsTheFinalLineOfAParameterizedAnnotation(@TempDir Path directory) {
        String source = """
            class Sample {
                void update(
                    @First
                    @Second(
                        name = "form"
                    ) Type value
                ) {}
            }
            """;

        assertEquals(1, findings(directory, source), "the annotation ends at its closing parenthesis");
    }

    @Test
    void treatsFinalAsTheStartOfTheDeclaration(@TempDir Path directory) {
        String source = """
            class Sample {
                void bad(
                    @First
                    @Second final Type value
                ) {}
                void good(
                    @First
                    @Second
                    final Type value
                ) {}
            }
            """;

        assertEquals(1, findings(directory, source), "the final modifier belongs to the declaration line");
    }

    @Test
    void countsEveryAnnotationInTheParameterModifierSequence(@TempDir Path directory) {
        String source = """
            class Sample {
                void update(
                    @Binding
                    @Nullable String value
                ) {}
            }
            """;

        assertEquals(1, findings(directory, source), "a leading type-use-capable annotation is syntactically direct");
    }

    @Test
    void ignoresNestedAndStructurallyTypedAnnotations(@TempDir Path directory) {
        String source = """
            class Sample {
                void nested(@Outer(@Inner("x")) Type value) {}
                void typed(
                    @Binding java.lang.@Nullable String value
                ) {}
            }
            """;

        assertEquals(0, findings(directory, source), "only direct parameter annotations form the group");
    }

    @Test
    void passesOverFieldsAndRecordComponents(@TempDir Path directory) {
        String source = """
            class Sample {
                @First
                @Second Type field;
            }
            record Row(
                @First
                @Second Type component
            ) {}
            """;

        assertEquals(0, findings(directory, source), "other checks own fields and record components");
    }

    @Test
    void reportsEveryOffendingParameter(@TempDir Path directory) {
        String source = """
            class Sample {
                void update(
                    @First
                    @Second Type first,
                    @Third
                    @Fourth Type second
                ) {}
            }
            """;

        assertEquals(2, findings(directory, source), "each declaration receives its own finding");
    }

    private static int findings(Path directory, String source) {
        return CheckstyleRule.findings(directory, source, RULE);
    }
}
