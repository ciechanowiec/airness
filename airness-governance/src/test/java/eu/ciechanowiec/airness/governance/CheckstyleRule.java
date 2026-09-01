package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@UtilityClass
final class CheckstyleRule {

    private static final String CONFIGURATION
        = "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/checkstyle.xml";
    private static final String DOCTYPE = "<!DOCTYPE";

    @SneakyThrows
    static int findings(Path directory, String source, String rule, String queryMarker) {
        DefaultConfiguration ruleConfiguration = new DefaultConfiguration("MatchXpath");
        ruleConfiguration.addProperty("id", rule);
        ruleConfiguration.addProperty("query", query(rule, queryMarker));
        DefaultConfiguration walkerConfiguration = new DefaultConfiguration("TreeWalker");
        walkerConfiguration.addChild(ruleConfiguration);
        DefaultConfiguration checkerConfiguration = new DefaultConfiguration("Checker");
        checkerConfiguration.addChild(walkerConfiguration);
        Checker checker = new Checker();
        checker.setModuleClassLoader(CheckstyleRule.class.getClassLoader());
        try {
            checker.configure(checkerConfiguration);
            Path file = Files.writeString(directory.resolve("Sample.java"), source);
            return checker.process(List.of(file.toFile()));
        } finally {
            checker.destroy();
        }
    }

    private static String query(String rule, String queryMarker) {
        return ProjectFiles.descendants(configuration(), "module")
            .filter(module -> "MatchXpath".equals(module.getAttribute("name")))
            .filter(module -> rule.equals(property(module, "id")))
            .map(module -> property(module, "query"))
            .filter(query -> query.contains(queryMarker))
            .reduce(
                (_, _) -> {
                    throw new IllegalStateException("Several queries matched " + rule + " and " + queryMarker);
                }
            )
            .orElseThrow(() -> new IllegalStateException("No query matched " + rule + " and " + queryMarker));
    }

    @SneakyThrows
    private static Document configuration() {
        Path path = SelfModules.repository().resolve(CONFIGURATION);
        String content = Files.readString(path);
        int declaration = content.indexOf(DOCTYPE);
        int end = content.indexOf('>', declaration) + 1;
        String withoutDoctype = content.substring(0, declaration) + content.substring(end);
        return Xml.parse(withoutDoctype);
    }

    private static String property(Element module, String name) {
        return Xml.children(module, "property").stream()
            .filter(property -> name.equals(property.getAttribute("name")))
            .map(property -> property.getAttribute("value"))
            .findFirst()
            .orElse("");
    }
}
