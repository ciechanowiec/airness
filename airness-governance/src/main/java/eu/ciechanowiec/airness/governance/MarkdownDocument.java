package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * One Markdown file of the committable tree, parsed the way a renderer reads it.
 *
 * <p>The tables and frontmatter extensions are what keep the rules honest. Without them a table row parses as
 * a paragraph whose lines were cut, and the frontmatter that opens every skill parses as a thematic break
 * followed by an underlined heading, so two rules would accuse a well-formed file of defects it does not have.
 *
 * @param path  the file's path below the repository root, with forward slashes
 * @param text  the file's text with line endings normalized to a line feed
 * @param lines the text split into lines, the first at index zero
 * @param root  the parsed document, carrying the source span of every block and inline node
 */
record MarkdownDocument(String path, String text, List<String> lines, Node root) {

    private static final Parser PARSER = Parser.builder()
        .extensions(List.of(TablesExtension.create(), YamlFrontMatterExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .build();
    private static final String LF = "\n";

    /**
     * Copies the lines, so the document cannot change after it was parsed.
     *
     * @param path  the file's path below the repository root
     * @param text  the normalized text
     * @param lines the text split into lines
     * @param root  the parsed document
     */
    MarkdownDocument {
        lines = List.copyOf(lines);
    }

    /**
     * Parses one file.
     *
     * @param path the file's path below the repository root, with forward slashes
     * @param text the file's text
     * @return the parsed document
     */
    static MarkdownDocument parse(String path, String text) {
        String normalized = text.replace("\r\n", LF);
        return new MarkdownDocument(path, normalized, List.of(normalized.split(LF, -1)), PARSER.parse(normalized));
    }

    /**
     * Every node below the document root, in the order the source states them.
     *
     * @return the nodes, depth first
     */
    Stream<Node> nodes() {
        return descendants(this.root);
    }

    /**
     * Every node of one kind below the document root, in source order.
     *
     * @param <T>  the node type
     * @param kind the node class
     * @return the nodes of that kind
     */
    <T extends Node> Stream<T> nodes(Class<T> kind) {
        return this.nodes().filter(kind::isInstance).map(kind::cast);
    }

    /**
     * The blocks directly below the document root, in source order.
     *
     * @return the top-level blocks
     */
    Stream<Node> blocks() {
        return children(this.root);
    }

    /**
     * Where a node starts, as an offence prefix.
     *
     * @param node a node of this document
     * @return the path and the one-based line
     */
    String place(Node node) {
        return this.path + ":" + line(node);
    }

    /**
     * The source line a span covers, cut to the span.
     *
     * @param span a span of this document
     * @return the spanned text
     */
    String spanned(SourceSpan span) {
        String line = this.lines.get(span.getLineIndex());
        int start = Math.min(span.getColumnIndex(), line.length());
        return line.substring(start, Math.min(start + span.getLength(), line.length()));
    }

    /**
     * The one-based line a node starts on, taken from the nearest node that carries a span.
     *
     * @param node a parsed node
     * @return the line, or one where no node above it carries a span
     */
    static int line(Node node) {
        return Stream.iterate(node, Objects::nonNull, Node::getParent)
            .map(Node::getSourceSpans)
            .filter(spans -> !spans.isEmpty())
            .findFirst()
            .map(spans -> spans.getFirst().getLineIndex() + 1)
            .orElse(1);
    }

    /**
     * The text a node renders, with code written out and line breaks read as spaces.
     *
     * @param node a parsed node
     * @return the plain text
     */
    static String text(Node node) {
        return descendants(node).map(MarkdownDocument::literal).collect(Collectors.joining());
    }

    /**
     * The children of a node, in source order.
     *
     * @param node a parsed node
     * @return its direct children
     */
    static Stream<Node> children(Node node) {
        return Stream.iterate(node.getFirstChild(), Objects::nonNull, Node::getNext);
    }

    private static Stream<Node> descendants(Node node) {
        return children(node).flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }

    private static String literal(Node node) {
        if (node instanceof Text text) {
            return text.getLiteral();
        }
        return node instanceof Code code ? code.getLiteral() : spacing(node);
    }

    private static String spacing(Node node) {
        return node instanceof SoftLineBreak || node instanceof HardLineBreak ? " " : "";
    }
}
