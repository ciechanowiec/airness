package eu.ciechanowiec.airness.governance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;

/**
 * The fragments a link may name in a document: the anchors GitHub derives from its headings, and the ids its
 * HTML declares.
 *
 * <p>An anchor is the heading's text in lower case, stripped of every character that is not a letter, a mark, a
 * digit, a connector, a space or a hyphen, with each space turned into a hyphen. A heading whose anchor an
 * earlier heading already took gets a counter, so a second {@code Usage} answers to {@code usage-1}. That is
 * GitHub's own rule, and the one a reader's browser follows.
 */
@UtilityClass
final class MarkdownAnchors {

    private static final Pattern OUTSIDE = Pattern.compile("[^\\p{L}\\p{M}\\p{N}\\p{Pc} -]");
    private static final Pattern DECLARED = Pattern.compile("(?i)\\b(?:id|name)\\s*=\\s*[\"']([^\"']+)[\"']");

    /**
     * Every fragment the document answers to, in lower case.
     *
     * @param document a parsed document
     * @return the fragments
     */
    static Set<String> of(MarkdownDocument document) {
        Map<String, Integer> taken = new HashMap<>();
        List<String> headings = new ArrayList<>();
        for (Heading heading : document.nodes(Heading.class).toList()) {
            headings.add(numbered(taken, slug(MarkdownDocument.text(heading).strip())));
        }
        Stream<String> html = Stream.concat(
            document.nodes(HtmlBlock.class).map(HtmlBlock::getLiteral),
            document.nodes(HtmlInline.class).map(HtmlInline::getLiteral)
        ).flatMap(literal -> DECLARED.matcher(literal).results().map(result -> result.group(1)));
        return Stream.concat(headings.stream(), html)
            .map(fragment -> fragment.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The anchor GitHub derives from one heading's text, before any counter.
     *
     * @param title the heading's text
     * @return the anchor
     */
    static String slug(String title) {
        return OUTSIDE.matcher(title.toLowerCase(Locale.ROOT)).replaceAll("").replace(' ', '-');
    }

    private static String numbered(Map<String, Integer> taken, String slug) {
        int count = taken.getOrDefault(slug, 0);
        taken.put(slug, count + 1);
        return count == 0 ? slug : slug + "-" + count;
    }
}
