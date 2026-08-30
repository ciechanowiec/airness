package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads every markup resource a module ships and reports the elements that are replaced by a fragment
 * while carrying an attribute the replacement throws away.
 *
 * <p>A replacement discards the element it is written on and puts the fragment in its place, so every
 * other attribute of that element goes with it, unread. A condition there decides nothing, an
 * iteration there runs once, and a text there reaches no page: the fragment is drawn for everybody,
 * always, exactly once. The dialect settles this by precedence rather than by writing order, so the
 * two orderings of the same element are equally wrong.
 *
 * <p>Nothing else in a build says so. The markup parses, the page renders, and a rule read against the
 * rendered page finds a document that is valid and wrong, which leaves the defect visible only to
 * somebody who already knows what the page was supposed to say. The repair is to put what was
 * discarded on a block wrapping the replaced element, which is the construct the dialect carries for
 * exactly this.
 *
 * <p>Only a replacement is read this way. An insertion keeps the element and fills its body, so what
 * else is written there still runs, and reporting one would be reporting a defect nobody has.
 */
public final class TemplateReplacementCheck {

    private static final String HEADLINE = "Elements replaced by a fragment that also carry an attribute nothing reads";

    private static final String SEPARATOR = ", ";

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateReplacementCheck(Path root, Collection<Path> resourceRoots) {
        this.scan = new MarkupScan(root, resourceRoots);
    }

    /**
     * How many markup resources the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.scan.scanned();
    }

    /**
     * The one replacement rule and every element that breaks it.
     *
     * @return one verdict, carrying a line per element whose replacement discards something written
     *         beside it
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.scan.offences(Replacements::new)));
    }

    /**
     * Collects every element whose replacement discards something written beside it, with the place it
     * was written.
     */
    private static final class Replacements implements MarkupElement {

        private static final String THYMELEAF = "th:";

        // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
        private static final String THYMELEAF_DATA = "data-th-";

        private static final String REPLACE = "th:replace";

        private static final String REPLACE_DATA = "data-th-replace";

        private final Path named;

        private final Collection<String> offences;

        private Replacements(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            List<String> written = attributes.keySet()
                .stream()
                .map(attribute -> attribute.toLowerCase(Locale.ROOT))
                .filter(Replacements::dialect)
                .sorted()
                .toList();
            this.measure(written, line, column);
        }

        // Sorted rather than in the order they were written, so that one element reads the same way in
        // two documents and a message does not change when somebody moves an attribute.
        private void measure(List<String> written, int line, int column) {
            List<String> discarded = written.stream().filter(attribute -> !replaces(attribute)).toList();
            written.stream()
                .filter(Replacements::replaces)
                .findFirst()
                .filter(_ -> !discarded.isEmpty())
                .ifPresent(
                    replacement -> this.offences.add(
                        "%s:%d:%d: %s discards %s on this element. Put it on a wrapping th:block instead"
                            .formatted(this.named, line, column, replacement, String.join(SEPARATOR, discarded))
                    )
                );
        }

        private static boolean dialect(String attribute) {
            return attribute.startsWith(THYMELEAF) || attribute.startsWith(THYMELEAF_DATA);
        }

        private static boolean replaces(String attribute) {
            return REPLACE.equals(attribute) || REPLACE_DATA.equals(attribute);
        }
    }
}
