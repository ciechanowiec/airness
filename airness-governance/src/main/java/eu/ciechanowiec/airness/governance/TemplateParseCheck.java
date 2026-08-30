package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.attoparser.MarkupParser;
import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;
import org.attoparser.discard.DiscardMarkupHandler;

/**
 * Reads every markup resource a module ships and reports the ones no engine could read.
 *
 * <p>A template is a program whose output is HTML, so nothing about it is known until something reads
 * it. A fragment no page calls yet is read by nothing at all, and the session that finally calls it is
 * where a mistyped tag surfaces, far from whoever wrote it. This check reads all of them on every
 * build instead.
 *
 * <p>What counts as readable is decided by the parser the engines themselves use, at the balancing its
 * HTML configuration sets, rather than by the HTML specification. That is deliberate: a specification
 * knows nothing of the attributes and elements a templating dialect adds, so judging a template
 * against one reports the dialect rather than a defect. Asking the engine's own parser asks the only
 * question with an answer, which is whether the file can be read at all.
 *
 * <p>Which files those are is answered by {@link MarkupResources}, so that this check and every other
 * check over templates read one set rather than two that can drift apart.
 */
public final class TemplateParseCheck {

    private static final String HEADLINE = "Markup resources that no template engine could read";

    private final Path root;
    private final List<Path> files;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateParseCheck(Path root, Collection<Path> resourceRoots) {
        this.root = root;
        this.files = MarkupResources.of(root, resourceRoots);
    }

    /**
     * How many markup resources the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.files.size();
    }

    /**
     * The one markup rule and every file that breaks it.
     *
     * @return one verdict, carrying a line per unreadable file
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.unreadable()));
    }

    private List<String> unreadable() {
        return this.files.stream()
            .map(this::refusal)
            .flatMap(Optional::stream)
            .sorted()
            .toList();
    }

    private Optional<String> refusal(Path file) {
        return Repository.readText(file).flatMap(text -> parse(this.root.relativize(file), text));
    }

    private static Optional<String> parse(Path named, String text) {
        try {
            new MarkupParser(ParseConfiguration.htmlConfiguration()).parse(text, new DiscardMarkupHandler());
            return Optional.empty();
        } catch (ParseException exception) {
            return Optional.of("%s: %s".formatted(named, exception.getMessage()));
        }
    }
}
