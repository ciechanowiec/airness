package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads every markup resource a module ships and reports the two constructs that hand a template's own
 * input more authority than a template is meant to give it.
 *
 * <p>The first is output the engine does not escape. Everything a template writes is escaped unless it
 * is asked not to, and asking not to is a single attribute or a single pair of brackets. What was
 * stored is then written into the page as markup, so a value that reached the model from a form, a
 * query string or a row of a table becomes script. Nothing in a build reads it: the document parses,
 * the page renders, and it renders correctly for every value nobody chose to attack it with.
 *
 * <p>The second is preprocessing, which evaluates an expression and then reads the result as another
 * expression. What that turns a stored value into is not markup but an expression the engine runs, so
 * the reach of one is the reach of the engine rather than the reach of a page. It is the sharper of
 * the two by a wide margin and it has no ordinary use, which is why it is refused wherever it is
 * written rather than held to a condition.
 *
 * <p>The repair for the first is the escaping form of the same construct, which is what a template
 * wants in every case where the value is not markup the project itself wrote. Where it truly is such
 * markup, the repair is to stop carrying markup in the model, because a value that is safe today is
 * safe only for as long as nobody stores a different one. The repair for the second is to write the
 * expression the preprocessing was building, and to select on a value rather than to compose a name
 * from one.
 *
 * <p>Both are read from the parsed document, in what an element carries and in the text between two of
 * them alike, because either construct is written in either place and a rule that read one of them
 * would report half of what it claims to.
 */
public final class TemplateOutputCheck {

    private static final String UNESCAPED = "Template output written into the page without escaping";

    private static final String PREPROCESSED = "Template expressions read a second time as expressions";

    private static final Set<String> UTEXT = Set.of("th:utext", "data-th-utext");

    // The inlined form of the same thing an unescaped attribute asks for, written in the text of a
    // document rather than on an element.
    private static final Pattern INLINED = Pattern.compile("\\[\\(.*?\\)]", Pattern.DOTALL);

    // An expression written around another one, under any of the four spellings the engine reads. What
    // the inner one returns is read as expression text rather than as a value.
    private static final Pattern PREPROCESSING = Pattern.compile("__[$*#@]\\{");

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateOutputCheck(Path root, Collection<Path> resourceRoots) {
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
     * The two output rules and every place that breaks one of them.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(UNESCAPED, this.scan.offences(Unescaped::new)),
            new Findings(PREPROCESSED, this.scan.offences(Preprocessed::new))
        );
    }

    /**
     * Collects every place a document writes output the engine will not escape.
     */
    private static final class Unescaped implements MarkupElement {

        private static final String REPAIR
            = "Write it with the escaping form, and keep markup out of what the model carries";

        private final Path named;

        private final Collection<String> offences;

        private Unescaped(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.keySet()
                .stream()
                .filter(attribute -> UTEXT.contains(attribute.toLowerCase(Locale.ROOT)))
                .forEach(attribute -> this.add(line, column, attribute + " writes its value as markup"));
        }

        @Override
        public void text(String content, int line, int column) {
            if (INLINED.matcher(content).find()) {
                this.add(line, column, "an inlined expression writes its value as markup");
            }
        }

        private void add(int line, int column, String said) {
            this.offences.add("%s:%d:%d: %s. %s".formatted(this.named, line, column, said, REPAIR));
        }
    }

    /**
     * Collects every place a document has the engine read one expression's result as another.
     */
    private static final class Preprocessed implements MarkupElement {

        private static final String SAID
            = "an expression is preprocessed, so what it returns is run as an expression";

        private static final String REPAIR
            = "Write the expression it was building, and select on the value rather than composing a name from it";

        private final Path named;

        private final Collection<String> offences;

        private Preprocessed(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.values()
                .stream()
                .filter(value -> PREPROCESSING.matcher(value).find())
                .forEach(_ -> this.add(line, column));
        }

        @Override
        public void text(String content, int line, int column) {
            if (PREPROCESSING.matcher(content).find()) {
                this.add(line, column);
            }
        }

        private void add(int line, int column) {
            this.offences.add("%s:%d:%d: %s. %s".formatted(this.named, line, column, SAID, REPAIR));
        }
    }
}
