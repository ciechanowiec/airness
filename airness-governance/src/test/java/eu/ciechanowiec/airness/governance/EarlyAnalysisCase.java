package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

record EarlyAnalysisCase(
    String tool, String rule, String identifier, String source, int expected, int originalExpected
) {

    @SneakyThrows
    static List<EarlyAnalysisCase> all() {
        Path inventory = SelfModules.repository().resolve("airness-governance/src/test/resources/early-analysis.xml");
        return ProjectFiles.descendants(Xml.parse(Files.readString(inventory)), "rule")
            .flatMap(rule -> Xml.children(rule, "case").stream().map(sample -> read(rule, sample)))
            .toList();
    }

    private static EarlyAnalysisCase read(Element rule, Element sample) {
        return new EarlyAnalysisCase(
            rule.getAttribute("tool"),
            rule.getAttribute("name"),
            rule.getAttribute("id"),
            ProjectFiles.child(sample, "source").getTextContent(),
            Integer.parseInt(sample.getAttribute("findings")),
            Integer.parseInt(
                sample.hasAttribute("original")
                    ? sample.getAttribute("original") : sample.getAttribute("findings")
            )
        );
    }
}
