package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

/**
 * The outline a document's headings draw, and the sections it divides the document into.
 *
 * <p>Only the headings directly below the document root take part, because a heading inside a list item or a
 * quotation belongs to that block rather than to the outline. The first heading may stand at any level: a file
 * that opens with the managed blocks Airness writes opens at the second level and leaves the first to the
 * project's own title below them. What the outline may not do is skip a level on the way down, since a
 * subsection two levels below its section belongs to a section nobody wrote.
 */
@UtilityClass
final class MarkdownOutline {

    private static final Pattern ATX = Pattern.compile("^ {0,3}#");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final String CLOSING = ".:;,";
    private static final int NO_PARENT = -1;

    /**
     * The headings that break the outline.
     *
     * @param document a parsed document
     * @return one offence per defect, each naming its line
     */
    static List<String> outline(MarkdownDocument document) {
        List<Heading> headings = headings(document);
        return IntStream.range(0, headings.size())
            .boxed()
            .flatMap(
                index -> defects(document, headings, index).map(
                    defect -> document.place(headings.get(index))
                        + ": " + defect
                )
            ).toList();
    }

    /**
     * The sections that hold nothing, and the ones that hold a single subsection.
     *
     * @param document a parsed document
     * @return one offence per section, each naming its heading's line
     */
    static List<String> sections(MarkdownDocument document) {
        List<Heading> headings = headings(document);
        Stream<String> empty = headings.stream()
            .filter(MarkdownOutline::empty)
            .map(heading -> document.place(heading) + ": the section '" + title(heading) + "' is empty");
        Stream<String> lone = IntStream.range(0, headings.size())
            .filter(index -> subsections(headings, index) == 1)
            .mapToObj(
                index -> document.place(headings.get(index)) + ": the section '" + title(headings.get(index))
                    + "' holds a single subsection"
            );
        return Stream.concat(empty, lone).toList();
    }

    private static Stream<String> defects(MarkdownDocument document, List<Heading> headings, int index) {
        Heading heading = headings.get(index);
        String title = title(heading);
        return Stream.of(
            jump(headings, index),
            ATX.matcher(document.lines().get(MarkdownDocument.line(heading) - 1)).find()
                ? "" : "the heading '" + title + "' is underlined; write it with #",
            title.isBlank() ? "the heading has no text" : "",
            closing(heading),
            repeated(headings, index) ? "the heading '" + title + "' repeats a sibling's" : ""
        ).filter(defect -> !defect.isEmpty());
    }

    private static String jump(List<Heading> headings, int index) {
        int level = headings.get(index).getLevel();
        int previous = index == 0 ? level : headings.get(index - 1).getLevel();
        return level > previous + 1 ? "the outline skips from level %d to %d".formatted(previous, level) : "";
    }

    private static String closing(Heading heading) {
        String title = title(heading);
        boolean textual = heading.getLastChild() instanceof Text;
        return textual && !title.isEmpty() && CLOSING.indexOf(title.charAt(title.length() - 1)) >= 0
            ? "the heading '" + title + "' ends with '" + title.charAt(title.length() - 1) + "'" : "";
    }

    private static boolean repeated(List<Heading> headings, int index) {
        int parent = parent(headings, index);
        String key = key(headings.get(index));
        return IntStream.range(0, index)
            .filter(earlier -> parent(headings, earlier) == parent)
            .filter(earlier -> headings.get(earlier).getLevel() == headings.get(index).getLevel())
            .anyMatch(earlier -> key(headings.get(earlier)).equals(key));
    }

    private static int subsections(List<Heading> headings, int index) {
        return (int) IntStream.range(index + 1, headings.size())
            .filter(later -> parent(headings, later) == index)
            .count();
    }

    private static int parent(List<Heading> headings, int index) {
        int level = headings.get(index).getLevel();
        return IntStream.iterate(index - 1, earlier -> earlier >= 0, earlier -> earlier - 1)
            .filter(earlier -> headings.get(earlier).getLevel() < level)
            .findFirst()
            .orElse(NO_PARENT);
    }

    private static boolean empty(Heading heading) {
        return Stream.iterate(heading.getNext(), Objects::nonNull, Node::getNext)
            .filter(block -> !comment(block))
            .findFirst()
            .map(block -> block instanceof Heading next && next.getLevel() <= heading.getLevel())
            .orElse(true);
    }

    private static boolean comment(Node block) {
        if (block instanceof HtmlBlock html) {
            String literal = html.getLiteral().strip();
            return literal.startsWith("<!--") && literal.endsWith("-->");
        }
        return false;
    }

    private static List<Heading> headings(MarkdownDocument document) {
        return document.blocks()
            .filter(Heading.class::isInstance)
            .map(Heading.class::cast)
            .toList();
    }

    private static String key(Heading heading) {
        return SPACES.matcher(title(heading)).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String title(Heading heading) {
        return MarkdownDocument.text(heading).strip();
    }
}
