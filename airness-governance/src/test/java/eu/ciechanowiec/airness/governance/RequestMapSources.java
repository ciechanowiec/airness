package eu.ciechanowiec.airness.governance;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
final class RequestMapSources {

    static final String NAMED = "AirnessSpringWebParameterIsNamed";
    static final String REQUIRED = "AirnessSpringWebParameterDeclaresRequiredness";
    static final String ANNOTATION = "@RequestParam";
    static final String MAP = "Map<String, String>";
    static final String REQUEST = "org.springframework.web.bind.annotation.RequestParam";

    static String source(String annotation, String type) {
        return """
            package example;
            import java.util.Map;
            import org.springframework.util.MultiValueMap;
            import org.springframework.web.bind.annotation.RequestParam;
            final class Sample {
                void read(%s %s values) {}
            }
            """.formatted(annotation, type);
    }

    static List<String> findings(Path directory, String source, boolean spring) {
        return CheckstyleConfigurationTest.inspect(
            directory, new Fixture("Sample.java", source, NAMED, 1), spring
        ).stream().map(Finding::rule).filter(rule -> NAMED.equals(rule) || REQUIRED.equals(rule)).sorted().toList();
    }
}
