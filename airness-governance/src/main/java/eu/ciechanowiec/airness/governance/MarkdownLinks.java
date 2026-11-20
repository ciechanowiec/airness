package eu.ciechanowiec.airness.governance;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;

/**
 * The links of the Markdown documents in one committable tree, and whether each leads where it says.
 *
 * <p>A relative destination is read the way GitHub reads it: against the document's directory, or against the
 * repository root when it starts with a slash. It must name a committable file or a directory holding one,
 * spelled with the case that file has, because a link to a file nobody committed works on the machine that has
 * it and nowhere else. A fragment naming a Markdown document must be one of that document's
 * {@linkplain MarkdownAnchors anchors}. A destination with a scheme, or one starting with two slashes, leaves
 * the repository and is not followed, since whether a page elsewhere exists is not a fact about this tree.
 *
 * <p>A reference link whose label has no definition is not parsed as a link at all, so it stays in the text as
 * brackets around words, which is what this reads for. A definition no link uses is dead text.
 */
final class MarkdownLinks {

    private static final Pattern SCHEME = Pattern.compile("^(?:[A-Za-z][A-Za-z0-9+.-]*:|//)");
    private static final Pattern REFERENCE = Pattern.compile("\\[([^\\[\\]\\x00]+)]\\[([^\\[\\]\\x00]*)]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern TRAILING = Pattern.compile("[.:!]+$");
    private static final Set<String> VAGUE = Set.of(
        "here", "click here", "this", "this link", "link", "read more", "more"
    );
    private static final String BARRIER = Character.toString(0);
    private static final String MARKDOWN = ".md";
    private static final String PARENT = "..";
    private static final char FRAGMENT = '#';
    private static final char QUERY = '?';
    private static final char SLASH = '/';
    private static final Pattern ESCAPES = Pattern.compile("(?:%[0-9A-Fa-f]{2})+");
    private static final int ESCAPE = 3;
    private static final int HEX = 16;

    private final Set<String> files;
    private final Set<String> directories;
    private final Map<String, Set<String>> anchors;

    /**
     * Names what a destination may resolve to.
     *
     * @param files   every committable file, by its path below the root with forward slashes
     * @param anchors the fragments of every Markdown document, by the same path
     */
    MarkdownLinks(Set<String> files, Map<String, Set<String>> anchors) {
        this.files = Set.copyOf(files);
        this.directories = files.stream().flatMap(MarkdownLinks::ancestors).collect(Collectors.toUnmodifiableSet());
        this.anchors = Map.copyOf(anchors);
    }

    /**
     * The links and definitions of a document that lead nowhere.
     *
     * @param document a parsed document
     * @return one offence per link or definition
     */
    List<String> broken(MarkdownDocument document) {
        Stream<String> destinations = Stream.concat(
            document.nodes(Link.class).map(link -> new Destination(link, link.getDestination())),
            document.nodes(Image.class).map(image -> new Destination(image, image.getDestination()))
        ).flatMap(
            destination -> this.unresolved(document, destination.target()).stream()
                .map(reason -> document.place(destination.node()) + ": " + reason)
        );
        Stream<String> undefined = document.nodes()
            .filter(node -> MarkdownDocument.children(node).anyMatch(Text.class::isInstance))
            .flatMap(
                node -> REFERENCE.matcher(prose(node)).results()
                    .map(
                        result -> document.place(node) + ": the reference link '" + label(
                            result.group(1),
                            result.group(2)
                        ) + "' has no definition"
                    )
            );
        return Stream.of(destinations, undefined, unused(document)).flatMap(stream -> stream).distinct().toList();
    }

    /**
     * The links whose text does not name where they lead, and the images that say nothing of what they show.
     *
     * @param document a parsed document
     * @return one offence per link or image
     */
    static List<String> wording(MarkdownDocument document) {
        Stream<String> links = document.nodes(Link.class)
            .filter(link -> MarkdownDocument.children(link).noneMatch(Image.class::isInstance))
            .filter(link -> VAGUE.contains(spoken(link)) || spoken(link).isEmpty())
            .map(
                link -> document.place(link) + (spoken(link).isEmpty() ? ": the link has no text"
                    : ": the link text '" + spoken(link) + "' does not name its destination")
            );
        Stream<String> images = document.nodes(Image.class)
            .filter(image -> MarkdownDocument.text(image).isBlank())
            .map(image -> document.place(image) + ": the image has no alt text");
        return Stream.concat(links, images).toList();
    }

    private Optional<String> unresolved(MarkdownDocument document, String destination) {
        if (destination.isBlank()) {
            return Optional.of("the link has no destination");
        }
        return SCHEME.matcher(destination).find() ? Optional.empty() : this.local(document, destination);
    }

    private Optional<String> local(MarkdownDocument document, String destination) {
        Reference reference = Reference.of(destination);
        return resolved(document.path(), reference.file())
            .map(path -> this.missing(destination, path, reference.fragment()))
            .orElseGet(() -> Optional.of("'" + destination + "' leads outside the repository"));
    }

    private Optional<String> missing(String destination, String path, String fragment) {
        if (!path.isEmpty() && !this.files.contains(path) && !this.directories.contains(path)) {
            return Optional.of("'" + destination + "' names no committed file");
        }
        return this.anchored(path, fragment) ? Optional.empty()
            : Optional.of("'" + destination + "' names no heading of " + path);
    }

    private boolean anchored(String path, String fragment) {
        boolean read = !fragment.isEmpty() && path.endsWith(MARKDOWN) && this.files.contains(path);
        return !read || this.anchors.getOrDefault(path, Set.of()).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static Stream<String> unused(MarkdownDocument document) {
        String source = SPACES.matcher(referable(document)).replaceAll(" ").toLowerCase(Locale.ROOT);
        return document.nodes(LinkReferenceDefinition.class)
            .filter(definition -> !source.contains("[" + normal(definition.getLabel()) + "]"))
            .map(
                definition -> document.place(definition) + ": the definition '" + definition.getLabel()
                    + "' is never used"
            );
    }

    private static String referable(MarkdownDocument document) {
        Set<Integer> hidden = document.nodes()
            .filter(
                node -> node instanceof LinkReferenceDefinition || node instanceof FencedCodeBlock
                    || node instanceof IndentedCodeBlock
            ).flatMap(node -> node.getSourceSpans().stream())
            .map(SourceSpan::getLineIndex)
            .collect(Collectors.toUnmodifiableSet());
        return IntStream.range(0, document.lines().size())
            .filter(index -> !hidden.contains(index))
            .mapToObj(document.lines()::get)
            .collect(Collectors.joining(" "));
    }

    private static String prose(Node node) {
        return MarkdownDocument.children(node)
            .map(child -> child instanceof Text text ? text.getLiteral() : barrier(child))
            .collect(Collectors.joining());
    }

    private static String barrier(Node node) {
        return node instanceof Code || node instanceof HtmlInline || node instanceof Link || node instanceof Image
            ? BARRIER : "";
    }

    private static String label(String text, String label) {
        return label.isBlank() ? text : label;
    }

    private static String spoken(Node link) {
        return TRAILING.matcher(MarkdownDocument.text(link).strip().toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String normal(String label) {
        return SPACES.matcher(label.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static Optional<String> resolved(String document, String file) {
        if (file.isEmpty()) {
            return Optional.of(document);
        }
        boolean rooted = file.charAt(0) == SLASH;
        Path base = rooted ? Path.of("") : Optional.ofNullable(Path.of(document).getParent()).orElse(Path.of(""));
        Path target = base.resolve(rooted ? file.substring(1) : file).normalize();
        return target.startsWith(PARENT) ? Optional.empty()
            : Optional.of(target.toString().replace(File.separatorChar, SLASH));
    }

    private static Stream<String> ancestors(String file) {
        return Stream.iterate(file.lastIndexOf(SLASH), index -> index > 0, index -> file.lastIndexOf(SLASH, index - 1))
            .map(index -> file.substring(0, index));
    }

    private static String decoded(String encoded) {
        return ESCAPES.matcher(encoded).replaceAll(escapes -> Matcher.quoteReplacement(utf8(escapes.group())));
    }

    private static String utf8(String escapes) {
        byte[] bytes = new byte[escapes.length() / ESCAPE];
        for (int index = 0; index < bytes.length; index++) {
            int start = index * ESCAPE + 1;
            bytes[index] = (byte) Integer.parseInt(escapes.substring(start, start + ESCAPE - 1), HEX);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * A local destination split into the file it names and the fragment after it, each percent-decoded, with any
     * query left out.
     *
     * @param file     the file, empty where the destination names only a fragment
     * @param fragment the fragment, empty where the destination names none
     */
    private record Reference(String file, String fragment) {

        static Reference of(String destination) {
            int hash = destination.indexOf(FRAGMENT);
            String location = hash < 0 ? destination : destination.substring(0, hash);
            int query = location.indexOf(QUERY);
            return new Reference(
                decoded(query < 0 ? location : location.substring(0, query)),
                hash < 0 ? "" : decoded(destination.substring(hash + 1))
            );
        }
    }

    /**
     * A link or an image, with the destination it names.
     *
     * @param node   the link or image
     * @param target its destination
     */
    private record Destination(Node node, String target) {
    }
}
