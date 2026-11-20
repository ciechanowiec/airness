package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;

/**
 * The shape of a document's code, lists and paragraphs.
 *
 * <p>A paragraph is one line of source because that is what it renders as: a break inside it draws as a space,
 * so the break carries nothing a reader sees and costs every later edit a rewrap. A line ending in a backslash
 * is the one break Markdown draws as a break, written where the author can see it, and it stays allowed. Two
 * trailing spaces draw the same break invisibly, which is why they count as a cut line rather than as a break.
 */
@UtilityClass
final class MarkdownBlocks {

    private static final int DEEPEST = 2;
    private static final char BACKSLASH = '\\';

    /**
     * The code blocks that are indented or name no language.
     *
     * @param document a parsed document
     * @return one offence per block
     */
    static List<String> code(MarkdownDocument document) {
        Stream<String> indented = document.nodes(IndentedCodeBlock.class)
            .map(block -> document.place(block) + ": the code block is indented; fence it");
        Stream<String> unnamed = document.nodes(FencedCodeBlock.class)
            .filter(block -> Objects.requireNonNullElse(block.getInfo(), "").isBlank())
            .map(block -> document.place(block) + ": the code fence names no language");
        return Stream.concat(indented, unnamed).toList();
    }

    /**
     * The lists nested past two levels, and the lists of a single item.
     *
     * @param document a parsed document
     * @return one offence per list
     */
    static List<String> lists(MarkdownDocument document) {
        Stream<String> deep = document.nodes(ListBlock.class)
            .filter(list -> depth(list) == DEEPEST + 1)
            .map(list -> document.place(list) + ": the list is nested three levels deep");
        Stream<String> single = document.nodes(ListBlock.class)
            .filter(list -> MarkdownDocument.children(list).filter(ListItem.class::isInstance).count() == 1)
            .map(list -> document.place(list) + ": the list holds a single item");
        return Stream.concat(deep, single).toList();
    }

    /**
     * The paragraphs written over more than one source line.
     *
     * @param document a parsed document
     * @return one offence per paragraph, counting the breaks to join
     */
    static List<String> lines(MarkdownDocument document) {
        return document.nodes(Paragraph.class)
            .filter(paragraph -> cuts(document, paragraph) > 0)
            .map(
                paragraph -> document.place(paragraph) + ": the paragraph is cut across lines (join %d line break(s)"
                    .formatted(cuts(document, paragraph)) + " into spaces)"
            ).toList();
    }

    private static long cuts(MarkdownDocument document, Node paragraph) {
        List<SourceSpan> spans = paragraph.getSourceSpans();
        return IntStream.range(0, spans.size() - 1)
            .filter(index -> !breaks(document.spanned(spans.get(index))))
            .count();
    }

    private static boolean breaks(CharSequence line) {
        int backslashes = (int) IntStream.iterate(line.length() - 1, index -> index >= 0, index -> index - 1)
            .takeWhile(index -> line.charAt(index) == BACKSLASH)
            .count();
        return backslashes % 2 != 0;
    }

    private static int depth(Node list) {
        return (int) Stream.iterate(list, Objects::nonNull, Node::getParent)
            .filter(ListBlock.class::isInstance)
            .count();
    }
}
