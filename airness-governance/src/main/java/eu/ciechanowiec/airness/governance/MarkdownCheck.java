package eu.ciechanowiec.airness.governance;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every Markdown document of the committable tree holds to the structure a reader, human or agent, can rely on:
 * links that lead somewhere, an outline without gaps, sections with something in them, fenced and labelled
 * code, shallow lists, no text stated twice, instruction files within a budget, one line per paragraph and link
 * text that names its destination.
 *
 * <p>Each rule reads what a renderer draws, through the CommonMark parser with GitHub's tables and a leading
 * YAML frontmatter block, rather than what a line of source looks like. Code is never read as prose: a fenced or
 * indented block, a code span, HTML and frontmatter carry no paragraph, heading or link. A symbolic link is
 * passed over, since it is the one form a second copy of a document can take without being a copy.
 */
public final class MarkdownCheck {

    private static final String LINKS = "Markdown links that lead nowhere (point each at a committed file, a"
        + " heading of one or a definition, or remove it)";
    private static final String OUTLINE = "Markdown headings that break the outline (write each with #, one level"
        + " below its section, with text, no closing punctuation and a title its siblings do not share)";
    private static final String SECTIONS = "Markdown sections that are empty or hold a single subsection (give the"
        + " section its text, or merge the lone subsection into it)";
    private static final String CODE = "Markdown code that is indented or names no language (write a fenced block"
        + " whose opening fence names the language, text for plain output)";
    private static final String LISTS = "Markdown lists nested three levels deep or holding a single item (flatten"
        + " the list, or write the single item as a sentence)";
    private static final String COPIES = "Markdown text stated in more than one place (keep one copy and link to"
        + " it from the others, or replace a whole copied file with a symbolic link)";
    private static final String BUDGETS = "Agent instruction files over their word budget (remove what the"
        + " guideline or the code already states, and move a skill's detail into a reference file it links to)";
    private static final String LINES = "Markdown paragraphs cut across lines (join each paragraph onto one line;"
        + " end a line with a backslash only where the rendered text breaks)";
    private static final String WORDING = "Markdown links and images that do not say what they show (name the"
        + " destination in the link text, and describe each image in its alt text)";
    private static final String MARKDOWN = ".md";

    private final List<MarkdownDocument> documents;
    private final MarkdownLinks links;

    /**
     * Names the working tree whose Markdown documents are read.
     *
     * @param root the working tree root
     */
    public MarkdownCheck(Path root) {
        List<Path> files = Repository.trackedFiles(root);
        this.documents = files.stream()
            .filter(file -> String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT).endsWith(MARKDOWN))
            .filter(file -> !Files.isSymbolicLink(file))
            .sorted()
            .map(file -> MarkdownDocument.parse(slashed(root.relativize(file)), Repository.readText(file).orElse("")))
            .toList();
        Map<String, Set<String>> anchors = this.documents.stream()
            .collect(Collectors.toUnmodifiableMap(MarkdownDocument::path, MarkdownAnchors::of));
        this.links = new MarkdownLinks(
            files.stream().map(file -> slashed(root.relativize(file))).collect(Collectors.toUnmodifiableSet()),
            anchors
        );
    }

    /**
     * How many Markdown documents the check reads.
     *
     * @return the document count
     */
    public int scanned() {
        return this.documents.size();
    }

    /**
     * The verdict of every rule, each offence naming its file and line.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            this.each(LINKS, this.links::broken),
            this.each(OUTLINE, MarkdownOutline::outline),
            this.each(SECTIONS, MarkdownOutline::sections),
            this.each(CODE, MarkdownBlocks::code),
            this.each(LISTS, MarkdownBlocks::lists),
            new Findings(COPIES, MarkdownDuplicates.copies(this.documents)),
            this.each(BUDGETS, MarkdownBudgets::excess),
            this.each(LINES, MarkdownBlocks::lines),
            this.each(WORDING, MarkdownLinks::wording)
        );
    }

    private Findings each(String headline, Function<MarkdownDocument, List<String>> rule) {
        return new Findings(
            headline, this.documents.stream().flatMap(document -> rule.apply(document).stream())
                .toList()
        );
    }

    private static String slashed(Path relative) {
        return relative.toString().replace(File.separatorChar, '/');
    }
}
