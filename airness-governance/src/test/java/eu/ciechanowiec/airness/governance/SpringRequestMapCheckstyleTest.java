package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringRequestMapCheckstyleTest {

    @Test
    void acceptsStandardWholeRequestMaps(@TempDir Path directory) {
        List.of("Map<String, String>", "MultiValueMap<String, String>").forEach(
            type -> {
                String source = RequestMapSources.source("@RequestParam", type);
                assertTrue(RequestMapSources.findings(directory, source, true).isEmpty());
            }
        );
    }

    @Test
    void acceptsExplicitEmptyNames(@TempDir Path directory) {
        List.of("@RequestParam(\"\")", "@RequestParam(name = \"\")", "@RequestParam(value = \"\")").forEach(
            annotation -> assertTrue(
                RequestMapSources.findings(
                    directory, RequestMapSources.source(annotation, RequestMapSources.MAP), true
                )
                    .isEmpty()
            )
        );
    }

    @Test
    void leavesIgnoredMapSettingsToTheMapResolver(@TempDir Path directory) {
        List.of(
            "@RequestParam(required = true)", "@RequestParam(required = false)",
            "@RequestParam(defaultValue = \"ignored\")", "@RequestParam(required = true, defaultValue = \"ignored\")"
        ).forEach(
            annotation -> assertTrue(
                RequestMapSources.findings(
                    directory, RequestMapSources.source(annotation, RequestMapSources.MAP), true
                )
                    .isEmpty()
            )
        );
    }

    @Test
    void acceptsQualifiedMapTypes(@TempDir Path directory) {
        List.of("java.util.Map<String, String>", "org.springframework.util.MultiValueMap<String, String>").forEach(
            type -> {
                String source = RequestMapSources.source("@RequestParam", type)
                    .replace("import java.util.Map;", "").replace("import org.springframework.util.MultiValueMap;", "");
                assertTrue(RequestMapSources.findings(directory, source, true).isEmpty());
            }
        );
    }

    @Test
    void acceptsAQualifiedSpringAnnotation(@TempDir Path directory) {
        String annotation = "@" + RequestMapSources.REQUEST + "(required = true, defaultValue = \"ignored\")";
        String source = RequestMapSources.source(annotation, RequestMapSources.MAP)
            .replace("import " + RequestMapSources.REQUEST + ";", "");
        assertTrue(RequestMapSources.findings(directory, source, true).isEmpty());
    }

    @Test
    void preservesIndividualParameterFindings(@TempDir Path directory) {
        String source = RequestMapSources.source("@RequestParam", "String");
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void refusesAnEmptyNameOnAnIndividualValue(@TempDir Path directory) {
        List.of("@RequestParam(name = \"\", required = false)", "@RequestParam(value = \"\", required = false)")
            .forEach(
                annotation -> assertEquals(
                    List.of(RequestMapSources.NAMED), RequestMapSources.findings(
                        directory, RequestMapSources.source(annotation, "String"), true
                    )
                )
            );
    }

    @Test
    void preservesRequirednessForNamedMaps(@TempDir Path directory) {
        String source = RequestMapSources.source("@RequestParam(name = \"payload\")", RequestMapSources.MAP);
        assertEquals(List.of(RequestMapSources.REQUIRED), RequestMapSources.findings(directory, source, true));
    }

    @Test
    void preservesContradictorySettingsForNamedMaps(@TempDir Path directory) {
        String source = RequestMapSources.source(
            "@RequestParam(name = \"payload\", required = true, defaultValue = \"fallback\")", RequestMapSources.MAP
        );
        assertEquals(List.of(RequestMapSources.REQUIRED), RequestMapSources.findings(directory, source, true));
    }

    @Test
    void doesNotExemptOtherAnnotationsOnTheParameter(@TempDir Path directory) {
        String source = RequestMapSources.source("@RequestParam @RequestHeader", RequestMapSources.MAP);
        assertEquals(
            List.of(RequestMapSources.REQUIRED, RequestMapSources.NAMED),
            RequestMapSources.findings(directory, source, true)
        );
    }

    @Test
    void preservesThePlainJavaBoundary(@TempDir Path directory) {
        String source = RequestMapSources.source("@RequestParam", RequestMapSources.MAP);
        assertTrue(RequestMapSources.findings(directory, source, false).isEmpty());
    }
}
