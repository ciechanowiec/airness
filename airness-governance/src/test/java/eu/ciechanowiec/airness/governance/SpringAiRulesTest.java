package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpringAiRulesTest {

    private static final String TOOL_IMPORT
        = "import org.springframework.ai.tool.annotation.Tool;";

    private static String source(String imported, String annotation) {
        return """
            package com.example;

            %s

            final class Tools {

                %s
                String calculate() {
                    return "answer";
                }
            }
            """.formatted(imported, annotation);
    }

    @Test
    void reportsAToolWithNoDescription() {
        List<String> offences = SpringAiRules.missingDescriptions(source(TOOL_IMPORT, "@Tool"));

        assertEquals(1, offences.size(), "the model-facing contract says nothing");
        assertTrue(offences.getFirst().contains("model"), "the offence states the affected caller");
    }

    @Test
    void reportsAnExplicitlyBlankDescription() {
        String annotation = "@Tool(description = \"   \")";

        assertEquals(
            1, SpringAiRules.missingDescriptions(source(TOOL_IMPORT, annotation)).size(),
            "spaces do not describe an operation"
        );
    }

    @Test
    void acceptsANonblankDescription() {
        String annotation = "@Tool(description = \"Calculates the answer for the current request\")";

        assertEquals(
            List.of(), SpringAiRules.missingDescriptions(source(TOOL_IMPORT, annotation)),
            "the model can decide when the operation applies"
        );
    }

    @Test
    void resolvesADescriptionConstantInTheSameSource() {
        String declared = source(TOOL_IMPORT, "@Tool(description = DESCRIPTION)")
            .replace(
                "final class Tools {",
                "final class Tools {\n\n    private static final String DESCRIPTION = \"Calculates an answer\";"
            );

        assertEquals(List.of(), SpringAiRules.missingDescriptions(declared), "the constant has a value");
    }

    @Test
    void reportsABlankDescriptionConstantInTheSameSource() {
        String declared = source(TOOL_IMPORT, "@Tool(description = DESCRIPTION)")
            .replace(
                "final class Tools {",
                "final class Tools {\n\n    private static final String DESCRIPTION = \"\";"
            );

        assertEquals(1, SpringAiRules.missingDescriptions(declared).size(), "the constant is blank");
    }

    @Test
    void passesOverADescriptionExpressionItCannotResolve() {
        String annotation = "@Tool(description = Descriptions.CALCULATION)";

        assertEquals(
            List.of(), SpringAiRules.missingDescriptions(source(TOOL_IMPORT, annotation)),
            "a value declared elsewhere is not guessed at"
        );
    }

    @Test
    void readsEverySupportedSpringAiAnnotation() {
        String imports = """
            import org.springframework.ai.mcp.annotation.*;
            import org.springframework.ai.tool.annotation.Tool;
            """;
        String annotations = """
            @Tool
            @McpTool
            @McpPrompt
            @McpResource
            """;

        assertEquals(
            4, SpringAiRules.missingDescriptions(source(imports, annotations)).size(),
            "each published operation has its own contract"
        );
    }

    @Test
    void readsAFullyQualifiedAnnotationWithoutAnImport() {
        String annotation = "@org.springframework.ai.tool.annotation.Tool";

        assertEquals(
            1, SpringAiRules.missingDescriptions(source("", annotation)).size(),
            "the supplier is stated at the use"
        );
    }

    @Test
    void passesOverAnUnrelatedToolAnnotation() {
        String unrelated = "import com.example.commands.Tool;";

        assertEquals(
            List.of(), SpringAiRules.missingDescriptions(source(unrelated, "@Tool")),
            "the generic simple name proves no Spring AI relationship"
        );
    }
}
