package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A small, hardened XML reader shared by the pom parsers and the Maven Central metadata parser. It
 * disables document type declarations and external entities so a hostile input cannot reach the
 * network or the filesystem, and it exposes only the direct-child navigation the callers need. A reader
 * that judges how a document is laid out reads it as a stream of events instead, because a DOM keeps
 * neither the line a node stands on nor, reliably, the whitespace between nodes.
 */
@UtilityClass
final class Xml {

    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GENERAL = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER = "http://xml.org/sax/features/external-parameter-entities";

    static XMLStreamReader events(String content) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        try {
            return factory.createXMLStreamReader(new StringReader(content));
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Could not parse XML", exception);
        }
    }

    static Document parse(String content) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(DISALLOW_DOCTYPE, true);
            factory.setFeature(EXTERNAL_GENERAL, false);
            factory.setFeature(EXTERNAL_PARAMETER, false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(content)));
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalStateException("Could not parse XML", exception);
        }
    }

    static List<Element> children(Node parent, String tag) {
        NodeList nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .filter(element -> element.getTagName().equals(tag))
            .toList();
    }

    static Optional<Element> firstChild(Node parent, String tag) {
        return children(parent, tag).stream().findFirst();
    }

    static Optional<String> text(Node parent, String tag) {
        return firstChild(parent, tag)
            .map(element -> Objects.requireNonNull(element.getTextContent()).strip());
    }

    static String idTextOrEmpty(Node parent) {
        List<Element> found = children(parent, "id");
        return found.isEmpty()
            ? ""
            : Objects.requireNonNull(found.getFirst().getTextContent()).strip();
    }
}
