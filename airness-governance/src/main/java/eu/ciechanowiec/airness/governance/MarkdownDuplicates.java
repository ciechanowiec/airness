package eu.ciechanowiec.airness.governance;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.commonmark.node.Paragraph;

/**
 * The Markdown text a repository states in more than one place.
 *
 * <p>A copy is the common way a document sprawls: a rule is written into a second file rather than linked from
 * the first, and the two drift apart from the next edit on. Two readings find it. A file whose project-owned
 * text equals an earlier file's is a copy as a whole, and is named once rather than paragraph by paragraph. A
 * paragraph of at least {@value #SHORTEST} characters whose rendered text, in lower case and with its spaces
 * collapsed, appears again anywhere in the repository is a copy of the first place it appears, which is what
 * catches a paragraph pasted from one file into another.
 *
 * <p>The blocks Airness manages are left out of both readings. Airness writes the same block into every node
 * project of a repository by design, and a project can neither change one nor remove it. The agent-rules block
 * {@code next dev} writes into the {@code AGENTS.md} of each Next.js application is left out for the same reason:
 * Next.js writes it verbatim and adds it again once removed, so two applications of one repository always share
 * it. Only that block, named by its exact markers, is left out, so no other marked text escapes the comparison.
 */
@UtilityClass
final class MarkdownDuplicates {

    private static final int SHORTEST = 80;
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern FRAMEWORK_BLOCK = Pattern.compile(
        "(?m)^<!-- BEGIN:nextjs-agent-rules -->[ \\t]*\\n(?:.*\\n)*?"
            + "<!-- END:nextjs-agent-rules -->[ \\t]*(?:\\n|\\z)"
    );

    /**
     * The copied files and paragraphs.
     *
     * @param documents every document of the repository, in path order
     * @return one offence per copy, naming the original it repeats
     */
    static List<String> copies(List<MarkdownDocument> documents) {
        Map<String, List<String>> files = grouped(
            documents.stream().filter(document -> !owned(document).isBlank()),
            MarkdownDuplicates::owned, MarkdownDocument::path
        );
        Set<String> copiedFiles = files.values().stream()
            .flatMap(paths -> paths.stream().skip(1))
            .collect(Collectors.toUnmodifiableSet());
        Map<String, List<String>> paragraphs = grouped(
            documents.stream()
                .filter(document -> !copiedFiles.contains(document.path()))
                .flatMap(MarkdownDuplicates::paragraphs),
            Place::text, Place::where
        );
        return Stream.concat(repeats(files, "the file copies "), repeats(paragraphs, "the paragraph copies "))
            .toList();
    }

    private static <T> Map<String, List<String>> grouped(
        Stream<T> items, Function<T, String> key, Function<T, String> where
    ) {
        return items.collect(
            Collectors.groupingBy(key, LinkedHashMap::new, Collectors.mapping(where, Collectors.toList()))
        );
    }

    private static Stream<String> repeats(Map<String, List<String>> groups, String verb) {
        return groups.values().stream()
            .filter(places -> places.size() > 1)
            .flatMap(places -> places.stream().skip(1).map(place -> place + ": " + verb + places.getFirst()));
    }

    private static Stream<Place> paragraphs(MarkdownDocument document) {
        BitSet managed = ManagedSections.managedLines(document.text());
        managed.or(ManagedSections.blockLines(FRAMEWORK_BLOCK, document.text()));
        return document.nodes(Paragraph.class)
            .filter(paragraph -> !managed.get(MarkdownDocument.line(paragraph) - 1))
            .map(paragraph -> new Place(document.place(paragraph), normal(MarkdownDocument.text(paragraph))))
            .filter(place -> place.text().length() >= SHORTEST);
    }

    private static String owned(MarkdownDocument document) {
        String project = ManagedSections.projectPart(document.text()).orElse(document.text());
        return FRAMEWORK_BLOCK.matcher(project).replaceAll("").strip();
    }

    private static String normal(String text) {
        return SPACES.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * A paragraph long enough to judge, with where it starts.
     *
     * @param where the path and line of the paragraph
     * @param text  its rendered text, normalized
     */
    private record Place(String where, String text) {
    }
}
