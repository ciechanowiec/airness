package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads every stylesheet a module ships and reports the selectors whose space is a combinator.
 *
 * <p>A selector too long for a line is wrapped like any other line, and the wrap changes what it
 * selects: the break is a space, and a space between two parts of a selector means one is inside the
 * other. A rule about links becomes a rule about whatever a link contains, and it goes on being valid
 * the whole time.
 *
 * <p>Nothing else in a build says so. The stylesheet has no syntax error, the formatter accepts it and
 * joins the halves back onto one line keeping the space, which leaves the mistake reading like a
 * decision, and a page drawn from it is a page that renders. Only the thing the rule was aimed at goes
 * on looking as it did before anybody wrote the rule.
 *
 * <p>One shape of the mistake is reported rather than every space before a pseudo-class. A space
 * before a group is ordinary and means what it says, so a table naming its own cells is left alone.
 * What is reported is a chain of refusals with a space in the middle of it, which is the shape a line
 * budget makes and which nobody writes on purpose.
 */
public final class StylesheetSelectorCheck {

    private static final String HEADLINE
        = "Selectors joining two refusals with a space, which reads as a combinator rather than a wrap";

    private final Path root;

    private final List<Path> files;

    /**
     * Creates a check over the stylesheets one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public StylesheetSelectorCheck(Path root, Collection<Path> resourceRoots) {
        this.root = root;
        this.files = StylesheetResources.of(root, resourceRoots);
    }

    /**
     * How many stylesheets the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.files.size();
    }

    /**
     * What the check found, as one verdict.
     *
     * @return the verdict, holding no offence where every selector means what it names
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.files.stream().flatMap(this::offencesIn).toList()));
    }

    private Stream<String> offencesIn(Path file) {
        return Repository.readText(file).stream()
            .flatMap(text -> StylesheetSelectorRules.broken(text).stream())
            .map(line -> this.worded(file, line));
    }

    private String worded(Path file, int line) {
        return "%s:%d: a space joins two refusals, so this selects inside what it names rather than it. "
            .formatted(this.root.relativize(file), line)
            + "Write the selector on one line, or name the parts in one refusal";
    }
}
