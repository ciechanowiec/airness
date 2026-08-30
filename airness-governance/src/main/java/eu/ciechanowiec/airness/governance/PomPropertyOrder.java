package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Orders each Maven property block by its declarations' first concrete use in the same pom.
 */
@UtilityClass
final class PomPropertyOrder {

    private static final String PROPERTY_PREFIX = "${";
    private static final String PROPERTY_SUFFIX = "}";
    private static final String PROFILE = "profile";

    static List<String> problems(Element root) {
        List<Node> blocks = DeclaredCoordinates.propertyBlocks(root).toList();
        return blocks.stream()
            .flatMap(block -> problem(root, block, blocks).stream())
            .toList();
    }

    private static Optional<String> problem(Element root, Node block, Collection<Node> blocks) {
        List<String> actual = propertyNames(block);
        List<String> expected = new ArrayList<>(actual);
        Map<String, Integer> uses = firstUses(root, actual, blocks);
        Comparator<String> byUse = Comparator.comparing(
            uses::get, Comparator.nullsFirst(Comparator.naturalOrder())
        );
        expected.sort(byUse.thenComparing(Comparator.naturalOrder()));
        if (actual.equals(expected)) {
            return Optional.empty();
        }
        return Optional.of(
            "Order " + owner(block) + " properties as " + String.join(", ", expected)
                + "; properties not locally referenced come first alphabetically, then properties follow first use"
        );
    }

    private static Map<String, Integer> firstUses(
        Element root, Collection<String> names, Collection<Node> blocks
    ) {
        List<Element> sites = Stream.concat(Stream.of(root), elements(root))
            .filter(element -> !inside(element, blocks))
            .toList();
        return names.stream()
            .flatMap(
                name -> IntStream.range(0, sites.size())
                    .filter(index -> references(sites.get(index), name))
                    .findFirst()
                    .stream()
                    .mapToObj(index -> Map.entry(name, index))
            )
            .collect(
                // A property block may declare one name twice, which is well-formed XML that Maven
                // accepts by taking the last value. Keeping the first use of such a name reports the
                // ordering the reader sees, where refusing the duplicate key would end every model rule
                // at once over a fault none of them is about.
                Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first)
            );
    }

    private static boolean references(Node site, String name) {
        String reference = PROPERTY_PREFIX + name + PROPERTY_SUFFIX;
        NamedNodeMap attributes = site.getAttributes();
        boolean attribute = IntStream.range(0, attributes.getLength())
            .mapToObj(attributes::item)
            .map(Node::getNodeValue)
            .anyMatch(value -> value.contains(reference));
        return attribute || nodes(site.getChildNodes())
            .filter(PomPropertyOrder::text)
            .map(Node::getNodeValue)
            .anyMatch(value -> value.contains(reference));
    }

    private static boolean text(Node node) {
        return node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE;
    }

    private static boolean inside(@Nullable Node node, Collection<Node> blocks) {
        return Optional.ofNullable(node)
            .map(held -> propertyBlock(held, blocks) || inside(held.getParentNode(), blocks))
            .orElse(false);
    }

    private static boolean propertyBlock(Node node, Collection<Node> blocks) {
        return blocks.contains(node);
    }

    private static List<String> propertyNames(Node block) {
        return nodes(block.getChildNodes())
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .map(Element::getTagName)
            .toList();
    }

    private static String owner(Node block) {
        Node owner = block.getParentNode();
        if (owner instanceof Element element && PROFILE.equals(element.getTagName())) {
            return "profile " + Xml.text(element, "id").orElse("<unnamed>");
        }
        return "project";
    }

    private static Stream<Element> elements(Element root) {
        return nodes(root.getElementsByTagName("*")).map(Element.class::cast);
    }

    private static Stream<Node> nodes(NodeList nodes) {
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item);
    }
}
