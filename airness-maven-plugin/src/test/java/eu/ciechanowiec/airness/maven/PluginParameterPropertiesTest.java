package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import eu.ciechanowiec.airness.governance.ManagedVersions;
import eu.ciechanowiec.airness.governance.ProjectProperties;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Every property a goal of this plugin reads in place of an unconfigured parameter is one a project file may
 * not declare, read from the descriptor this module's build writes.
 *
 * <p>The governance module classifies the properties of the third-party plugins from their descriptors, and
 * cannot read this one, which is built after it. A goal added here with a parameter bound to a property outside
 * the Airness namespace would otherwise be a setting any child could change unreported.
 */
class PluginParameterPropertiesTest {

    private static final Path DESCRIPTOR = Path.of("target", "classes", "META-INF", "maven", "plugin.xml");
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern BUILT_IN = Pattern.compile(
        "^(?:(?:project|session|settings|mojoExecution|mojo|plugin|reactorProjects|localRepository|basedir"
            + "|executedProject|repositorySystemSession|pluginDescriptor|env)(?:\\.|$)"
            + "|maven\\.(?:home|version|build\\.))"
    );

    @Test
    void keepsEveryPropertyAGoalReadsFromAProjectFile() {
        Set<String> properties = properties();
        assertFalse(properties.isEmpty(), "the descriptor binds parameters to properties, skipTests among them");
        Set<String> images = Set.copyOf(ManagedVersions.imageProperties());
        assertEquals(
            Set.of(),
            properties.stream()
                .filter(property -> !ProjectProperties.reserves(property) && !images.contains(property))
                .filter(property -> !ProjectProperties.ownedByProject(property))
                .collect(Collectors.toCollection(TreeSet::new)),
            "reserve these in ProjectProperties, or name them as the project's own and document them"
        );
    }

    @SneakyThrows
    private static Set<String> properties() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        Element root = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(Files.readAllBytes(DESCRIPTOR)))
            .getDocumentElement();
        return elements(root.getElementsByTagName("mojo"))
            .flatMap(PluginParameterPropertiesTest::propertiesOf)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Stream<String> propertiesOf(Element mojo) {
        Set<String> editable = elements(mojo.getElementsByTagName("parameter"))
            .filter(parameter -> !"false".equals(text(parameter, "editable")))
            .map(parameter -> text(parameter, "name"))
            .collect(Collectors.toUnmodifiableSet());
        return elements(mojo.getElementsByTagName("configuration"))
            .flatMap(PluginParameterPropertiesTest::children)
            .filter(value -> editable.contains(value.getTagName()))
            .map(value -> EXPRESSION.matcher(value.getTextContent().strip()))
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .filter(property -> !BUILT_IN.matcher(property).find());
    }

    private static String text(Element parent, String tag) {
        return children(parent)
            .filter(child -> tag.equals(child.getTagName()))
            .map(child -> child.getTextContent().strip())
            .findFirst()
            .orElse("");
    }

    private static Stream<Element> children(Node parent) {
        return elements(parent.getChildNodes());
    }

    private static Stream<Element> elements(NodeList nodes) {
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast);
    }
}
