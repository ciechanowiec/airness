package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import org.junit.jupiter.api.Test;

class ParameterAnnotationFormattingTest {

    @Test
    void separatesADeclarationFromAMultilineAnnotationGroup() {
        String source = """
            class Sample {
                void update(
                    @ModelAttribute(name = "form")
                    @Valid BookingForm bookingForm
                ) {}
            }
            """;
        String expected = """
            class Sample {
                void update(
                    @ModelAttribute(name = "form")
                    @Valid
                    BookingForm bookingForm
                ) {}
            }
            """;

        assertEquals(expected, ParameterAnnotationFormatting.normalized(source));
    }

    @Test
    void leavesExpandedAndCompactGroupsUnchanged() {
        String source = """
            class Sample {
                void expanded(
                    @First
                    @Second
                    Type value
                ) {}
                void compact(@First @Second Type value) {}
                void single(
                    @First Type value
                ) {}
            }
            """;

        assertEquals(source, ParameterAnnotationFormatting.normalized(source));
    }

    @Test
    void appliesSeveralReplacementsWithoutMovingLaterOffsets() {
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
        String expected = """
            class Sample {
                void update(
                    @First
                    @Second
                    Type first,
                    @Third
                    @Fourth
                    Type second
                ) {}
            }
            """;

        assertEquals(expected, ParameterAnnotationFormatting.normalized(source));
    }

    @Test
    void placesAFinalModifierOnTheDeclarationLine() {
        String source = """
            class Sample {
                void update(
                    @First
                    @Second final Type value
                ) {}
            }
            """;
        String expected = """
            class Sample {
                void update(
                    @First
                    @Second
                    final Type value
                ) {}
            }
            """;

        assertEquals(expected, ParameterAnnotationFormatting.normalized(source));
    }

    @Test
    void ignoresNestedAndStructurallyTypedAnnotations() {
        String source = """
            class Sample {
                void nested(@Outer(@Inner("x")) Type value) {}
                void typed(
                    @Binding java.lang.@Nullable String value
                ) {}
            }
            """;

        assertEquals(source, ParameterAnnotationFormatting.normalized(source));
    }

    @Test
    void leavesHorizontalWhitespaceBeforeAnExistingLineBreak() {
        String source = """
            class Sample {
                void update(
                    @First
                    @Second\s\s
                    Type value
                ) {}
            }
            """;

        assertEquals(source, ParameterAnnotationFormatting.normalized(source));
    }

    @Test
    void reportsSourceTheParserCannotRead() {
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> ParameterAnnotationFormatting.normalized("class Broken {")
        );
        String message = Objects.requireNonNull(thrown.getMessage());

        assertTrue(message.startsWith("Could not parse formatted Java source:"));
    }
}
