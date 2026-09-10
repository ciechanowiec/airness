package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RequestMapScopeCheckstyleTest {

    @Test
    void requiresTheOuterDeclaredTypeToBeAMap(@TempDir Path directory) {
        List.of(
            "Map<String, String>[]", "Map<String, String>...", "List<Map<String, String>>",
            "Envelope<Map<String, String>>"
        ).forEach(
            type -> assertEquals(
                List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED), RequestMapSources.findings(
                    directory, RequestMapSources.source(RequestMapSources.ANNOTATION, type), true
                )
            )
        );
    }

    @Test
    void doesNotExemptOptionalMaps(@TempDir Path directory) {
        String source = RequestMapSources.source("@RequestParam(required = true)", "Optional<Map<String, String>>");
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void doesNotExemptAnUnrelatedMapImport(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
            .replace("import java.util.Map;", "import example.other.Map;");
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void doesNotExemptAnUnrelatedAnnotationImport(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
            .replace(RequestMapSources.REQUEST, "example.other.RequestParam");
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void rejectsMemberShadowing(@TempDir Path directory) {
        List.of("class Map<Key, Value> {}", "interface Map<Key, Value> {}", "@interface RequestParam {}").forEach(
            declaration -> {
                String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
                    .replace("final class Sample {", "final class Sample {\n" + declaration);
                assertEquals(
                    List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
                    RequestMapSources.findings(directory, source, true)
                );
            }
        );
    }

    @Test
    void rejectsLocalMapShadowing(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
            .replace("final class Sample {", "class Outside { void scope() { class Map<Key, Value> {} class Sample {")
            + "}}\n";
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void rejectsClassTypeParameterShadowing(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, "Map").replace(
            "class Sample", "class Sample<Map>"
        );
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void rejectsMethodTypeParameterShadowing(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, "Map").replace(
            "void read", "<Map> void read"
        );
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void rejectsUnknownInheritedMemberScopes(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
            .replace("class Sample", "class Sample implements Parent");
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void ignoresDeclarationsInUnrelatedScopes(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
            + "final class Elsewhere { interface Map<Key, Value> {} @interface RequestParam {} }\n";
        assertTrue(RequestMapSources.findings(directory, source, true).isEmpty());
    }

    @Test
    void rejectsEnclosingAnnotationShadowing(@TempDir Path directory) {
        String source = RequestMapSources.source(RequestMapSources.ANNOTATION, RequestMapSources.MAP)
            .replace("final class Sample {", "final class Outside { @interface RequestParam {} final class Sample {")
            + "}\n";
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void acceptsQualifiedNamesDespiteAnInheritedScope(@TempDir Path directory) {
        String source = RequestMapSources.source(
            "@" + RequestMapSources.REQUEST + "(required = true, defaultValue = \"ignored\")",
            "java.util.Map<String, String>"
        ).replace("class Sample", "class Sample inherits Parent").replace("inherits", "extends");
        assertTrue(RequestMapSources.findings(directory, source, true).isEmpty());
    }

    @Test
    void doesNotTreatOneEmptyAliasAsAnAbsentName(@TempDir Path directory) {
        String source = RequestMapSources.source(
            "@RequestParam(name = \"payload\", value = \"\")", RequestMapSources.MAP
        );
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void leavesConstantNamesOutsideTheException(@TempDir Path directory) {
        String source = RequestMapSources.source("@RequestParam(name = EMPTY)", RequestMapSources.MAP)
            .replace("final class Sample {", "final class Sample {\nstatic final String EMPTY = \"\";");
        assertEquals(List.of(RequestMapSources.REQUIRED), RequestMapSources.findings(directory, source, true));
    }
}
