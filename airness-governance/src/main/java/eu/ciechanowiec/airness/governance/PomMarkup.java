package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.experimental.UtilityClass;

/**
 * Reads a pom into its tags and comments, each with the line it starts on and the text before it.
 *
 * <p>The text between two pieces of markup is what a layout rule asks about: the line breaks it holds say whether
 * a tag starts its own line and how many blank lines stand before it. A tag starts on the line where the event
 * before it ended, which is the line the reader reports for that event.
 */
@UtilityClass
final class PomMarkup {

    private static final Set<Integer> TEXT = Set.of(
        XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE, XMLStreamConstants.CDATA
    );

    /**
     * Reads the markup of a pom in document order.
     *
     * @param content the pom as written
     * @return its tags and comments
     */
    static List<PomTag> read(String content) {
        XMLStreamReader events = Xml.events(content);
        try {
            List<PomTag> tags = walk(events);
            events.close();
            return tags;
        } catch (XMLStreamException exception) {
            throw new IllegalStateException("Could not parse XML", exception);
        }
    }

    private static List<PomTag> walk(XMLStreamReader events) throws XMLStreamException {
        List<PomTag> tags = new ArrayList<>();
        StringBuilder gap = new StringBuilder();
        int line = 1;
        int open = 0;
        while (events.hasNext()) {
            int event = events.next();
            Optional<PomTag.Kind> kind = kind(event);
            if (kind.isPresent()) {
                tags.add(new PomTag(kind.get(), name(events, event), kind.get().depthOf(open), line, gap.toString()));
                open = kind.get().openAfter(open);
                gap.setLength(0);
            }
            gap.append(text(events, event));
            line = events.getLocation().getLineNumber();
        }
        return List.copyOf(tags);
    }

    private static Optional<PomTag.Kind> kind(int event) {
        return switch (event) {
            case XMLStreamConstants.START_ELEMENT -> Optional.of(PomTag.Kind.OPEN);
            case XMLStreamConstants.END_ELEMENT -> Optional.of(PomTag.Kind.CLOSE);
            case XMLStreamConstants.COMMENT -> Optional.of(PomTag.Kind.COMMENT);
            default -> Optional.empty();
        };
    }

    private static String text(XMLStreamReader events, int event) {
        return TEXT.contains(event) ? events.getText() : "";
    }

    private static String name(XMLStreamReader events, int event) {
        return event == XMLStreamConstants.COMMENT ? "" : events.getLocalName();
    }
}
